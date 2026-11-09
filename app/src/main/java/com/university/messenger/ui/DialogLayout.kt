package com.university.messenger.ui

import android.content.Context
import android.view.View
import android.widget.ScrollView

/** Custom dialog forms scroll on short displays and when the keyboard opens. */
fun Context.dialogContent(content: View): View = ScrollView(this).apply {
    val inset = (24 * resources.displayMetrics.density).toInt()
    setPadding(inset, 8, inset, 8)
    clipToPadding = false
    addView(content)
}
