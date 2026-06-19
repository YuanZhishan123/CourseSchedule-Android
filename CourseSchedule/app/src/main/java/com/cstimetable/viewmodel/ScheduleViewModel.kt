package com.cstimetable.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cstimetable.model.Course
import com.cstimetable.model.Schedule
import com.cstimetable.model.TimeSlot
import com.cstimetable.parser.PdfParser
import com.cstimetable.notify.CourseAlarmReceiver
import com.cstimetable.widget.CourseWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 持久化文件名 */
private const val DATA_FILE = "schedule_data.json"

/**
 * 课程表状态
 */
data class ScheduleState(
    val schedule: Schedule? = null,
    val currentWeek: Int = 1,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasData: Boolean = false,
    val firstWeekStartDate: String? = null,  // 第一周周一 "yyyy-MM-dd"
    val pendingDateSelection: Boolean = false,  // 导入后待选日期
)

/**
 * 课程表 ViewModel
 */
class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ScheduleState())
    val state: StateFlow<ScheduleState> = _state.asStateFlow()

    private val dataFile = File(application.filesDir, DATA_FILE)

    init {
        loadFromFile()
    }

    /** 当前周课程 */
    fun getCurrentWeekCourses(): List<Course> {
        val s = _state.value.schedule ?: return emptyList()
        val week = _state.value.currentWeek
        return s.courses.filter { week in it.weeks }
    }

    /** 切换周次 */
    fun selectWeek(week: Int) {
        _state.update { it.copy(currentWeek = week) }
    }

    /** 回到当前实际周（根据第一周起始日期计算） */
    fun goToCurrentWeek() {
        val firstDateStr = _state.value.firstWeekStartDate
        val week = if (firstDateStr != null) {
            try {
                val firstMonday = java.time.LocalDate.parse(firstDateStr)
                val today = java.time.LocalDate.now()
                val daysDiff = today.toEpochDay() - firstMonday.toEpochDay()
                val w = (daysDiff / 7).toInt() + 1
                if (w >= 1) w.coerceIn(1, _state.value.schedule?.totalWeeks ?: 20) else 1
            } catch (_: Exception) {
                _state.value.currentWeek  // 解析失败则保持不动
            }
        } else {
            _state.value.currentWeek
        }
        _state.update { it.copy(currentWeek = week) }
    }

    /** 设置第一周开始日期 */
    fun setFirstWeekStart(year: Int, month: Int, day: Int) {
        val dateStr = "%04d-%02d-%02d".format(year, month, day)
        _state.update { it.copy(firstWeekStartDate = dateStr) }
        saveToFile()
        CourseWidgetProvider.updateAllWidgets(getApplication())
    }

    /** 清除课表数据 */
    fun clearSchedule() {
        _state.update { ScheduleState() }
        dataFile.delete()
        CourseWidgetProvider.updateAllWidgets(getApplication())
    }

    /** 导入 PDF 课表 */
    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            val result = PdfParser.parse(getApplication(), uri)

            result.fold(
                onSuccess = { schedule ->
                    _state.update {
                        it.copy(
                            schedule = schedule,
                            isLoading = false,
                            hasData = true,
                            currentWeek = schedule.currentWeek,
                            pendingDateSelection = true,
                        )
                    }
                    saveToFile()
                    CourseWidgetProvider.updateAllWidgets(getApplication())
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "解析失败",
                        )
                    }
                }
            )
        }
    }

    /** 导入 JSON 课表 */
    fun importJson(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val jsonStr = getApplication<android.app.Application>()
                    .contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText()
                    ?: throw Exception("无法读取文件")

                val json = JSONObject(jsonStr)
                val courses = mutableListOf<Course>()
                val jsonCourses = json.getJSONArray("courses")
                for (i in 0 until jsonCourses.length()) {
                    val c = jsonCourses.getJSONObject(i)
                    courses.add(Course(
                        name = c.getString("name"),
                        day = c.getInt("day"),
                        startPeriod = c.getInt("startPeriod"),
                        endPeriod = c.getInt("endPeriod"),
                        weeks = c.getJSONArray("weeks").let { arr ->
                            (0 until arr.length()).map { arr.getInt(it) }
                        },
                        location = c.optString("location", "未知地点"),
                        teacher = c.optString("teacher", "未知"),
                        color = c.optLong("color", 0xFFB4A7D6),
                        detail = c.optString("detail", ""),
                    ))
                }

                val schedule = Schedule(
                    semester = json.optString("semester", "未知学期"),
                    studentName = json.optString("studentName", "未知"),
                    studentId = json.optString("studentId", ""),
                    timeSlots = listOf(
                        TimeSlot(1, "08:00", "08:45"), TimeSlot(2, "08:50", "09:35"),
                        TimeSlot(3, "09:55", "10:40"), TimeSlot(4, "10:45", "11:30"),
                        TimeSlot(5, "11:35", "12:20"), TimeSlot(6, "13:30", "14:15"),
                        TimeSlot(7, "14:20", "15:05"), TimeSlot(8, "15:20", "16:05"),
                        TimeSlot(9, "16:10", "16:55"), TimeSlot(10, "18:00", "18:45"),
                        TimeSlot(11, "18:50", "19:35"), TimeSlot(12, "19:40", "20:25"),
                    ),
                    courses = courses,
                    currentWeek = 1,
                    totalWeeks = json.optInt("totalWeeks", 20),
                )

                _state.update {
                    it.copy(schedule = schedule, isLoading = false, hasData = true, currentWeek = 1, pendingDateSelection = true)
                }
                saveToFile()
                CourseWidgetProvider.updateAllWidgets(getApplication())
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = "JSON 解析失败: ${e.message}")
                }
            }
        }
    }

    /** 清除错误消息 */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** 关闭导入后日期选择 */
    fun dismissDateSelection() {
        _state.update { it.copy(pendingDateSelection = false) }
    }

    // ── 课程增删 ────────────────────────────────────────

    /**
     * 添加课程 → 重建重叠关系 → 持久化 → 刷新 widget
     */
    fun addCourse(course: Course) {
        _state.update { state ->
            val schedule = state.schedule ?: return@update state
            val updated = schedule.courses + course
            state.copy(schedule = schedule.copy(courses = recalculateOverlaps(updated)), hasData = true)
        }
        saveToFile()
        CourseWidgetProvider.updateAllWidgets(getApplication())
    }

    /**
     * 完全删除课程 → 重建重叠关系 → 持久化 → 刷新 widget
     */
    fun deleteCourseCompletely(courseId: String) {
        _state.update { state ->
            val schedule = state.schedule ?: return@update state
            val updated = schedule.courses.filter { it.id != courseId }
            state.copy(schedule = schedule.copy(courses = recalculateOverlaps(updated)), hasData = updated.isNotEmpty())
        }
        saveToFile()
        CourseWidgetProvider.updateAllWidgets(getApplication())
    }

    /**
     * 仅从某周删除 → 若 weeks 变空则移除整门课 → 重建重叠 → 持久化 → 刷新 widget
     */
    fun deleteCourseFromWeek(courseId: String, week: Int) {
        _state.update { state ->
            val schedule = state.schedule ?: return@update state
            val updated = schedule.courses.mapNotNull { course ->
                if (course.id != courseId) course
                else {
                    val newWeeks = course.weeks.filter { it != week }
                    if (newWeeks.isEmpty()) null else course.copy(weeks = newWeeks)
                }
            }
            state.copy(schedule = schedule.copy(courses = recalculateOverlaps(updated)), hasData = updated.isNotEmpty())
        }
        saveToFile()
        CourseWidgetProvider.updateAllWidgets(getApplication())
    }

    /**
     * 重新计算所有课程的 overlappingCourseIds
     * 重叠条件：同天 + 时间范围相交 + 有共同周次
     */
    private fun recalculateOverlaps(courses: List<Course>): List<Course> {
        return courses.map { course ->
            val overlapping = courses.filter { other ->
                other.id != course.id &&
                other.day == course.day &&
                other.startPeriod <= course.endPeriod &&
                other.endPeriod >= course.startPeriod &&
                other.weeks.any { it in course.weeks }
            }.map { it.id }
            course.copy(overlappingCourseIds = overlapping)
        }
    }

    // ── 持久化 ────────────────────────────────────────

    /** 序列化当前状态到本地 JSON 文件 */
    private fun saveToFile() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = _state.value.schedule ?: return@launch
                val root = JSONObject()
                root.put("firstWeekStartDate", _state.value.firstWeekStartDate ?: "")
                root.put("currentWeek", _state.value.currentWeek)

                val scheduleJson = JSONObject()
                scheduleJson.put("semester", s.semester)
                scheduleJson.put("studentName", s.studentName)
                scheduleJson.put("studentId", s.studentId)
                scheduleJson.put("currentWeek", s.currentWeek)
                scheduleJson.put("totalWeeks", s.totalWeeks)

                val timeSlotsArr = JSONArray()
                for (ts in s.timeSlots) {
                    val t = JSONObject()
                    t.put("period", ts.period)
                    t.put("start", ts.startTime)
                    t.put("end", ts.endTime)
                    timeSlotsArr.put(t)
                }
                scheduleJson.put("timeSlots", timeSlotsArr)

                val coursesArr = JSONArray()
                for (c in s.courses) {
                    val co = JSONObject()
                    co.put("id", c.id)
                    co.put("name", c.name)
                    co.put("day", c.day)
                    co.put("startPeriod", c.startPeriod)
                    co.put("endPeriod", c.endPeriod)
                    co.put("weeks", JSONArray(c.weeks))
                    co.put("location", c.location)
                    co.put("teacher", c.teacher)
                    co.put("color", c.color)
                    co.put("detail", c.detail)
                    val overlapArr = JSONArray()
                    for (oid in c.overlappingCourseIds) overlapArr.put(oid)
                    co.put("overlappingCourseIds", overlapArr)
                    coursesArr.put(co)
                }
                scheduleJson.put("courses", coursesArr)

                root.put("schedule", scheduleJson)
                dataFile.writeText(root.toString())
                // 课程数据变更 → 重新调度通知闹钟
                CourseAlarmReceiver.schedule(getApplication())
            } catch (_: Exception) { }
        }
    }

    /** 从本地 JSON 文件恢复状态 */
    private fun loadFromFile() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!dataFile.exists()) return@launch
                val root = JSONObject(dataFile.readText())

                val firstWeekDate = root.optString("firstWeekStartDate", "").ifEmpty { null }
                val savedWeek = root.optInt("currentWeek", 1)

                val scheduleJson = root.getJSONObject("schedule")

                val timeSlotsArr = scheduleJson.getJSONArray("timeSlots")
                val timeSlots = (0 until timeSlotsArr.length()).map { i ->
                    val t = timeSlotsArr.getJSONObject(i)
                    TimeSlot(
                        period = t.getInt("period"),
                        startTime = t.getString("start"),
                        endTime = t.getString("end"),
                    )
                }

                val coursesArr = scheduleJson.getJSONArray("courses")
                val courses = (0 until coursesArr.length()).map { i ->
                    val co = coursesArr.getJSONObject(i)
                    Course(
                        name = co.getString("name"),
                        day = co.getInt("day"),
                        startPeriod = co.getInt("startPeriod"),
                        endPeriod = co.getInt("endPeriod"),
                        weeks = co.getJSONArray("weeks").let { arr ->
                            (0 until arr.length()).map { arr.getInt(it) }
                        },
                        location = co.optString("location", "未知地点"),
                        teacher = co.optString("teacher", "未知"),
                        color = co.optLong("color", 0xFFB4A7D6),
                        detail = co.optString("detail", ""),
                        id = co.optString("id", ""),
                        overlappingCourseIds = co.optJSONArray("overlappingCourseIds")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList(),
                    )
                }

                val schedule = Schedule(
                    semester = scheduleJson.optString("semester", "未知学期"),
                    studentName = scheduleJson.optString("studentName", "未知"),
                    studentId = scheduleJson.optString("studentId", ""),
                    timeSlots = timeSlots,
                    courses = courses,
                    currentWeek = scheduleJson.optInt("currentWeek", 1),
                    totalWeeks = scheduleJson.optInt("totalWeeks", 20),
                )

                // 根据第一周日期计算当前实际周
                val actualWeek = if (firstWeekDate != null) {
                    try {
                        val firstMonday = java.time.LocalDate.parse(firstWeekDate)
                        val today = java.time.LocalDate.now()
                        val daysDiff = today.toEpochDay() - firstMonday.toEpochDay()
                        val w = (daysDiff / 7).toInt() + 1
                        if (w >= 1) w.coerceIn(1, schedule.totalWeeks) else savedWeek
                    } catch (_: Exception) { savedWeek }
                } else savedWeek

                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            schedule = schedule,
                            hasData = true,
                            currentWeek = actualWeek,
                            firstWeekStartDate = firstWeekDate,
                        )
                    }
                }
            } catch (_: Exception) { }
        }
    }
}
