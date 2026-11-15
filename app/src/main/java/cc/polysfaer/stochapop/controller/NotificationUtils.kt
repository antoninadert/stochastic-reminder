package cc.polysfaer.stochapop.controller

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.polysfaer.stochapop.MainActivity
import cc.polysfaer.stochapop.R
import cc.polysfaer.stochapop.data.reminder.Reminder
import cc.polysfaer.stochapop.data.reminder.ReminderScheduleType
import cc.polysfaer.stochapop.data.reminder.ReminderSettings

object NotificationChannels {
    fun hasPostNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission( context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getChannelIdPrefix(reminderId: Int): String {
        return "reminder_${reminderId}_"
    }

    fun getChannelId(reminder: Reminder): String {
        return getChannelId(
            reminderId = reminder.id,
            hasSound = reminder.hasSound,
            hasVibration = reminder.hasVibration,
            soundUri = reminder.soundUri
        )
    }

    fun getChannelId(
        reminderId: Int,
        hasSound: Boolean,
        hasVibration: Boolean,
        soundUri: Uri? = ReminderSettings.DEFAULT_NOTIFICATION_URI
    ): String {
        val soundHash = if (hasSound) {
            val uri = soundUri ?: ReminderSettings.DEFAULT_NOTIFICATION_URI
            uri.toString().hashCode().toString()
        } else {
            "NOURI"
        }
        val vibrationStr = if (hasVibration) "V" else "NV"
        return "${getChannelIdPrefix(reminderId)}${soundHash}_${vibrationStr}"
    }

    /**
     * Create or update the notification channel for a specific reminder.
     * Ensures channel name matches the reminder title and sound/vibration are in sync.
     * Also deletes any outdated channels for this reminder (e.g. from prior sound settings).
     */
    fun getOrCreateChannel(
        context: Context,
        reminder: Reminder
    ): String {
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return getChannelId(reminder)

        val targetChannelId = getChannelId(reminder)
        val channelName = reminder.title.trim().ifBlank {
            context.getString(R.string.edit_reminder_default_title)
        }

        val existingChannel = manager.getNotificationChannel(targetChannelId)
        if (existingChannel != null) {
            if (existingChannel.name != channelName) {
                existingChannel.name = channelName
                manager.createNotificationChannel(existingChannel)
            }
        } else {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val visibility = Notification.VISIBILITY_PUBLIC

            val channel = NotificationChannel(targetChannelId, channelName, importance).apply {
                lockscreenVisibility = visibility
                enableVibration(reminder.hasVibration)
                if (reminder.hasSound) {
                    val soundUri = reminder.soundUri ?: ReminderSettings.DEFAULT_NOTIFICATION_URI
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                    setSound(soundUri, audioAttributes)
                } else {
                    setSound(null, null)
                }
            }
            manager.createNotificationChannel(channel)
        }

        // Clean up any other channels belonging to this reminder (e.g. with older sound/vibration settings)
        val prefix = getChannelIdPrefix(reminder.id)
        val channels = manager.notificationChannels ?: emptyList()
        for (channel in channels) {
            if (channel.id.startsWith(prefix) && channel.id != targetChannelId) {
                manager.deleteNotificationChannel(channel.id)
            }
        }

        return targetChannelId
    }

    /**
     * Delete any notification channels associated with a specific reminder.
     */
    fun deleteChannelForReminder(context: Context, reminderId: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val prefix = getChannelIdPrefix(reminderId)
        val channels = manager.notificationChannels ?: return
        for (channel in channels) {
            if (channel.id.startsWith(prefix)) {
                manager.deleteNotificationChannel(channel.id)
            }
        }
    }

    /**
     * Synchronize all notification channels with the list of active reminders.
     * Creates/updates channels for active reminders with their current titles and sounds,
     * and deletes any notification channels that do not correspond to an active reminder.
     */
    fun syncChannels(context: Context, activeReminders: List<Reminder>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // 1. Ensure channels for all active reminders and collect active channel IDs
        val activeChannelIds = mutableSetOf<String>()
        for (reminder in activeReminders) {
            val channelId = getOrCreateChannel(context, reminder)
            activeChannelIds.add(channelId)
        }

        // 2. Delete any existing channels not in activeChannelIds
        val existingChannels = manager.notificationChannels ?: return
        for (channel in existingChannels) {
            if (channel.id != NotificationChannel.DEFAULT_CHANNEL_ID && channel.id !in activeChannelIds) {
                manager.deleteNotificationChannel(channel.id)
            }
        }
    }

    @Deprecated("Use getChannelId(reminder) instead")
    fun getChannelId(
        isUrgent: Boolean,
        isPublic: Boolean,
        hasSound: Boolean,
        hasVibration: Boolean,
        soundUri: Uri? = ReminderSettings.DEFAULT_NOTIFICATION_URI
    ): String {
        return getChannelId(
            reminderId = 0,
            hasSound = hasSound,
            hasVibration = hasVibration,
            soundUri = soundUri
        )
    }

    @Deprecated("Use getOrCreateChannel(context, reminder) instead")
    fun createChannel(
        context: Context,
        isUrgent: Boolean,
        isPublic: Boolean,
        hasSound: Boolean,
        hasVibration: Boolean,
        soundUri: Uri?
    ): String {
        val dummy = Reminder(
            id = 0,
            title = "",
            message = "",
            enabled = true,
            hasSound = hasSound,
            hasVibration = hasVibration,
            soundUri = soundUri,
            scheduleType = ReminderScheduleType.FIXED,
            notificationCount = 1,
            startTime = java.time.LocalTime.MIN,
            endTime = java.time.LocalTime.MAX,
            selectedDays = java.time.DayOfWeek.entries.toSet()
        )
        return getOrCreateChannel(context, dummy)
    }
}

// ------------------------------------------------------------------------------------------------

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun sendNotification(
    context: Context,
    title: String,
    message: String,
    summaryNotificationId: Int,
    groupKey: String,
    channelId: String,
    notificationId: Int = System.currentTimeMillis().toInt(),
    backToActivityOnClick: Boolean = true
) {
    /* Handle click on notification launching the App's activity. */
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        context,
        summaryNotificationId,
        intent,
        PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_reminder_notification)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setAutoCancel(true)
        .setGroup(groupKey)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .setContentIntent(if (backToActivityOnClick) pendingIntent else null)

    val summaryNotification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_reminder_notification)
        .setGroup(groupKey)
        .setGroupSummary(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .setAutoCancel(true)
        .setContentIntent(if (backToActivityOnClick) pendingIntent else null)
        .build()

    with(NotificationManagerCompat.from(context)) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notify(notificationId, builder.build())
            notify(summaryNotificationId, summaryNotification)
        }
    }
}

fun sendNotification(
    context: Context,
    reminder: Reminder,
    backToActivityOnClick: Boolean = true
) {
    val summaryNotificationId = reminder.id

    val channelId = NotificationChannels.getOrCreateChannel(
        context = context,
        reminder = reminder
    )

    sendNotification(
        context = context,
        title = reminder.title,
        message = reminder.message,
        summaryNotificationId = summaryNotificationId,
        groupKey = "${context.getString(R.string.app_name)}_${summaryNotificationId}",
        channelId = channelId,
        backToActivityOnClick = backToActivityOnClick
    )
}
