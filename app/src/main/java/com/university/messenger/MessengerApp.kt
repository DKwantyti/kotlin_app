package com.university.messenger

import android.app.Application
import com.university.messenger.data.SessionStore

class MessengerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionStore.init(this)
    }
}
