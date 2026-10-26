package com.university.messenger.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.university.messenger.data.SessionStore
import com.university.messenger.ui.login.LoginActivity

data class ScreenState(
    val loading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

fun AppCompatActivity.logout() {
    SessionStore.clearSession()
    val intent = Intent(this, LoginActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    startActivity(intent)
}
