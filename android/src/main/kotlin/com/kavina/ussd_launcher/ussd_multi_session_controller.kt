package com.kavina.ussd_launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import io.flutter.plugin.common.MethodChannel.Result
import java.util.Locale

class UssdMultiSession(private val context: Context) {
    
    private var ussdOptionsQueue: ArrayDeque<String> = ArrayDeque()
    private var isRunning = false
    private var callbackInvoke: CallbackInvoke? = null
    private var map: HashMap<String, HashSet<String>>? = null
    
    // Configurable delays (in milliseconds)
    var initialDelayMs: Long = 5000  // Wait 5s for first dialog
    var optionDelayMs: Long = 4000   // Wait 4s between options
    var replyDelayMs: Long = 3000    // Wait 3s for dialog to render
    
    // Custom overlay message
    var overlayMessage: String = "Opération USSD en cours..."

    companion object {
        private const val KEY_ERROR = "KEY_ERROR"
        private const val KEY_LOGIN = "KEY_LOGIN"
        
        fun createDefaultHashMap(): HashMap<String, HashSet<String>> {
            return hashMapOf(
                KEY_ERROR to hashSetOf("error", "failed", "invalid", "échec", "erreur"),
                KEY_LOGIN to hashSetOf("login", "password", "pin", "code")
            )
        }
    }

    fun callUSSDInvoke(
        str: String, 
        simSlot: Int, 
        hashMap: HashMap<String, HashSet<String>>, 
        callbackInvoke: CallbackInvoke
    ) {
        this.callbackInvoke = callbackInvoke
        this.map = hashMap
        if (verifyAccessibilityAccess()) {
            dialUp(str, simSlot)
        } else {
            this.callbackInvoke?.over("ACCESSIBILITY_NOT_ENABLED")
        }
    }

    fun callUSSDWithMenu(
        str: String, 
        simSlot: Int, 
        options: List<String>, 
        hashMap: HashMap<String, HashSet<String>>, 
        callbackInvoke: CallbackInvoke
    ) {
        this.callbackInvoke = callbackInvoke
        this.map = hashMap
        this.ussdOptionsQueue.clear()
        this.ussdOptionsQueue.addAll(options)
        
        if (verifyAccessibilityAccess()) {
            dialUp(str, simSlot)
        } else {
            this.callbackInvoke?.over("ACCESSIBILITY_NOT_ENABLED")
        }
    }

    fun callUSSDOverlayInvoke(
        str: String, 
        simSlot: Int, 
        hashMap: HashMap<String, HashSet<String>>, 
        callbackInvoke: CallbackInvoke
    ) {
        this.callbackInvoke = callbackInvoke
        this.map = hashMap
        
        if (verifyAccessibilityAccess() && verifyOverlay()) {
            dialUp(str, simSlot)
        } else {
            this.callbackInvoke?.over("ACCESSIBILITY_OR_OVERLAY_NOT_ENABLED")
        }
    }

    private fun dialUp(str: String, simSlot: Int) {
        val hashMap = this.map
        if (hashMap == null || !hashMap.containsKey(KEY_ERROR) || !hashMap.containsKey(KEY_LOGIN)) {
            this.callbackInvoke?.over("BAD_MAPPING_STRUCTURE")
            return
        }
        
        if (str.isEmpty()) {
            this.callbackInvoke?.over("EMPTY_USSD_CODE")
            return
        }
        
        try {
            val encodedHash = Uri.encode("#")
            val ussdCode = str.replace("#", encodedHash)
            val uri = Uri.parse("tel:$ussdCode")
            
            this.isRunning = true
            setHideDialogs(true)
            startOverlay(overlayMessage)
            
            context.startActivity(getActionCallIntent(uri, simSlot))

            Handler(Looper.getMainLooper()).postDelayed({
                sendNextUssdOption()
            }, initialDelayMs)
        } catch (e: Exception) {
            this.callbackInvoke?.over("DIAL_ERROR: ${e.message}")
        }
    }

    private fun sendNextUssdOption() {
        if (ussdOptionsQueue.isNotEmpty()) {
            val nextOption = ussdOptionsQueue.removeFirstOrNull()
            if (nextOption != null) {
                sendUssdOption(nextOption)
            }
        } else {
            println("UssdMultiSession: All options processed, ending session")
            try {
                cancelSession()
                stopOverlay()
                this.callbackInvoke?.over("SESSION_COMPLETED")
            } catch (e: Exception) {
                println("UssdMultiSession: Error ending session: ${e.message}")
                this.callbackInvoke?.over("SESSION_END_ERROR: ${e.message}")
            }
        }
    }

    private fun sendUssdOption(option: String) {
        try {
            println("UssdMultiSession: Sending option '$option'...")
            UssdAccessibilityService.sendReply(listOf(option))
            
            // Wait for the reply to be processed and next dialog to appear
            Handler(Looper.getMainLooper()).postDelayed({
                // Check if there are more options
                if (ussdOptionsQueue.isNotEmpty()) {
                    println("UssdMultiSession: Waiting for next dialog before sending next option...")
                }
                sendNextUssdOption()
            }, optionDelayMs)
        } catch (e: Exception) {
            println("UssdMultiSession: Error sending option: ${e.message}")
            callbackInvoke?.over("SEND_OPTION_ERROR: ${e.message}")
        }
    }

    private fun getActionCallIntent(uri: Uri, simSlot: Int): Intent {
        // Common slot keys used by different manufacturers
        val slotKeys = arrayOf(
            "extra_asus_dial_use_dualsim",
            "com.android.phone.extra.slot",
            "slot", "simslot", "sim_slot",
            "Subscription", "phone",
            "com.android.phone.DialingMode",
            "simSlot", "slot_id", "simId",
            "simnum", "phone_type", "slotId", "slotIdx"
        )
        
        val intent = Intent(Intent.ACTION_CALL, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("com.android.phone.force.slot", true)
            putExtra("Cdma_Supp", true)
        }
        
        // Add slot info with all possible keys for maximum compatibility
        for (key in slotKeys) {
            intent.putExtra(key, simSlot)
        }
        
        // Use TelecomManager for proper SIM selection on modern devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager?
                telecomManager?.let {
                    val phoneAccounts = it.callCapablePhoneAccounts
                    if (phoneAccounts.size > simSlot && simSlot >= 0) {
                        intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", phoneAccounts[simSlot])
                    }
                }
            } catch (e: SecurityException) {
                println("UssdMultiSession: Cannot access phone accounts: ${e.message}")
            }
        }
        
        return intent
    }

    private fun verifyAccessibilityAccess(): Boolean {
        return try {
            val accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver, 
                Settings.Secure.ACCESSIBILITY_ENABLED, 
                0
            )
            
            if (accessibilityEnabled == 1) {
                val services = Settings.Secure.getString(
                    context.contentResolver, 
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                services?.lowercase(Locale.ROOT)?.contains(context.packageName.lowercase(Locale.ROOT)) == true
            } else {
                false
            }
        } catch (e: Exception) {
            println("UssdMultiSession: Error checking accessibility: ${e.message}")
            false
        }
    }

    private fun verifyOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun sendMessage(message: String, result: Result) {
        if (isRunning) {
            UssdAccessibilityService.sendReply(listOf(message))
            result.success("Message sent: $message")
        } else {
            result.error("NO_ACTIVE_SESSION", "No USSD session is currently active", null)
        }
    }

    fun cancelSession(result: Result? = null) {
        if (isRunning) {
            isRunning = false
            setHideDialogs(false)
            stopOverlay()
            
            try {
                UssdAccessibilityService.cancelSession()
                result?.success(null)
            } catch (e: Exception) {
                println("UssdMultiSession: Error cancelling session: ${e.message}")
                result?.error("CANCEL_ERROR", "Error cancelling session: ${e.message}", null)
            }
        } else {
            result?.error("NO_ACTIVE_SESSION", "No USSD session is currently active", null)
        }
    }

    fun setHideDialogs(hide: Boolean) {
        UssdAccessibilityService.hideDialogs = hide
    }
    
    private fun startOverlay(message: String) {
        if (verifyOverlay()) {
            try {
                val intent = Intent(context, UssdOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                UssdOverlayService.updateMessage(message)
            } catch (e: Exception) {
                println("UssdMultiSession: Error starting overlay: ${e.message}")
            }
        }
    }
    
    private fun updateOverlay(message: String) {
        if (UssdOverlayService.isRunning()) {
            UssdOverlayService.updateMessage(message)
        }
    }
    
    private fun stopOverlay() {
        try {
            val intent = Intent(context, UssdOverlayService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {
            println("UssdMultiSession: Error stopping overlay: ${e.message}")
        }
    }
    
    fun isSessionRunning(): Boolean = isRunning

    interface CallbackInvoke {
        fun responseInvoke(message: String)
        fun over(message: String)
    }
}
