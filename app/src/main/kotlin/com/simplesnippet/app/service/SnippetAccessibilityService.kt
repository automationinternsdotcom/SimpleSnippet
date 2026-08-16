package com.simplesnippet.app.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.simplesnippet.app.MainActivity
import com.simplesnippet.app.R
import com.simplesnippet.app.data.AppConfig
import com.simplesnippet.app.data.Snippet
import com.simplesnippet.app.data.SnippetMatcher
import com.simplesnippet.app.data.createDefaultConfig
import com.simplesnippet.app.data.normalized

/**
 * Watches text fields for snippet triggers and expands them in place.
 */
class SnippetAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SnippetService"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "simplesnippet_service"

        const val PREFS_NAME = "simplesnippet_prefs"
        const val KEY_CONFIG = "config_json"
        const val KEY_TESTING = "is_testing_active"

        fun isEnabled(context: Context): Boolean =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )?.contains(context.packageName + "/" + SnippetAccessibilityService::class.java.name) == true

        /**
         * Posts (or re-posts) the ongoing status notification. Callable from
         * app code too: on Android 13+ the POST_NOTIFICATIONS grant usually
         * arrives after the service has already connected, and without a
         * re-post the notification would stay invisible until a reconnect.
         *
         * Posted as a plain notification, never via startForeground: this app
         * targets SDK 35, and on API 34+ startForeground without a declared
         * foregroundServiceType throws — which is why the old foreground
         * notification never appeared on modern devices.
         */
        fun showServiceNotification(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "SimpleSnippet",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Shows while snippet expansion is active"
                        setShowBadge(false)
                    }
                    context.getSystemService(NotificationManager::class.java)
                        .createNotificationChannel(channel)
                }

                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent =
                    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("SimpleSnippet is active")
                    .setContentText("Watching for snippet triggers.")
                    .setSmallIcon(R.drawable.ic_notification_monochrome)
                    .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .build()

                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Error posting service notification", e)
            }
        }
    }

    private val gson = Gson()
    private lateinit var overlayManager: OverlayManager
    private val debounceHandler = Handler(Looper.getMainLooper())
    private val expansionGuard = ExpansionGuard()
    @Volatile private var config: AppConfig = createDefaultConfig()

    // SharedPreferences only holds a weak reference to its listener, so this
    // must be a strong field or the listener is silently garbage collected.
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager = OverlayManager(this)
        loadConfig()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CONFIG) loadConfig()
        }
        prefsListener = listener
        prefs.registerOnSharedPreferenceChangeListener(listener)
        showServiceNotification(this)
        Log.d(TAG, "Service connected")
    }

    private fun loadConfig() {
        config = try {
            gson.fromJson(prefs.getString(KEY_CONFIG, null), AppConfig::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse config", e)
            null
        }.normalized()
        // Work queued under the previous config must not run under the new one:
        // a queued expansion or an open picker could otherwise fire after the
        // master switch was turned off or the snippet was edited away.
        debounceHandler.removeCallbacksAndMessages(null)
        if (::overlayManager.isInitialized && overlayManager.isShowing) {
            overlayManager.hideSnippetSelection()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Our own UI is ignored unless the in-app Test Lab is running.
        if (event.packageName?.toString() == packageName) {
            if (!prefs.getBoolean(KEY_TESTING, false)) return
        }

        // Refreshing the focused node on window changes keeps stale caches from
        // making inputNode.text return text that is several keystrokes old.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.refresh()
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        if (event.addedCount == 0 && event.removedCount == 0) return

        debounceHandler.removeCallbacksAndMessages(null)

        val inputNode = event.source
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return
        var currentText = inputNode.text?.toString() ?: ""
        // Some IMEs report the text only on the event itself, not on the node.
        if (currentText.isEmpty() && event.text.isNotEmpty()) {
            currentText = event.text.joinToString("")
        }

        // The echo of our own ACTION_SET_TEXT must not be treated as input:
        // re-matching it lets self-referential or cyclic snippets expand forever.
        if (expansionGuard.consumeIfServiceWrite(currentText)) return

        val cfg = config
        if (!cfg.isAppEnabled) return

        try {
            // Quick-save runs before expansion on purpose: a save payload that
            // contains an existing trigger must be saved, not expanded.
            SnippetMatcher.findSaveCommand(currentText, cfg.saveSnippetPattern)?.let { cmd ->
                val existing = cfg.snippets.find { it.trigger == cmd.trigger }
                if (existing != null) {
                    if (!existing.contents.contains(cmd.content)) existing.contents.add(cmd.content)
                } else {
                    cfg.snippets.add(Snippet(cmd.trigger, mutableListOf(cmd.content)))
                }
                prefs.edit().putString(KEY_CONFIG, gson.toJson(cfg)).apply()
                pasteText(inputNode, currentText.replace(cmd.fullMatch, cmd.content))
                overlayManager.showToast("Snippet '" + cmd.trigger + "' saved!")
                return
            }

            val match = SnippetMatcher.find(
                currentText,
                cfg.snippetTriggerPrefix,
                cfg.snippets,
                cfg.allowTriggerAnywhere
            )
            if (match == null) {
                // The trigger was edited away (e.g. backspace) — retire a
                // variation picker that no longer applies.
                if (overlayManager.isShowing) overlayManager.hideSnippetSelection()
                return
            }

            if (match.variations.size > 1) {
                overlayManager.showSnippetSelection(
                    match.snippet.trigger,
                    match.variations,
                    isDarkMode()
                ) { selected ->
                    replaceTrigger(inputNode, match.snippet, selected, currentText)
                }
            } else {
                debounceHandler.postDelayed(
                    { replaceTrigger(inputNode, match.snippet, match.variations[0], currentText) },
                    cfg.triggerDebounceMs
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling text change", e)
        }
    }

    /**
     * Re-reads the node before splicing: the text captured when the trigger was
     * detected is stale by the time the debounce fires or the user picks a
     * variation, and splicing on it would clobber whatever was typed since.
     *
     * Re-running SnippetMatcher.find on the fresh text (restricted to the one
     * matched snippet) re-applies the end-of-text anchor and keeps production
     * on the same find/splice code the unit tests exercise.
     *
     * [capturedText] is the event-time text, used as a fallback for apps whose
     * nodes report empty text (detection has the same fallback via event.text —
     * without it those apps would detect the trigger but never expand it).
     *
     * Runs against the *current* config, not the one captured at detection
     * time: by the time the debounce fires or the user picks a variation, the
     * app may have been disabled or the snippet edited away.
     */
    private fun replaceTrigger(
        node: AccessibilityNodeInfo,
        snippet: Snippet,
        replacement: String,
        capturedText: String
    ) {
        try {
            val cfg = config
            if (!cfg.isAppEnabled) return
            val liveSnippet = cfg.snippets.find { it.trigger == snippet.trigger } ?: return
            if (replacement !in liveSnippet.contents) return
            if (!node.refresh()) return
            val freshText = node.text?.toString()?.takeIf { it.isNotEmpty() } ?: capturedText
            val fresh = SnippetMatcher.find(
                freshText,
                cfg.snippetTriggerPrefix,
                listOf(liveSnippet),
                cfg.allowTriggerAnywhere
            ) ?: return
            pasteText(node, SnippetMatcher.splice(freshText, fresh, replacement))
        } catch (e: Exception) {
            // Runs from the handler/overlay callback, outside the event's
            // try/catch — a disconnected node must not crash the process.
            Log.e(TAG, "Error replacing trigger", e)
        }
    }

    private fun pasteText(node: AccessibilityNodeInfo, text: String) {
        expansionGuard.expectServiceWrite(text)
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    override fun onInterrupt() {
        debounceHandler.removeCallbacksAndMessages(null)
        if (::overlayManager.isInitialized) overlayManager.hideAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        // A pending debounced replacement must not fire against a dead service.
        debounceHandler.removeCallbacksAndMessages(null)
        prefsListener?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) }
        prefsListener = null
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        if (::overlayManager.isInitialized) overlayManager.hideAll()
    }
}
