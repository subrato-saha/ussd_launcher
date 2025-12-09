package com.kavina.ussd_launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Path
import java.util.Locale

class UssdAccessibilityService : AccessibilityService() {
    
    companion object {
        private var instance: UssdAccessibilityService? = null
        private var pendingMessages: ArrayDeque<String> = ArrayDeque()
        var hideDialogs = false
        private var lastUssdMessage: String? = null
        private var currentStepIndex = 0
        private var retryCount = 0
        private const val MAX_RETRIES = 5
        private const val RETRY_DELAY_MS = 800L
        
        // Packages that can display USSD dialogs
        private val USSD_PACKAGES = setOf(
            "com.android.phone",
            "com.samsung.android.phone",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.sec.android.app.telephonyui",
            "com.huawei.systemmanager",
            "com.miui.securitycenter",
            "com.coloros.phonemanager",
            "com.oppo.usercenter"
        )
        
        // List of common confirm button texts in multiple languages
        private val CONFIRM_BUTTON_TEXTS = listOf(
            "send", "ok", "submit", "yes", "confirm", "continue",
            "envoyer", "confirmer", "oui", "valider", "continuer",
            "enviar", "aceptar", "sí", "confirmar",
            "senden", "ja", "bestätigen"
        )

        fun sendReply(messages: List<String>) {
            println("UssdAccessibilityService: Setting pending messages: $messages")
            pendingMessages.clear()
            pendingMessages.addAll(messages)
            retryCount = 0
            // Schedule retry attempts
            instance?.scheduleReplyAttempt()
        }

        fun cancelSession() {
            instance?.let { service ->
                try {
                    val rootInActiveWindow = service.rootInActiveWindow
                    rootInActiveWindow?.let { root ->
                        val cancelButton = root.findAccessibilityNodeInfosByViewId("android:id/button2")
                        val clicked = cancelButton?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                        
                        if (!clicked) {
                            service.performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                        
                        cancelButton?.forEach { it.recycle() }
                        root.recycle()
                    }
                    println("UssdAccessibilityService: USSD session cancelled")
                    lastUssdMessage = null
                    currentStepIndex = 0
                    retryCount = 0
                    pendingMessages.clear()
                } catch (e: Exception) {
                    println("UssdAccessibilityService: Error cancelling session: ${e.message}")
                }
            }
        }
        
        fun isServiceRunning(): Boolean = instance != null
        
        fun resetLastMessage() {
            lastUssdMessage = null
            currentStepIndex = 0
            retryCount = 0
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Schedule a reply attempt with retry logic
     */
    private fun scheduleReplyAttempt() {
        handler.postDelayed({
            tryPerformReply()
        }, RETRY_DELAY_MS)
    }

    /**
     * Try to perform reply with retry mechanism
     */
    private fun tryPerformReply() {
        if (pendingMessages.isEmpty()) {
            retryCount = 0
            return
        }

        val message = pendingMessages.firstOrNull() ?: return
        println("UssdAccessibilityService: Attempt ${retryCount + 1}/$MAX_RETRIES - Trying to reply with: '$message'")

        val rootInActiveWindow = this.rootInActiveWindow
        if (rootInActiveWindow == null) {
            println("UssdAccessibilityService: No active window")
            retryIfNeeded()
            return
        }
        
        try {
            val editText = findInputField(rootInActiveWindow)

            if (editText != null) {
                // Found the input field, proceed with reply
                pendingMessages.removeFirstOrNull()
                currentStepIndex++
                retryCount = 0
                
                // Set text in the input field
                val bundle = Bundle()
                bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
                val setTextSuccess = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                println("UssdAccessibilityService: [Step $currentStepIndex] Set text '$message': $setTextSuccess")
                
                editText.recycle()

                // Find and click confirm button with a small delay
                handler.postDelayed({
                    clickConfirmButton()
                }, 300)
            } else {
                println("UssdAccessibilityService: Input field not found, will retry...")
                retryIfNeeded()
            }
        } catch (e: Exception) {
            println("UssdAccessibilityService: Error in tryPerformReply: ${e.message}")
            retryIfNeeded()
        } finally {
            rootInActiveWindow.recycle()
        }
    }
    
    /**
     * Retry if max retries not reached
     */
    private fun retryIfNeeded() {
        retryCount++
        if (retryCount < MAX_RETRIES) {
            println("UssdAccessibilityService: Scheduling retry ${retryCount + 1}/$MAX_RETRIES in ${RETRY_DELAY_MS}ms")
            scheduleReplyAttempt()
        } else {
            println("UssdAccessibilityService: Max retries ($MAX_RETRIES) reached, giving up on current message")
            // Move to next message
            pendingMessages.removeFirstOrNull()
            retryCount = 0
            if (pendingMessages.isNotEmpty()) {
                scheduleReplyAttempt()
            }
        }
    }

    /**
     * Find and click the confirm button
     */
    private fun clickConfirmButton() {
        val rootInActiveWindow = this.rootInActiveWindow ?: return
        
        try {
            val button = findConfirmButton(rootInActiveWindow)
            if (button != null) {
                val clickSuccess = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                println("UssdAccessibilityService: [Step $currentStepIndex] Click 'Send': $clickSuccess")
                button.recycle()
            } else {
                println("UssdAccessibilityService: Confirm button not found, trying alternatives")
                tryAlternativeConfirmMethods(rootInActiveWindow)
            }
        } finally {
            rootInActiveWindow.recycle()
        }
    }

    private fun findInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val editTexts = findNodesByClassName(root, "android.widget.EditText")
        val result = editTexts.firstOrNull()
        editTexts.filter { it != result }.forEach { it.recycle() }
        return result
    }

    private fun findConfirmButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val buttons = findNodesByClassName(root, "android.widget.Button")
        val confirmButton = buttons.firstOrNull { button ->
            val buttonText = button.text?.toString()?.lowercase(Locale.ROOT) ?: ""
            CONFIRM_BUTTON_TEXTS.any { confirmText -> buttonText.contains(confirmText) }
        }
        buttons.filter { it != confirmButton }.forEach { it.recycle() }
        return confirmButton
    }

    private fun tryAlternativeConfirmMethods(root: AccessibilityNodeInfo) {
        val allButtons = findNodesByClassName(root, "android.widget.Button")
        for (button in allButtons) {
            val buttonText = button.text?.toString()?.lowercase(Locale.ROOT) ?: ""
            // Skip cancel button
            if (buttonText.contains("annuler") || buttonText.contains("cancel")) {
                continue
            }
            println("UssdAccessibilityService: Clicking button: ${button.text}")
            val clickSuccess = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clickSuccess) {
                allButtons.forEach { it.recycle() }
                return
            }
        }
        allButtons.forEach { it.recycle() }

        // Try gesture as last resort
        try {
            dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(Path(), 0, 1))
                    .build(),
                null,
                null
            )
        } catch (e: Exception) {
            println("UssdAccessibilityService: Gesture failed: ${e.message}")
        }
    }

    private fun findNodesByClassName(root: AccessibilityNodeInfo?, className: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return result

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.className?.toString() == className) {
                result.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return result
    }

    private fun isUssdPackage(packageName: String?): Boolean {
        if (packageName == null) return false
        return USSD_PACKAGES.any { ussdPkg -> 
            packageName.lowercase(Locale.ROOT).contains(ussdPkg.lowercase(Locale.ROOT)) 
        } || packageName.lowercase(Locale.ROOT).contains("phone") 
          || packageName.lowercase(Locale.ROOT).contains("dialer")
          || packageName.lowercase(Locale.ROOT).contains("telecom")
    }

    private fun isValidUssdMessage(message: String): Boolean {
        val lowerMessage = message.lowercase(Locale.ROOT)
        
        if (message.length < 3) return false
        
        val invalidPatterns = listOf(
            "play store", "google play", "raccourci", "shortcut",
            "services téléchargés", "downloaded services", 
            "volume", "settings", "paramètres",
            "notification", "battery", "batterie",
            "wifi", "bluetooth", "airplane", "avion"
        )
        
        if (invalidPatterns.any { lowerMessage.contains(it) }) {
            return false
        }
        
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString()
        
        // Only process events from USSD-related packages
        if (!isUssdPackage(packageName)) {
            return
        }
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && hideDialogs) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        try {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                
                val nodeInfo = event.source ?: return
                
                try {
                    // Get the complete dialog content
                    val dialogContent = getCompleteDialogContent(nodeInfo)
                    
                    if (dialogContent != null && dialogContent.isNotBlank()) {
                        // Log the complete dialog content
                        println("")
                        println("╔══════════════════════════════════════════════════════════════")
                        println("║ 📱 USSD DIALOG - Step ${currentStepIndex + 1}")
                        println("╠══════════════════════════════════════════════════════════════")
                        dialogContent.lines().forEach { line ->
                            if (line.isNotBlank()) {
                                println("║ $line")
                            }
                        }
                        println("╚══════════════════════════════════════════════════════════════")
                        println("")
                        
                        // Send to Flutter if different from last message
                        if (isValidUssdMessage(dialogContent) && dialogContent != lastUssdMessage) {
                            lastUssdMessage = dialogContent
                            println(">>> SENDING TO FLUTTER: ${dialogContent.take(50)}...")
                            UssdLauncherPlugin.onUssdResult(dialogContent)
                        }
                    }

                    // If there are pending messages, try to reply
                    // The dialog is now showing, so we can attempt to reply
                    if (pendingMessages.isNotEmpty() && retryCount == 0) {
                        // Reset retry count for new dialog
                        handler.postDelayed({
                            tryPerformReply()
                        }, 1000) // Wait 1s for dialog to fully render
                    }
                } finally {
                    nodeInfo.recycle()
                }
            }
        } catch (e: Exception) {
            println("UssdAccessibilityService: Error in onAccessibilityEvent: ${e.message}")
        }
    }

    /**
     * Get the complete content of the USSD dialog
     */
    private fun getCompleteDialogContent(node: AccessibilityNodeInfo): String? {
        val allTexts = mutableListOf<String>()
        collectAllTextViewContent(node, allTexts)
        
        if (allTexts.isEmpty()) return null
        
        // Filter out button texts and system labels
        val meaningfulTexts = allTexts.filter { text ->
            val lower = text.lowercase(Locale.ROOT)
            !CONFIRM_BUTTON_TEXTS.contains(lower) &&
            lower != "annuler" && lower != "cancel" &&
            lower != "message ussd" && lower != "ussd code" &&
            text.length > 2
        }
        
        return if (meaningfulTexts.isNotEmpty()) {
            meaningfulTexts.joinToString("\n").trim()
        } else {
            null
        }
    }
    
    /**
     * Recursively collect all TextView content from the node tree
     */
    private fun collectAllTextViewContent(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        if (node.className?.toString() == "android.widget.EditText") {
            return
        }

        if (node.className?.toString() == "android.widget.TextView" && node.text != null) {
            val text = node.text.toString().trim()
            if (text.isNotBlank() && text.length > 1) {
                texts.add(text)
            }
        }

        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i) ?: continue
            try {
                collectAllTextViewContent(childNode, texts)
            } finally {
                childNode.recycle()
            }
        }
    }

    override fun onInterrupt() {
        println("UssdAccessibilityService: Service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        println("UssdAccessibilityService: Service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        pendingMessages.clear()
        lastUssdMessage = null
        currentStepIndex = 0
        retryCount = 0
        handler.removeCallbacksAndMessages(null)
        println("UssdAccessibilityService: Service destroyed")
    }
}