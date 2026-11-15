package cc.polysfaer.stochapop.controller.broadcast

import android.content.Context
import android.content.Intent
import cc.polysfaer.stochapop.MainApplication
import cc.polysfaer.stochapop.controller.NotificationChannels

class BootReceiver : AsyncBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as MainApplication
            val remindersRepository = app.container.remindersRepository
            val schedulerRepository = app.container.schedulerRepository

            goAsync {
                val reminders = remindersRepository.getAllReminders()
                val activeReminders = reminders.filter { it.enabled }
                NotificationChannels.syncChannels(context, activeReminders)
                activeReminders.forEach {
                    schedulerRepository.scheduleReminderAlarms(it)
                }
            }
        }
    }
}
