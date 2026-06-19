package com.cstimetable.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cstimetable.MainActivity
import com.cstimetable.model.Course
import com.cstimetable.model.TimeSlot
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 课程通知提醒助手
 *
 * ## 通知如何到达手环？
 *
 * 通知通过 Android 标准 NotificationManager 发出后，
 * 小米运动健康 App 会自动通过蓝牙将通知推送到手环。
 * 这是系统级行为，无需额外开发。
 *
 * 你的手环上会看到：
 * ```
 * ┌──────────────────┐
 * │ 📚 即将上课       │  ← 通知标题
 * │ 计算机网络        │  ← 课程名（大字）
 * │ 08:00-09:35      │  ← 时间
 * │ 广宇楼206        │  ← 地点
 * └──────────────────┘
 * ```
 *
 * ## 集成方式
 *
 * 在 Application.onCreate() 中调用 init()，
 * 在 ViewModel 中每次课程数据变更后调用 scheduleNext()。
 */
class CourseNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "course_reminder"
        const val CHANNEL_NAME = "课程提醒"
        const val NOTIFY_ID_BASE = 5000

        private const val DATA_FILE = "schedule_data.json"

        // -------- 可调参数 --------

        /** 课前多少分钟提醒（可改为列表，如 listOf(5, 10) 表示课前5分钟和10分钟各提醒一次） */
        var remindMinutesBefore: List<Int> = listOf(10)

        /** 一天中最晚的提醒时间（超过此时间不再提醒） */
        var latestRemindTime: LocalTime = LocalTime.of(21, 0)

        // --------------------------

        /** 单例（进程级） */
        @Volatile private var instance: CourseNotificationHelper? = null

        fun init(context: Context): CourseNotificationHelper {
            return instance ?: synchronized(this) {
                instance ?: CourseNotificationHelper(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun getInstance(): CourseNotificationHelper? = instance
    }

    // ── 公开 API ──────────────────────────────────────

    /**
     * 发送所有即将上课的通知。
     *
     * 调用时机：
     * - App 启动
     * - 课程数据变更后
     * - AlarmManager 定时触发
     *
     * @return 实际发送了几条通知
     */
    fun sendCourseReminders(): Int {
        val courses = loadTodayCourses() ?: return 0
        val now = LocalTime.now()

        var sent = 0
        for (course in courses) {
            val startTime = getStartTime(course) ?: continue
            val minutesUntil = java.time.Duration.between(now, startTime).toMinutes()

            // 只提醒未来课程
            if (minutesUntil < 0) continue

            // 检查是否匹配提醒时间窗口
            val matchedMinute = remindMinutesBefore.firstOrNull { m ->
                minutesUntil in (m - 2)..(m + 2)  // ±2分钟容差
            } ?: continue

            // 检查是否超过最晚提醒时间
            if (now.isAfter(latestRemindTime)) continue

            sendNotification(course, matchedMinute, minutesUntil.toInt())
            sent++
        }

        return sent
    }

    /**
     * 确保通知渠道已创建（公开方法，供外部测试使用）
     *
     * Android 8.0+ 必须先创建 NotificationChannel 才能发送通知，
     * 否则通知会被系统静默丢弃。
     */
    fun ensureChannel() {
        createChannel()
    }

    /**
     * 获取"下节课"信息（不发送通知，供 UI 查询）
     */
    fun getNextCourse(): NextCourseInfo? {
        val courses = loadTodayCourses() ?: return null
        val now = LocalTime.now()

        val next = courses
            .filter { getStartTime(it)?.let { s -> s.isAfter(now) } ?: false }
            .minByOrNull { getStartTime(it)!! }

        return next?.let { course ->
            val startTime = getStartTime(course)!!
            val minutesUntil = java.time.Duration.between(now, startTime).toMinutes()
            NextCourseInfo(
                course = course,
                startTime = startTime,
                minutesUntil = minutesUntil.toInt(),
            )
        }
    }

    // ── 内部实现 ──────────────────────────────────────

    private fun loadTodayCourses(): List<Course>? {
        return try {
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

            // 解析时间段（获取每节课的开始时间）
            val periodStartTime = mutableMapOf<Int, LocalTime>()
            if (scheduleJson.has("timeSlots")) {
                val slotsArr = scheduleJson.getJSONArray("timeSlots")
                for (i in 0 until slotsArr.length()) {
                    val slot = slotsArr.getJSONObject(i)
                    val period = slot.getInt("period")
                    val start = slot.getString("start")
                    periodStartTime[period] = LocalTime.parse(start, DateTimeFormatter.ofPattern("HH:mm"))
                }
            }

            // 解析课程
            val coursesArr = scheduleJson.getJSONArray("courses")
            val today = LocalDate.now()
            val todayDay = today.dayOfWeek.value.let { if (it == 7) 7 else it }

            val allCourses = (0 until coursesArr.length()).map { i ->
                val co = coursesArr.getJSONObject(i)
                Course(
                    name = co.getString("name"),
                    day = co.getInt("day"),
                    startPeriod = co.getInt("startPeriod"),
                    endPeriod = co.getInt("endPeriod"),
                    weeks = co.getJSONArray("weeks").let { arr ->
                        (0 until arr.length()).map { arr.getInt(it) }
                    },
                    location = co.optString("location", ""),
                    teacher = co.optString("teacher", ""),
                    color = co.optLong("color", 0),
                )
            }

            allCourses
                .filter { it.day == todayDay && currentWeek in it.weeks }
                .sortedBy { it.startPeriod }
                .ifEmpty { null }

        } catch (_: Exception) { null }
    }

    /** 获取课程开始时间 */
    private fun getStartTime(course: Course): LocalTime? {
        // 优先用缓存的时段表
        val savedTime = cachedStartTimes[course.startPeriod]
        if (savedTime != null) return savedTime

        // 回退到默认时段表
        return defaultTimeSlots[course.startPeriod]
    }

    /** 缓存的时段表（与 loadTodayCourses 同时填充） */
    private val cachedStartTimes = mutableMapOf<Int, LocalTime>()

    // 默认时段表（硬编码，与 Model.kt 保持一致）
    private val defaultTimeSlots = mapOf(
        1 to LocalTime.of(8, 0),   2 to LocalTime.of(8, 50),
        3 to LocalTime.of(9, 55),  4 to LocalTime.of(10, 45),
        5 to LocalTime.of(11, 35), 6 to LocalTime.of(13, 30),
        7 to LocalTime.of(14, 20), 8 to LocalTime.of(15, 20),
        9 to LocalTime.of(16, 10), 10 to LocalTime.of(18, 0),
        11 to LocalTime.of(18, 50), 12 to LocalTime.of(19, 40),
    )

    private fun sendNotification(course: Course, remindMinutes: Int, minutesUntil: Int) {
        createChannel()

        val notifyId = NOTIFY_ID_BASE + course.startPeriod

        val timeRange = "${course.startPeriod}-${course.endPeriod}节"

        val title = when {
            minutesUntil <= 5 -> "🔔 马上就要上课了"
            minutesUntil <= 15 -> "⏰ 即将上课"
            else -> "📚 课程提醒"
        }

        // 点击通知 → 打开主应用
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifyId, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 可替换为自定义图标
            .setContentTitle(title)
            .setContentText("${course.name}  $timeRange  ${course.location.removePrefix("@")}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${course.name}\n${timeRange}\n${course.location.removePrefix("@")}\n还有 ${minutesUntil} 分钟上课")
                .setBigContentTitle(title)
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)       // 高优先级 → 手环会振动
            .setCategory(NotificationCompat.CATEGORY_REMINDER)    // 归类为提醒
            .setDefaults(android.app.Notification.DEFAULT_VIBRATE) // 振动
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifyId, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH  // 高重要性 → 确保推送到手环
            ).apply {
                description = "课程上课提醒通知"
                enableVibration(true)
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        }
    }
}

/** 下节课信息 */
data class NextCourseInfo(
    val course: Course,
    val startTime: LocalTime,
    val minutesUntil: Int,  // 还有多少分钟上课
)
