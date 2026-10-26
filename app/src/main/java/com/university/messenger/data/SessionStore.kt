package com.university.messenger.data

import android.content.Context

object SessionStore {
    private const val FILE = "session"
    private const val TOKEN = "token"
    private const val ROLE = "role"
    private const val NAME = "name"
    private const val BASE_URL = "base_url"

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    var token: String?
        get() = prefs.getString(TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(TOKEN, value).apply()
        }

    var role: String?
        get() = prefs.getString(ROLE, null)
        set(value) {
            prefs.edit().putString(ROLE, value).apply()
        }

    var displayName: String?
        get() = prefs.getString(NAME, null)
        set(value) {
            prefs.edit().putString(NAME, value).apply()
        }

    var baseUrl: String
        get() = prefs.getString(BASE_URL, null)?.takeIf { it.isNotBlank() } ?: "http://10.0.2.2:8080"
        set(value) {
            prefs.edit().putString(BASE_URL, value.trim().trimEnd('/')).apply()
        }

    fun saveLogin(token: String, role: String, displayName: String) {
        this.token = token
        this.role = role
        this.displayName = displayName
    }

    fun clearSession() {
        prefs.edit().remove(TOKEN).remove(ROLE).remove(NAME).apply()
    }
}
