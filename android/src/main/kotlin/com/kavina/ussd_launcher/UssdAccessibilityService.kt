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
            instance?.performReply()
        }

        fun cancelSession() {
            instance?.let { service ->
                try {
                    val rootInActiveWindow = service.rootInActiveWindow
                    rootInActiveWindow?.let { root ->
                        // Try to find cancel button by ID
                        val cancelButton = root.findAccessibilityNodeInfosByViewId("android:id/button2")
                        val clicked = cancelButton?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                        
                        if (!clicked) {
                            // Fallback: use back action
                            service.performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                        
                        // Recycle nodes to prevent memory leak
                        cancelButton?.forEach { it.recycle() }
                        root.recycle()
                    }
                    println("UssdAccessibilityService: USSD session cancelled")
                    lastUssdMessage = null
                } catch (e: Exception) {
                    println("UssdAccessibilityService: Error cancelling session: ${e.message}")
                }
            } ?: run {
                println("UssdAccessibilityService: Service instance not available for cancel")
            }
        }
        
        fun isServiceRunning(): Boolean = instance != null
        
        fun resetLastMessage() {
            lastUssdMessage = null
        }
    }

    private fun performReply() {
        try {
            if (pendingMessages.isEmpty()) return

            val message = pendingMessages.removeFirstOrNull() ?: return
            println("UssdAccessibilityService: Performing reply with message: $message")

            val rootInActiveWindow = this.rootInActiveWindow ?: return
            
            try {
                val editText = findInputField(rootInActiveWindow)

                if (editText != null) {
                    // Set text in the input field
                    val bundle = Bundle()
                    bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
                    val setTextSuccess = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                    println("UssdAccessibilityService: Set text action performed: $setTextSuccess")
                    
                    // Recycle editText
                    editText.recycle()

                    // Find and click confirm button
                    val button = findConfirmButton(rootInActiveWindow)
                    if (button != null) {
                        val clickSuccess = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        println("UssdAccessibilityService: Click action performed: $clickSuccess")
                        button.recycle()
                        
                        // Wait before sending next message
                        Handler(Looper.getMainLooper()).postDelayed({
                            performReply()
                        }, 3000)
                    } else {
                        println("UssdAccessibilityService: Confirm button not found, trying alternatives")
                        tryAlternativeConfirmMethods(rootInActiveWindow)
                    }
                } else {
                    println("UssdAccessibilityService: Input field not found")
                }
            } finally {
                // Always recycle root node
                rootInActiveWindow.recycle()
            }
        } catch (e: Exception) {
            println("UssdAccessibilityService: Error in performReply: ${e.message}")
        }
    }

    private fun findInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val editTexts = findNodesByClassName(root, "android.widget.EditText")
        val result = editTexts.firstOrNull()
        // Recycle unused nodes
        editTexts.filter { it != result }.forEach { it.recycle() }
        return result
    }

    private fun findConfirmButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val buttons = findNodesByClassName(root, "android.widget.Button")
        val confirmButton = buttons.firstOrNull { button ->
            val buttonText = button.text?.toString()?.lowercase(Locale.ROOT) ?: ""
            CONFIRM_BUTTON_TEXTS.any { confirmText -> buttonText.contains(confirmText) }
        }
        // Recycle unused buttons
        buttons.filter { it != confirmButton }.forEach { it.recycle() }
        return confirmButton
    }

    private fun tryAlternativeConfirmMethods(root: AccessibilityNodeInfo) {
        // Method 1: Try clicking all buttons
        val allButtons = findNodesByClassName(root, "android.widget.Button")
        for (button in allButtons) {
            println("UssdAccessibilityService: Attempting to click button: ${button.text}")
            val clickSuccess = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clickSuccess) {
                println("UssdAccessibilityService: Successfully clicked button: ${button.text}")
                // Recycle all buttons
                allButtons.forEach { it.recycle() }
                Handler(Looper.getMainLooper()).postDelayed({
                    performReply()
                }, 3000)
                return
            }
        }
        allButtons.forEach { it.recycle() }

        // Method 2: Try clicking all clickable nodes
        val clickableNodes = findClickableNodes(root)
        for (node in clickableNodes) {
            println("UssdAccessibilityService: Attempting to click node: ${node.className}")
            val clickSuccess = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clickSuccess) {
                println("UssdAccessibilityService: Successfully clicked node: ${node.className}")
                clickableNodes.forEach { it.recycle() }
                Handler(Looper.getMainLooper()).postDelayed({
                    performReply()
                }, 3000)
                return
            }
        }
        clickableNodes.forEach { it.recycle() }

        // Method 3: Try gesture (empty path, just to trigger)
        try {
            dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(Path(), 0, 1))
                    .build(),
                null,
                null
            )
            println("UssdAccessibilityService: Dispatched gesture")
        } catch (e: Exception) {
            println("UssdAccessibilityService: Gesture dispatch failed: ${e.message}")
        }
    }

    private fun findClickableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isClickable && node != root) {
                result.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return result
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

    /**
     * Check if the event is from a USSD-related package
     */
    private fun isUssdPackage(packageName: String?): Boolean {
        if (packageName == null) return false
        return USSD_PACKAGES.any { ussdPkg -> 
            packageName.lowercase(Locale.ROOT).contains(ussdPkg.lowercase(Locale.ROOT)) 
        } || packageName.lowercase(Locale.ROOT).contains("phone") 
          || packageName.lowercase(Locale.ROOT).contains("dialer")
          || packageName.lowercase(Locale.ROOT).contains("telecom")
    }

    /**
     * Validate if the message looks like a USSD response
     */
    private fun isValidUssdMessage(message: String): Boolean {
        // Filter out common non-USSD messages
        val lowerMessage = message.lowercase(Locale.ROOT)
        
        // Too short messages are likely not USSD
        if (message.length < 3) return false
        
        // Filter out common system UI labels
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
        
        // USSD messages typically contain certain patterns
        val ussdPatterns = listOf(
            "*", "#", "solde", "balance", "credit", "crédit",
            "menu", "option", "appuyez", "press", "tapez", "enter",
            "montant", "amount", "numéro", "number", "compte", "account",
            "transfert", "transfer", "recharge", "envoi", "send",
            "1.", "2.", "3.", "4.", "5.", "6.", "7.", "8.", "9.", "0.",
            "1)", "2)", "3)", "4)", "5)", "6)", "7)", "8)", "9)", "0)",
            "fcfa", "xof", "xaf", "francs", "cfa", "ariary", "mzn", "eur", "usd"
        )
        
        // If it matches a USSD pattern, it's likely valid
        if (ussdPatterns.any { lowerMessage.contains(it) }) {
            return true
        }
        
        // If message is long (typical USSD menus), accept it
        if (message.length > 30) {
            return true
        }
        
        return false
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
                    val ussdMessage = findUssdMessage(nodeInfo)
                    
                    // Validate and deduplicate messages
                    if (!ussdMessage.isNullOrEmpty() && 
                        isValidUssdMessage(ussdMessage) &&
                        ussdMessage != lastUssdMessage) {
                        
                        lastUssdMessage = ussdMessage
                        UssdLauncherPlugin.onUssdResult(ussdMessage)
                    }

                    if (pendingMessages.isNotEmpty()) {
                        performReply()
                    }
                } finally {
                    nodeInfo.recycle()
                }
            }
        } catch (e: Exception) {
            println("UssdAccessibilityService: Error in onAccessibilityEvent: ${e.message}")
        }
    }

    private fun findUssdMessage(node: AccessibilityNodeInfo): String? {
        // Skip input fields
        if (node.className?.toString() == "android.widget.EditText") {
            return null
        }

        // Check if this is a TextView with text
        if (node.className?.toString() == "android.widget.TextView" && node.text != null) {
            val text = node.text.toString()
            // Only return non-empty meaningful text
            if (text.isNotBlank() && text.length > 2) {
                return text
            }
        }

        // Recursively search children
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i) ?: continue
            try {
                val message = findUssdMessage(childNode)
                if (message != null) {
                    return message
                }
            } finally {
                childNode.recycle()
            }
        }

        return null
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
        println("UssdAccessibilityService: Service destroyed")
    }
}