package com.simplesnippet.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast

class OverlayManager(private val context: Context) {

    private var windowManager: WindowManager? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun showToast(message: String) {
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    private var snippetSelectionView: FrameLayout? = null

    // Set synchronously on the accessibility thread before mainHandler.post runs, and read from
    // both that thread and the main thread (via isShowing), so it must be @Volatile.
    @Volatile
    private var currentSnippetTrigger: String? = null

    val isShowing: Boolean get() = currentSnippetTrigger != null

    // Always points at the latest caller's callback: the same-trigger early
    // return below keeps the existing views, but the selection must land in
    // whatever field the service most recently matched, not the original one.
    private var onSelectedCurrent: ((String) -> Unit)? = null

    fun showSnippetSelection(trigger: String, variations: List<String>, isDarkMode: Boolean, onSelected: (String) -> Unit) {
        onSelectedCurrent = onSelected
        if (currentSnippetTrigger == trigger) return // Already showing this one

        currentSnippetTrigger = trigger

        mainHandler.post {
            removeSnippetSelectionInternal()

            // These intentionally duplicate the Material 3 palette in ui/Theme.kt: this overlay
            // is a plain WindowManager view drawn outside Compose, so it can't read MaterialTheme
            // colors. Keep the two in sync when the app theme changes.
            val cardBgColor = if (isDarkMode) 0xFF1C1B1F.toInt() else 0xFFFFFBFE.toInt()
            val primaryTextColor = if (isDarkMode) 0xFF818CF8.toInt() else 0xFF4F46E5.toInt()
            val secondaryTextColor = if (isDarkMode) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
            val surfaceVariantColor = if (isDarkMode) 0xFF49454F.toInt() else 0xFFE7E0EC.toInt()
            val primaryColor = if (isDarkMode) 0xFF818CF8.toInt() else 0xFF4F46E5.toInt()

            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
                background = GradientDrawable().apply {
                    setColor(cardBgColor)
                    cornerRadius = 32f
                    setStroke(3, primaryColor)
                }
                isClickable = true
                elevation = 20f
            }

            val title = android.widget.TextView(context).apply {
                text = "Select Variation: $trigger"
                textSize = 18f
                setTextColor(primaryTextColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 20)
            }
            container.addView(title)

            val scrollView = android.widget.ScrollView(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    0
                ).apply {
                    weight = 1f
                }
            }
            // Constrain height to 35% of screen like preview dialog
            scrollView.layoutParams.height = (context.resources.displayMetrics.heightPixels * 0.35).toInt()

            val list = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
            }

            variations.forEach { variation ->
                val item = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(24, 32, 24, 32)
                    isClickable = true
                    val outValue = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)

                    setOnClickListener {
                        onSelectedCurrent?.invoke(variation)
                        hideSnippetSelection()
                    }
                }

                val content = android.widget.TextView(context).apply {
                    text = variation
                    textSize = 14f
                    setTextColor(secondaryTextColor)
                    maxLines = 4
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                item.addView(content)

                // Divider
                val divider = android.view.View(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2
                    )
                    setBackgroundColor(surfaceVariantColor)
                }

                list.addView(item)
                list.addView(divider)
            }
            scrollView.addView(list)
            container.addView(scrollView)

            val btnRow = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, 30, 0, 0)
            }

            val closeBtn = Button(context).apply {
                text = "Cancel"
                setTextColor(primaryTextColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = android.util.TypedValue().let { tv ->
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                    context.resources.getDrawable(tv.resourceId, context.theme)
                }
                setPadding(15, 20, 15, 20)
                setOnClickListener { hideSnippetSelection() }
            }
            btnRow.addView(closeBtn)
            container.addView(btnRow)

            snippetSelectionView = FrameLayout(context)
            snippetSelectionView?.addView(container)

            val params = WindowManager.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.80).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            try {
                windowManager?.addView(snippetSelectionView, params)
            } catch (e: Exception) {
                // Reset the flag or isShowing would report a picker that never
                // appeared, blocking every retry for this trigger.
                snippetSelectionView = null
                currentSnippetTrigger = null
            }
        }
    }

    fun hideSnippetSelection() {
        currentSnippetTrigger = null
        onSelectedCurrent = null
        mainHandler.post {
            removeSnippetSelectionInternal()
        }
    }

    private fun removeSnippetSelectionInternal() {
        if (snippetSelectionView != null) {
            try {
                windowManager?.removeView(snippetSelectionView)
            } catch (e: Exception) {
            } finally {
                snippetSelectionView = null
            }
        }
    }

    fun hideAll() {
        hideSnippetSelection()
    }
}
