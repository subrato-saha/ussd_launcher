## 1.3.0

### Major improvements
* **Android compatibility**: Full support for Android 5.0+ (API 21+)
  - Android 8+ uses TelephonyManager.sendUssdRequest()
  - Android 5-7 falls back to ACTION_CALL intent with accessibility service
* **Memory leak fixes**: Proper recycling of AccessibilityNodeInfo objects
* **Deprecated API fixes**:
  - Replaced GlobalScope with proper CoroutineScope
  - Replaced toLowerCase() with lowercase(Locale.ROOT)
* **New methods added**:
  - `isAccessibilityEnabled()`: Check if accessibility service is enabled
  - `openAccessibilitySettings()`: Open system accessibility settings
  - `cancelSession()`: Cancel active USSD session
  - `removeUssdMessageListener()`: Remove the message listener
* **Configurable delays**: Multi-session USSD now supports custom delays
  - `initialDelayMs`: Delay before first option (default: 3500ms)
  - `optionDelayMs`: Delay between options (default: 2000ms)
* **Better error handling**: All methods now return proper error codes
* **Multi-language support**: Confirm button detection in EN, FR, ES, DE
* **Updated dependencies**:
  - Kotlin 1.9.10
  - Android Gradle Plugin 8.1.0
  - Java 17 compatibility

### Bug fixes
* Fixed: Flutter code hanging when accessibility not enabled
* Fixed: Crash on Android versions below 8.0
* Fixed: Memory leaks in AccessibilityService
* Fixed: Incorrect accessibility service configuration

## 1.2.7

* Bug fixed

## 1.2.2

* Sim Cards data updated

## 1.2.1

* list and information about the sim cards present in the phone
* launch an operation from a selected sim

## 1.1.0

* Launch simple USSD requests
* Manage multi-step USSD sessions
* Updated readme

## 1.0.0

* Launch simple USSD requests

## 0.1.0

* Initial version.