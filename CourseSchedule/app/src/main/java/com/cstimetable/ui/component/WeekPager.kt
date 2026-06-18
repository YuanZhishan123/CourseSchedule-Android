package com.cstimetable.ui.component

import android.content.Context
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.cstimetable.model.Course
import com.cstimetable.model.Schedule
import com.cstimetable.viewmodel.ScheduleViewModel

/**
 * ViewPager2 适配器 — 每页一个 ComposeView 渲染 CourseGrid
 *
 * 使用原生 ViewPager2 实现丝滑的周次切换，避免 Compose HorizontalPager 的性能问题。
 */
class WeekPagerAdapter(
    private val viewModel: ScheduleViewModel,
    private val todayDayOfWeek: Int,
    private val onCourseClick: (Course) -> Unit,
    private val onAddCourseRequest: ((Int, Int) -> Unit)? = null,
) : RecyclerView.Adapter<WeekPagerAdapter.PageHolder>() {

    var schedule: Schedule? = null
    var currentWeek: Int = 1

    override fun getItemCount(): Int = schedule?.totalWeeks ?: 20

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val composeView = ComposeView(parent.context)
        composeView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return PageHolder(composeView)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val week = position + 1
        val s = schedule ?: return
        val weekCourses = s.courses.filter { week in it.weeks }
        val firstMonday = viewModel.state.value.firstWeekStartDate?.let {
            try { java.time.LocalDate.parse(it) } catch (_: Exception) { null }
        }
        val weekDays = generateWeekDays(week, firstMonday)
        val isCurrentWeek = week == currentWeek

        holder.composeView.setContent {
            CourseGrid(
                courses = weekCourses,
                timeSlots = s.timeSlots,
                onCourseClick = onCourseClick,
                currentWeek = week,
                allCourses = s.courses,
                weekDays = weekDays,
                currentDay = if (isCurrentWeek) todayDayOfWeek else 0,
                onAddCourseRequest = onAddCourseRequest,
            )
        }
    }

    inner class PageHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView)

    /**
     * ViewPager2 页面变更回调 — 同步到 ViewModel
     */
    class PageChangeCallback(
        private val adapter: WeekPagerAdapter,
        private val onPageSelected: (Int) -> Unit,
    ) : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            onPageSelected(position)
        }
    }
}

/** 生成星期日期数据 */
private fun generateWeekDays(weekNumber: Int, firstMonday: java.time.LocalDate?): List<WeekDay> {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val today = java.time.LocalDate.now()
    val weekMonday = if (firstMonday != null) {
        firstMonday.plusWeeks((weekNumber - 1).toLong())
    } else {
        today.with(java.time.temporal.WeekFields.of(java.util.Locale.CHINA).dayOfWeek(), 1)
    }
    val todayMonday = today.with(java.time.temporal.WeekFields.of(java.util.Locale.CHINA).dayOfWeek(), 1)
    val isThisWeek = weekMonday == todayMonday
    val todayDay = today.dayOfWeek.value.let { if (it == 7) 7 else it }
    return (0..6).map { offset ->
        val day = weekMonday.plusDays(offset.toLong())
        WeekDay(
            dayOfWeek = offset + 1,
            label = labels[offset],
            date = day.format(java.time.format.DateTimeFormatter.ofPattern("d")),
            isToday = isThisWeek && (offset + 1) == todayDay,
        )
    }
}
