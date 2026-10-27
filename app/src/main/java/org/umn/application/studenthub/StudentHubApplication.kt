package org.umn.application.studenthub

import android.app.Application
import org.umn.application.studenthub.data.local.ReminderScheduler

class StudentHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.ensureChannel(this)
    }
}
