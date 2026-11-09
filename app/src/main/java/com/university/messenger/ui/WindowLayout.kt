package com.university.messenger.ui

import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.doOnNextLayout

/** Keep content outside system bars and above the keyboard, including edge-to-edge Android. */
fun AppCompatActivity.fitWindowContent(root: View) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    WindowCompat.getInsetsController(window, root).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
    val left = root.paddingLeft; val top = root.paddingTop
    val right = root.paddingRight; val bottom = root.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
        val paddingChanged = view.paddingBottom != bottom + bars.bottom
        if (paddingChanged && view is ScrollView && insets.isVisible(WindowInsetsCompat.Type.ime())) {
            view.doOnNextLayout {
                view.findFocus()?.let { focused ->
                    if (focused !== view) {
                        val field = Rect(0, 0, focused.width, focused.height)
                        view.offsetDescendantRectToMyCoords(focused, field)
                        val labelSpace = (16 * resources.displayMetrics.density).toInt()
                        view.scrollTo(0, (field.top - view.paddingTop - labelSpace).coerceAtLeast(0))
                    }
                }
            }
        }
        view.updatePadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(root)
}
