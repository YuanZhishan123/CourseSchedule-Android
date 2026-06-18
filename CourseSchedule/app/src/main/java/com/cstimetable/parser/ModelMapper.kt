package com.cstimetable.parser

import com.cstimetable.model.Course
import com.cstimetable.model.Schedule
import com.cstimetable.model.TimeSlot

/**
 * 模型映射器
 *
 * 将解析中间结果 ParsedCourse 列表转换为应用的 Schedule 数据模型。
 * 负责：去重、排序、颜色分配、时间段注入。
 */
class ModelMapper {

    /** 课程卡片颜色调色板 */
    private val COLOR_PALETTE = listOf(
        0xFFE8A0BF, 0xFFB4A7D6, 0xFF7EC8A0, 0xFF6CB4E4,
        0xFFF0B866, 0xFFE8836E, 0xFF98D8C8, 0xFFC9B1D5,
        0xFFF5A0A0, 0xFF87CEEB,
    )

    /** 默认时间段定义 */
    private val DEFAULT_TIME_SLOTS = listOf(
        TimeSlot(1, "08:00", "08:45"), TimeSlot(2, "08:50", "09:35"),
        TimeSlot(3, "09:55", "10:40"), TimeSlot(4, "10:45", "11:30"),
        TimeSlot(5, "11:35", "12:20"), TimeSlot(6, "13:30", "14:15"),
        TimeSlot(7, "14:20", "15:05"), TimeSlot(8, "15:20", "16:05"),
        TimeSlot(9, "16:10", "16:55"), TimeSlot(10, "18:00", "18:45"),
        TimeSlot(11, "18:50", "19:35"), TimeSlot(12, "19:40", "20:25"),
    )

    /**
     * 将解析结果映射为 Schedule
     */
    fun mapToSchedule(
        parsedCourses: List<ParsedCourse>,
        semester: String = "未知学期",
        studentName: String = "未知",
        studentId: String = "",
        totalWeeks: Int = 20,
    ): Schedule {
        // 去重：按 (名称, day, startPeriod) 去重
        val deduped = deduplicate(parsedCourses)

        // 转换为 Course 并加颜色
        val courses = deduped.mapIndexed { index, parsed ->
            Course(
                name = parsed.name,
                day = parsed.day,
                startPeriod = parsed.startPeriod,
                endPeriod = parsed.endPeriod,
                weeks = parsed.weeks,
                location = parsed.location,
                teacher = parsed.teacher,
                color = COLOR_PALETTE[index % COLOR_PALETTE.size],
                detail = "",
            )
        }.sortedWith(compareBy({ it.day }, { it.startPeriod }))

        // 检测重叠并标记
        val markedCourses = detectOverlaps(courses)

        return Schedule(
            semester = semester,
            studentName = studentName,
            studentId = studentId,
            timeSlots = DEFAULT_TIME_SLOTS,
            courses = markedCourses,
            currentWeek = 1,
            totalWeeks = totalWeeks,
        )
    }

    /**
     * 检测课程重叠：同一天、时段有交叉、周次有重合
     */
    private fun detectOverlaps(courses: List<Course>): List<Course> {
        return courses.map { course ->
            val overlapIds = courses.filter { other ->
                other.id != course.id &&
                other.day == course.day &&
                other.startPeriod <= course.endPeriod &&
                other.endPeriod >= course.startPeriod &&
                other.weeks.any { it in course.weeks }
            }.map { it.id }
            course.copy(overlappingCourseIds = overlapIds)
        }
    }

    /**
     * 去重：同一课程（同名称、同天、同开始节次）仅保留一条
     */
    private fun deduplicate(courses: List<ParsedCourse>): List<ParsedCourse> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<ParsedCourse>()

        for (c in courses) {
            val key = "${c.name}|${c.day}|${c.startPeriod}|${c.endPeriod}"
            if (key !in seen) {
                seen.add(key)
                result.add(c)
            }
        }

        return result
    }
}
