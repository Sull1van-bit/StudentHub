package org.umn.application.studenthub.data.local

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.umn.application.studenthub.MainActivity
import org.umn.application.studenthub.R
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Pengingat StudentHub"
        val detail = intent.getStringExtra(EXTRA_DETAIL) ?: ""
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "Umum"

        val isEnabled = runCatching {
            val db = StudentHubDatabase.get(context)
            val found = db.reminders().firstOrNull { it.id == reminderId }
            found?.enabled ?: true
        }.getOrDefault(true)

        if (isEnabled) {
            ReminderScheduler.postNotification(
                context = context,
                notificationId = reminderId.hashCode(),
                title = "[$category] $title",
                detail = detail.ifBlank { "Waktunya kegiatan terjadwal di StudentHub." }
            )
        }
    }

    companion object {
        const val EXTRA_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_reminder_title"
        const val EXTRA_DETAIL = "extra_reminder_detail"
        const val EXTRA_CATEGORY = "extra_reminder_category"
    }
}

object ReminderScheduler {
    const val CHANNEL_ID = "studenthub_reminders_channel"
    private const val CHANNEL_NAME = "Pengingat StudentHub"
    private const val CHANNEL_DESC = "Notifikasi pengingat kelas, tugas, belajar, dan keuangan"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun scheduleReminder(context: Context, reminder: ReminderItemRow) {
        ensureChannel(context)
        if (!reminder.enabled) {
            cancelReminder(context, reminder.id)
            return
        }
        val clean = reminder.time.substringBefore('–').substringBefore('-').trim().replace(':', '.')
        val parts = clean.split('.')
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return
        if (hour !in 0..23 || minute !in 0..59) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID, reminder.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
            putExtra(ReminderReceiver.EXTRA_DETAIL, reminder.detail)
            putExtra(ReminderReceiver.EXTRA_CATEGORY, reminder.category)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = Calendar.getInstance()
        val weekdayMap = mapOf(
            "senin" to Calendar.MONDAY,
            "selasa" to Calendar.TUESDAY,
            "rabu" to Calendar.WEDNESDAY,
            "kamis" to Calendar.THURSDAY,
            "jumat" to Calendar.FRIDAY,
            "sabtu" to Calendar.SATURDAY,
            "minggu" to Calendar.SUNDAY
        )
        val detailLower = reminder.detail.lowercase()
        val matchedCalendarDay = weekdayMap.entries.firstOrNull { detailLower.contains(it.key) }?.value

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (matchedCalendarDay != null) {
                while (get(Calendar.DAY_OF_WEEK) != matchedCalendarDay || !after(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            } else if (!after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, reminderId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { alarmManager?.cancel(pendingIntent) }
        runCatching { NotificationManagerCompat.from(context).cancel(reminderId.hashCode()) }
    }

    fun syncAllReminders(context: Context) {
        runCatching {
            val db = StudentHubDatabase.get(context)
            db.reminders().forEach { reminder ->
                if (reminder.enabled) {
                    scheduleReminder(context, reminder)
                } else {
                    cancelReminder(context, reminder.id)
                }
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun postNotification(context: Context, notificationId: Int, title: String, detail: String): Boolean {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) return false
        return runCatching {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.hub_bell)
                .setContentTitle(title)
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }
}
