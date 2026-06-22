package com.cstimetable.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 精准课程闹钟接收器
 *
 * 不再使用轮询，改为为每一节课的提醒时间设置精准闹钟。
 * 每次闹钟触发后，自动计算下一节课的提醒时间并重新调度。
 *
 * ## 工作流程
 *
 * schedule(context) → 找到最近的提醒时间 → setExact 精准闹钟
 * onReceive()      → 发通知 → schedule(context) 设置下一个
 *
 * ## 可靠性的保证
 *
 * - 每次 App 启动 / 课表变更 → 重算所有提醒时间
 * - Android 12+ 使用 setExactAndAllowWhileIdle 保证 doze 模式下准时唤醒
 */
class CourseAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_ALARM = "com.cstimetable.notify.COURSE_ALARM"
        private const val REQUEST_CODE = 7100
        private const val DATA_FILE = "schedule_data.json"
        private const val MAX_FUTURE_DAYS = 7  // 最多调度未来7天

        /** 默认时段表 */
        private val defaultTimeSlots = mapOf(
            1 to LocalTime.of(8, 0),   2 to LocalTime.of(8, 50),
            3 to LocalTime.of(9, 55),  4 to LocalTime.of(10, 45),
            5 to LocalTime.of(11, 35), 6 to LocalTime.of(13, 30),
            7 to LocalTime.of(14, 20), 8 to LocalTime.of(15, 20),
            9 to LocalTime.of(16, 10), 10 to LocalTime.of(18, 0),
            11 to LocalTime.of(18, 50), 12 to LocalTime.of(19, 40),
        )

        /**
         * 调度下一次提醒闹钟。
         *
         * 逻辑：
         * 1. 读取课表数据
         * 2. 扫描本周 + 未来 MAX_FUTURE_DAYS 天内所有课程
         * 3. 为每节课计算提醒时间（startTime - remindMinutes）
         * 4. 找到最近的一个未来提醒时间
         * 5. 设置精准闹钟
         *
         * 调用时机：App 启动、课表变更、每次闹钟触发后
         */
        fun schedule(context: Context) {
            val pending = getPendingIntent(context)
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // 先取消旧的
            alarmMgr.cancel(pending)

            val nextReminder = findNextReminder(context) ?: return

            val triggerMs = nextReminder.zonedTrigger.toInstant().toEpochMilli()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmMgr.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerMs, pending
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmMgr.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerMs, pending
                    )
                } else {
                    alarmMgr.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pending)
                }
            } catch (_: SecurityException) {
                // 降级：没有精准闹钟权限时，用不精确闹钟（3分钟窗口基本够用）
                alarmMgr.set(AlarmManager.RTC_WAKEUP, triggerMs, pending)
            }
        }

        /**
         * 取消所有闹钟
         */
        fun cancel(context: Context) {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmMgr.cancel(getPendingIntent(context))
        }

        private fun getPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, CourseAlarmReceiver::class.java).apply {
                action = ACTION_ALARM
            }
            return PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // ── 核心：找最近提醒时间 ──────────────────────

        /**
         * 一条提醒记录
         * @param triggerTime 提醒触发时间（本地时间）
         * @param courseName 课程名（用于通知）
         */
        data class Reminder(
            val triggerTime: LocalTime,
            val triggerDate: LocalDate,
            val courseName: String,
            val startPeriod: Int,
            val endPeriod: Int,
            val location: String,
            val minutesUntilClass: Int,
        ) {
            val zonedTrigger: ZonedDateTime
                get() = ZonedDateTime.of(triggerDate, triggerTime, ZoneId.systemDefault())
        }

        private fun findNextReminder(context: Context): Reminder? {
            try {
                val dataFile = File(context.filesDir, DATA_FILE)
                if (!dataFile.exists()) return null

                val root = JSONObject(dataFile.readText())
                val scheduleJson = root.getJSONObject("schedule")

                // 计算实际周数
                val firstWeekDate = root.optString("firstWeekStartDate", "").ifEmpty { null }
                val savedWeek = root.optInt("currentWeek", 1)
                val totalWeeks = scheduleJson.optInt("totalWeeks", 20)

                val currentWeek = if (firstWeekDate != null) {
                    try {
                        val firstMonday = LocalDate.parse(firstWeekDate)
                        val today = LocalDate.now()
                        val daysDiff = today.toEpochDay() - firstMonday.toEpochDay()
                        val w = (daysDiff / 7).toInt() + 1
                        w.coerceIn(1, totalWeeks)
                    } catch (_: Exception) { savedWeek }
                } else savedWeek

                // 解析时段
                val periodStartTime = mutableMapOf<Int, LocalTime>()
                if (scheduleJson.has("timeSlots")) {
                    val slotsArr = scheduleJson.getJSONArray("timeSlots")
                    for (i in 0 until slotsArr.length()) {
                        val slot = slotsArr.getJSONObject(i)
                        periodStartTime[slot.getInt("period")] =
                            LocalTime.parse(slot.getString("start"), DateTimeFormatter.ofPattern("HH:mm"))
                    }
                }

                // 解析课程
                val coursesArr = scheduleJson.getJSONArray("courses")
                val now = LocalTime.now()
                val today = LocalDate.now()
                val todayDay = today.dayOfWeek.value.let { if (it == 7) 7 else it }

                val remindMinutes =
                    CourseNotificationHelper.getInstance()?.let {
                        CourseNotificationHelper.remindMinutesBefore
                    } ?: listOf(10)

                val allReminders = mutableListOf<Reminder>()

                for (i in 0 until coursesArr.length()) {
                    val co = coursesArr.getJSONObject(i)
                    val day = co.getInt("day")
                    val startPeriod = co.getInt("startPeriod")
                    val endPeriod = co.getInt("endPeriod")
                    val weeks = co.getJSONArray("weeks").let { arr ->
                        (0 until arr.length()).map { arr.getInt(it) }
                    }
                    val name = co.getString("name")
                    val location = co.optString("location", "")

                    // 只关心当前周
                    if (currentWeek !in weeks) continue

                    val startTime = periodStartTime[startPeriod]
                        ?: defaultTimeSlots[startPeriod]
                        ?: continue

                    for (rm in remindMinutes) {
                        val remindTime = startTime.minusMinutes(rm.toLong())
                        if (remindTime.isBefore(LocalTime.MIDNIGHT)) continue // 跨天了，不处理

                        // 计算是哪一天
                        val daysOffset = if (day >= todayDay) {
                            day - todayDay
                        } else {
                            // 下周
                            day + 7 - todayDay
                        }

                        val remindDate = today.plusDays(daysOffset.toLong())
                        if (daysOffset > MAX_FUTURE_DAYS) continue

                        // 跳过已过期的（今天 + 已过时间）
                        if (daysOffset == 0 && remindTime.isBefore(now)) continue

                        allReminders.add(Reminder(
                            triggerTime = remindTime,
                            triggerDate = remindDate,
                            courseName = name,
                            startPeriod = startPeriod,
                            endPeriod = endPeriod,
                            location = location,
                            minutesUntilClass = rm,
                        ))
                    }
                }

                // 返回最近的一个
                return allReminders.minByOrNull { it.zonedTrigger.toEpochSecond() }

            } catch (_: Exception) { return null }
        }
    }

    // ── 闹钟触发 ────────────────────────────────────

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ALARM ||
            intent.action == Intent.ACTION_BOOT_COMPLETED) {

            val helper = CourseNotificationHelper.getInstance()
                ?: CourseNotificationHelper.init(context)

            helper.sendCourseReminders()

            // 调度下一个提醒
            schedule(context)
        }
    }
}
