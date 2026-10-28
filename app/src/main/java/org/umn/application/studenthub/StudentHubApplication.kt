package org.umn.application.studenthub

import android.app.Application
import org.umn.application.studenthub.data.local.ReminderScheduler
import org.umn.application.studenthub.ui.notes.NoteAiAssistant

class StudentHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.ensureChannel(this)
        NoteAiAssistant.init(this)
    }
}
