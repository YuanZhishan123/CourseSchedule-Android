package com.cstimetable.model

import java.util.UUID

/**
 * 课程数据类
 */
data class Course(
    val id: String = UUID.randomUUID().toString(),
    val name: String,           // 课程名称
    val day: Int,               // 星期几 (1=周一, 7=周日)
    val startPeriod: Int,       // 开始节次
    val endPeriod: Int,         // 结束节次
    val weeks: List<Int>,       // 上课周次列表
    val location: String,       // 上课地点 "@翔宇楼206"
    val teacher: String,        // 教师姓名
    val color: Long,            // 卡片颜色 (ARGB)
    val detail: String = "",    // 详情 (课程编号、学分等)
    val overlappingCourseIds: List<String> = emptyList(),  // 冲突的课程 ID
)

/**
 * 时间段
 */
data class TimeSlot(
    val period: Int,            // 第几节 (1-11)
    val startTime: String,      // 开始时间 "08:00"
    val endTime: String,        // 结束时间 "08:45"
)

/**
 * 完整课表
 */
data class Schedule(
    val semester: String,                       // 学期 "2025-2026学年第2学期"
    val studentName: String,                    // 学生姓名
    val studentId: String,                      // 学号
    val timeSlots: List<TimeSlot>,              // 时间段定义
    val courses: List<Course>,                  // 课程列表
    val currentWeek: Int = 1,                   // 当前显示周次
    val totalWeeks: Int = 20,                   // 总周数
    val firstWeekStartDate: String? = null,     // 第一周周一日期 "yyyy-MM-dd"，null则用当前日期推算
)
