package dev.pinganator

import android.app.Application
import dev.pinganator.data.db.AppDatabase

class PingApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
