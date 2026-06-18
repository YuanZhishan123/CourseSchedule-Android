package com.cstimetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.cstimetable.MainActivity
import com.cstimetable.R
import com.cstimetable.model.Course
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 课程表桌面小部件 Provider (标准 Android AppWidget)
 *
 * 2×2 尺寸，显示今日课程（最多两节）
 */
class CourseWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val DATA_FILE = "schedule_data.json"

        /** 主应用数据变化时主动刷新所有 widget 实例 */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, CourseWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val intent = Intent(context, CourseWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, manager, widgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, widgetId, newOptions)
        updateWidget(context, manager, widgetId)
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_course_2x2)

        // 点击 widget → 打开主应用
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, widgetId, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(android.R.id.background, pendingIntent)

        bindCourseData(context, views)
        manager.updateAppWidget(widgetId, views)
    }

    private fun bindCourseData(context: Context, views: RemoteViews) {
        try {
            val dataFile = File(context.filesDir, DATA_FILE)
            if (!dataFile.exists()) {
                showEmptyState(views, "暂无数据\n请先导入课表")
                return
            }

            val root = JSONObject(dataFile.readText())
            val scheduleJson = root.getJSONObject("schedule")
            val firstWeekDate = root.optString("firstWeekStartDate", "").ifEmpty { null }
            val savedWeek = root.optInt("currentWeek", 1)

            val currentWeek = if (firstWeekDate != null) {
                try {
                    val firstMonday = LocalDate.parse(firstWeekDate)
                    val today = LocalDate.now()
                    val daysDiff = today.toEpochDay() - firstMonday.toEpochDay()
                    val w = (daysDiff / 7).toInt() + 1
                    w.coerceIn(1, scheduleJson.optInt("totalWeeks", 20))
                } catch (_: Exception) { savedWeek }
            } else savedWeek

            // 解析时间段（用于过滤已结束课程）
            val periodEndTime = mutableMapOf<Int, LocalTime>()
            if (scheduleJson.has("timeSlots")) {
                val slotsArr = scheduleJson.getJSONArray("timeSlots")
                for (i in 0 until slotsArr.length()) {
                    val slot = slotsArr.getJSONObject(i)
                    val period = slot.getInt("period")
                    val end = slot.getString("end") // "08:45"
                    periodEndTime[period] = LocalTime.parse(end, DateTimeFormatter.ofPattern("HH:mm"))
                }
            }

            val coursesArr = scheduleJson.getJSONArray("courses")
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

            val today = LocalDate.now()
            val now = LocalTime.now()
            val todayDay = today.dayOfWeek.value.let { if (it == 7) 7 else it }

            // 筛选：今日 + 当前周 + 未结束（含正在进行中的）
            val todayCourses = allCourses
                .filter { it.day == todayDay && currentWeek in it.weeks }
                .filter { course ->
                    val endKey = when {
                        // 最早结束时间对应的 key：可能是 endPeriod 或重叠课程的最后时段
                        periodEndTime.containsKey(course.endPeriod) -> course.endPeriod
                        else -> course.endPeriod // fallback：保留课程
                    }
                    val endTime = periodEndTime[endKey]
                    endTime == null || !now.isAfter(endTime) // now <= endTime
                }
                .sortedBy { it.startPeriod }

            val weekDayLabels = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
            val dateStr = "${today.format(DateTimeFormatter.ofPattern("M月d日"))} ${weekDayLabels[todayDay]}"
            views.setTextViewText(R.id.date_text, dateStr)

            if (todayCourses.isEmpty()) {
                showEmptyState(views, "今日课程已结束")
            } else {
                views.setInt(R.id.empty_hint, "setVisibility", android.view.View.GONE)
                views.setInt(R.id.course1_container, "setVisibility", android.view.View.VISIBLE)

                bindCourse(views, todayCourses[0], 1)

                if (todayCourses.size >= 2) {
                    views.setInt(R.id.course2_container, "setVisibility", android.view.View.VISIBLE)
                    views.setInt(R.id.course_divider, "setVisibility", android.view.View.VISIBLE)
                    bindCourse(views, todayCourses[1], 2)
                } else {
                    views.setInt(R.id.course2_container, "setVisibility", android.view.View.INVISIBLE)
                    views.setInt(R.id.course_divider, "setVisibility", android.view.View.INVISIBLE)
                }
            }
        } catch (e: Exception) {
            showEmptyState(views, "加载失败")
        }
    }

    private fun bindCourse(views: RemoteViews, course: Course, index: Int) {
        val nameId = if (index == 1) R.id.course1_name else R.id.course2_name
        val timeId = if (index == 1) R.id.course1_time else R.id.course2_time
        val locId = if (index == 1) R.id.course1_location else R.id.course2_location

        views.setTextViewText(nameId, course.name)
        views.setTextViewText(timeId, "${course.startPeriod}-${course.endPeriod}节")
        views.setTextViewText(locId, course.location.removePrefix("@"))
    }

    private fun showEmptyState(views: RemoteViews, hint: String) {
        views.setInt(R.id.course1_container, "setVisibility", android.view.View.GONE)
        views.setInt(R.id.course2_container, "setVisibility", android.view.View.GONE)
        views.setInt(R.id.course_divider, "setVisibility", android.view.View.GONE)
        views.setInt(R.id.empty_hint, "setVisibility", android.view.View.VISIBLE)
        views.setTextViewText(R.id.empty_hint, hint)
    }
}
