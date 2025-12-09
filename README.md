# ussd_launcher

A Flutter plugin to launch USSD requests and manage multi-step USSD sessions on Android directly from your application.

[![pub package](https://img.shields.io/pub/v/ussd_launcher.svg)](https://pub.dev/packages/ussd_launcher)

## Features

- ✅ Launch simple USSD requests (Android 5.0+)
- ✅ Manage multi-step USSD sessions with automatic menu navigation
- ✅ Check and request necessary accessibility permissions
- ✅ Full compatibility with Android 5.0+ (API 21+)
- ✅ Handle USSD responses and errors gracefully
- ✅ Get detailed information about available SIM cards
- ✅ Support for dual-SIM devices
- ✅ Configurable delays for multi-session USSD
- ✅ Multi-language button detection (EN, FR, ES, DE)

## Installation

Add `ussd_launcher` as a dependency in your `pubspec.yaml` file:

```yaml
dependencies:
  ussd_launcher: ^1.3.0
```

## Configuration

### Android

Add the following permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
```

### For multi-session USSD

Add the accessibility service configuration to your `AndroidManifest.xml` inside the `<application>` tag:

```xml
<application>
    ...
    <service
        android:name="com.kavina.ussd_launcher.UssdAccessibilityService"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
        android:exported="false">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService" />
        </intent-filter>
        <meta-data
            android:name="android.accessibilityservice"
            android:resource="@xml/accessibility_service_config" />
    </service>
</application>
```

## Usage

### Import the package

```dart
import 'package:ussd_launcher/ussd_launcher.dart';
import 'package:permission_handler/permission_handler.dart';
```

### Check and request permissions

```dart
Future<bool> requestPermissions() async {
  var phoneStatus = await Permission.phone.request();
  return phoneStatus.isGranted;
}
```

### Check if accessibility service is enabled

```dart
bool isEnabled = await UssdLauncher.isAccessibilityEnabled();
if (!isEnabled) {
  await UssdLauncher.openAccessibilitySettings();
}
```

### Get available SIM cards

```dart
List<Map<String, dynamic>> simCards = await UssdLauncher.getSimCards();
for (var sim in simCards) {
  print('SIM: ${sim['displayName']} - ${sim['carrierName']}');
  print('Slot: ${sim['slotIndex']}, ID: ${sim['subscriptionId']}');
}
```

### Single-session USSD request

```dart
try {
  String? response = await UssdLauncher.sendUssdRequest(
    ussdCode: '*123#',
    subscriptionId: simCards[0]['subscriptionId'],
  );
  print('USSD Response: $response');
} on PlatformException catch (e) {
  if (e.code == 'ACCESSIBILITY_NOT_ENABLED') {
    // Guide user to enable accessibility
  }
  print('Error: ${e.message}');
}
```

### Multi-session USSD with automatic menu navigation

```dart
// Set up listener for USSD messages
UssdLauncher.setUssdMessageListener((message) {
  print('USSD Message: $message');
  // Handle the message in your UI
});

// Launch multi-session USSD
await UssdLauncher.multisessionUssd(
  code: '*123#',
  slotIndex: 0,                    // First SIM slot
  options: ['1', '2', '3'],        // Menu options to select
  initialDelayMs: 3500,            // Optional: delay before first option
  optionDelayMs: 2000,             // Optional: delay between options
);

// Don't forget to remove listener when done
UssdLauncher.removeUssdMessageListener();
```

### Cancel an active session

```dart
await UssdLauncher.cancelSession();
```

## API Reference

### Methods

| Method | Description |
|--------|-------------|
| `sendUssdRequest()` | Send a single USSD request and get the response |
| `multisessionUssd()` | Start a multi-step USSD session with auto-navigation |
| `getSimCards()` | Get list of available SIM cards with details |
| `isAccessibilityEnabled()` | Check if accessibility service is enabled |
| `openAccessibilitySettings()` | Open system accessibility settings |
| `cancelSession()` | Cancel the current USSD session |
| `setUssdMessageListener()` | Set listener for USSD messages |
| `removeUssdMessageListener()` | Remove the message listener |

### Error Codes

| Code | Description |
|------|-------------|
| `ACCESSIBILITY_NOT_ENABLED` | Accessibility service is not enabled |
| `PERMISSION_DENIED` | Required permissions not granted |
| `USSD_FAILED` | USSD request failed |
| `INVALID_ARGUMENT` | Invalid parameter provided |
| `NO_ACTIVE_SESSION` | No USSD session is currently active |

## Platform Support

| Platform | Support |
|----------|---------|
| Android 5.0-7.1 (API 21-25) | ✅ Via Accessibility Service |
| Android 8.0+ (API 26+) | ✅ Native TelephonyManager API |
| iOS | ❌ Not supported (Apple restrictions) |

## Important Notes

- This plugin requires the accessibility service to be enabled for multi-session USSD.
- Single-session USSD on Android 8+ works without accessibility service.
- Multi-step USSD sessions may vary depending on mobile operators and countries.
- Delays may need adjustment based on network and operator response times.

## Troubleshooting

### USSD not working?

1. Ensure `CALL_PHONE` and `READ_PHONE_STATE` permissions are granted
2. Enable the accessibility service in device settings
3. Try increasing delays for slow networks
4. Check if your carrier supports the USSD code

### Dual-SIM issues?

1. Use `getSimCards()` to get the correct `subscriptionId` or `slotIndex`
2. Use `subscriptionId` for `sendUssdRequest()`
3. Use `slotIndex` for `multisessionUssd()`

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request on our [GitHub repository](https://github.com/codianselme/ussd_launcher).

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.