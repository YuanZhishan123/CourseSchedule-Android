package com.cstimetable.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 定时唤醒接收器
 *
 * 使用 AlarmManager 定时触发，检查是否有即将开始的课程。
 * 在 AndroidManifest 中注册为静态 Receiver。
 *
 * ## 唤醒频率
 *
 * 每 15 分钟检查一次（兼顾省电和时效性）。
 * 如果你希望更精确的提醒，可以改为 5 分钟。
 *
 * ## 注册方法
 *
 * 调用 [schedule] 开始定时，调用 [cancel] 停止。
 */
class CourseAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_CHECK = "com.cstimetable.notify.CHECK_COURSE"
        private const val REQUEST_CODE = 7001

        /**
         * 启动定时检查
         *
         * 调用时机：App 启动、课程数据变更后
         */
        fun schedule(context: Context) {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, CourseAlarmReceiver::class.java).apply {
                action = ACTION_CHECK
            }
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 每 15 分钟触发一次
            val intervalMs = 15 * 60 * 1000L
            val triggerTime = System.currentTimeMillis() + intervalMs

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ 不允许精确重复闹钟，用不精确的
                alarmMgr.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    intervalMs,
                    pending
                )
            } else {
                alarmMgr.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    intervalMs,
                    pending
                )
            }
        }

        /**
         * 取消定时检查
         *
         * 调用时机：课程数据清空后
         */
        fun cancel(context: Context) {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, CourseAlarmReceiver::class.java).apply {
                action = ACTION_CHECK
            }
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmMgr.cancel(pending)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CHECK ||
            intent.action == Intent.ACTION_BOOT_COMPLETED) {

            val helper = CourseNotificationHelper.getInstance()
                ?: CourseNotificationHelper.init(context)

            val sent = helper.sendCourseReminders()

            // 如果没有课程数据，停止定时检查
            if (sent < 0) {
                cancel(context)
            }
        }
    }
}
