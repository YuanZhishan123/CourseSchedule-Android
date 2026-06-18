package com.cstimetable.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.viewpager2.widget.ViewPager2
import com.cstimetable.model.Course
import com.cstimetable.ui.component.WeekDay
import com.cstimetable.ui.component.WeekPagerAdapter
import com.cstimetable.ui.theme.*
import com.cstimetable.viewmodel.ScheduleViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val totalWeeks = state.schedule?.totalWeeks ?: 20

    var showClearDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 文件选择器
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val name = it.lastPathSegment ?: ""
            when {
                name.endsWith(".json") -> viewModel.importJson(it)
                else -> viewModel.importPdf(it)
            }
        }
    }

    var selectedCourse by remember { mutableStateOf<Course?>(null) }

    // 删除 / 添加 对话框状态
    var showDeleteConfirm by remember { mutableStateOf<DeleteConfirm?>(null) }
    var showAddForm by remember { mutableStateOf<AddFormTarget?>(null) }

    val firstWeekMonday = remember(state.firstWeekStartDate) {
        val dateStr = state.firstWeekStartDate
        if (dateStr != null) {
            try { LocalDate.parse(dateStr) } catch (_: Exception) { null }
        } else null
    }

    val weekDays = remember(state.currentWeek, state.firstWeekStartDate) {
        generateWeekDays(state.currentWeek, firstWeekMonday)
    }

    // 导入后自动弹出日期选择
    LaunchedEffect(state.pendingDateSelection) {
        if (state.pendingDateSelection) showDatePicker = true
    }

    // 清除确认弹窗
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除课表") },
            text = { Text("确定要清除当前课表数据吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearSchedule()
                }) { Text("确定", color = Color(0xFFE53935)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }

    // 日期选择弹窗
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = firstWeekMonday?.atStartOfDay(
                java.time.ZoneOffset.UTC
            )?.toInstant()?.toEpochMilli()
                ?: System.currentTimeMillis(),
            initialDisplayMode = DisplayMode.Picker,
        )
        DatePickerDialog(
            onDismissRequest = {
                showDatePicker = false
                viewModel.dismissDateSelection()
            },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val instant = java.time.Instant.ofEpochMilli(millis)
                        val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        viewModel.setFirstWeekStart(date.year, date.monthValue, date.dayOfMonth)
                    }
                    showDatePicker = false
                    viewModel.dismissDateSelection()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    viewModel.dismissDateSelection()
                }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "第${state.currentWeek}周",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = formatDateForWeek(state.currentWeek, firstWeekMonday),
                            color = TimeTextColor,
                            fontSize = 11.sp,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.goToCurrentWeek() }) {
                        Text("本周", color = Purple40, fontSize = 13.sp)
                    }
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, "设置第一周日期", tint = Purple40)
                    }
                    if (state.hasData) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Filled.Delete, "清除课表", tint = Color(0xFFE53935).copy(alpha = 0.7f))
                        }
                    }
                    IconButton(onClick = { fileLauncher.launch(arrayOf("application/pdf", "application/json")) }) {
                        Icon(Icons.Filled.FileOpen, "导入课表", tint = Purple40)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(BackgroundGradientStart, BackgroundGradientEnd)
                )
            ),
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            val today = LocalDate.now()
            val todayDayOfWeek = today.dayOfWeek.value.let { if (it == 7) 7 else it }

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Purple40)
                }
            } else if (state.errorMessage != null) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = state.errorMessage ?: "", color = Color(0xFFE53935))
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.clearError() }) { Text("关闭") }
                    }
                }
            } else if (state.hasData) {
                val schedule = state.schedule!!
                val adapter = remember {
                    WeekPagerAdapter(
                        viewModel,
                        todayDayOfWeek,
                        onCourseClick = { course -> selectedCourse = course },
                        onAddCourseRequest = { day, period ->
                            showAddForm = AddFormTarget(day, period)
                        }
                    )
                }

                // 保持 adapter 数据同步
                LaunchedEffect(schedule) {
                    adapter.schedule = schedule
                    adapter.notifyDataSetChanged()
                }
                // 切周时只更新 currentWeek 引用，不触发全量刷新
                LaunchedEffect(state.currentWeek) {
                    adapter.currentWeek = state.currentWeek
                }

                // ViewPager2 引用，用于同步"本周"按钮
                var pagerRef by remember { mutableStateOf<ViewPager2?>(null) }

                // 同步 ViewModel 周次变化 → ViewPager2（点"本周"按钮时）
                LaunchedEffect(state.currentWeek) {
                    pagerRef?.let { pager ->
                        val target = state.currentWeek - 1
                        if (pager.currentItem != target) {
                            pager.setCurrentItem(target, true)
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            var fakeDragActive = false
                            var lastX = 0f
                            ViewPager2(ctx).apply {
                                orientation = ViewPager2.ORIENTATION_HORIZONTAL
                                offscreenPageLimit = 1
                                this.adapter = adapter
                                adapter.currentWeek = state.currentWeek  // 必须在 setCurrentItem 之前同步
                                setCurrentItem(state.currentWeek - 1, false)
                                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                                    override fun onPageSelected(position: Int) {
                                        val week = position + 1
                                        if (week != viewModel.state.value.currentWeek) {
                                            viewModel.selectWeek(week)
                                        }
                                    }
                                })
                                // 通过 fakeDrag 放大拖拽，降低翻页阈值
                                setOnTouchListener { _, event ->
                                    when (event.action) {
                                        android.view.MotionEvent.ACTION_DOWN -> {
                                            if (!isFakeDragging) {
                                                fakeDragActive = true
                                                beginFakeDrag()
                                                lastX = event.x
                                            }
                                            false
                                        }
                                        android.view.MotionEvent.ACTION_MOVE -> {
                                            if (fakeDragActive && isFakeDragging) {
                                                val dx = lastX - event.x
                                                fakeDragBy(dx * 6.0f) // 放大 4 倍
                                                lastX = event.x
                                            }
                                            true
                                        }
                                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                            if (fakeDragActive && isFakeDragging) {
                                                fakeDragActive = false
                                                endFakeDrag()
                                            }
                                            false
                                        }
                                        else -> false
                                    }
                                }
                                pagerRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // 水印周次
                    Column(
                        Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "${state.currentWeek}",
                            color = Purple40.copy(alpha = 0.25f), fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "/$totalWeeks",
                            color = TimeTextColor.copy(alpha = 0.2f), fontSize = 16.sp,
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂无课表数据", color = TimeTextColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("点击右上角 ", color = TimeTextColor.copy(alpha = 0.7f), fontSize = 14.sp)
                            Icon(Icons.Filled.FileOpen, null, tint = Purple40, modifier = Modifier.size(20.dp))
                            Text(" 导入课表", color = TimeTextColor.copy(alpha = 0.7f), fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        selectedCourse?.let { course ->
            val allCourses = state.schedule?.courses ?: emptyList()
            val overlapCourses = allCourses.filter { it.id in course.overlappingCourseIds }
            AlertDialog(
                onDismissRequest = { selectedCourse = null },
                title = { Text(course.name, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        if (overlapCourses.isNotEmpty()) {
                            Text("⚠ 以下课程与当前课程时间冲突：", color = Color(0xFFE53935), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            overlapCourses.forEach { oc ->
                                val overlapWeeks = oc.weeks.filter { it in course.weeks }
                                Text("• ${oc.name}", color = Color(0xFFE53935), fontSize = 12.sp)
                                Text("  周${oc.day} ${oc.startPeriod}-${oc.endPeriod}节 | ${oc.location} | ${oc.teacher}", color = Color(0xFFE53935).copy(alpha = 0.8f), fontSize = 11.sp)
                                if (overlapWeeks.isNotEmpty()) {
                                    Text("  冲突周次: ${overlapWeeks.joinToString(",")}", color = Color(0xFFE53935).copy(alpha = 0.7f), fontSize = 10.sp)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = TimeTextColor.copy(alpha = 0.2f))
                            Spacer(Modifier.height(8.dp))
                        }
                        DetailRow("上课时间", "周${course.day} 第${course.startPeriod}-${course.endPeriod}节")
                        DetailRow("上课周次", course.weeks.joinToString(", ") { "${it}周" })
                        DetailRow("上课地点", course.location)
                        DetailRow("授课教师", course.teacher)
                        if (course.detail.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(course.detail, fontSize = 11.sp, color = TimeTextColor)
                        }
                    }
                },
                confirmButton = {
                    val canDeleteThisWeek = state.currentWeek in course.weeks
                    // 只有一周时两者等价，只显示"删除本周"；多周时两个都显示
                    val showDeleteAll = course.weeks.size > 1
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 仅删本周 (课程多周 + 当前周有课时才显示)
                        if (canDeleteThisWeek) {
                            TextButton(onClick = {
                                showDeleteConfirm = DeleteConfirm(
                                    courseId = course.id,
                                    courseName = course.name,
                                    currentWeek = state.currentWeek,
                                    mode = DeleteMode.DELETE_WEEK,
                                )
                            }) {
                                Text("删除本周", color = Color(0xFFE53935), fontSize = 13.sp)
                            }
                        }
                        // 删所有周（仅课程有多个周时显示）
                        if (showDeleteAll) {
                            TextButton(onClick = {
                                showDeleteConfirm = DeleteConfirm(
                                    courseId = course.id,
                                    courseName = course.name,
                                    currentWeek = state.currentWeek,
                                    mode = DeleteMode.DELETE_ALL,
                                )
                            }) {
                                Text("删除所有周", color = Color(0xFFE53935), fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { selectedCourse = null }) {
                            Text("关闭", color = Purple40, fontSize = 13.sp)
                        }
                    }
                },
            )
        }

        // ── 删除确认弹窗 ──
        showDeleteConfirm?.let { confirm ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text("确认删除") },
                text = {
                    val desc = when (confirm.mode) {
                        DeleteMode.DELETE_WEEK -> "第${confirm.currentWeek}周的课程"
                        DeleteMode.DELETE_ALL -> "所有周的课程"
                    }
                    Text("确定要删除「${confirm.courseName}」的${desc}吗？\n此操作不可恢复。")
                },
                confirmButton = {
                    TextButton(onClick = {
                        when (confirm.mode) {
                            DeleteMode.DELETE_WEEK ->
                                viewModel.deleteCourseFromWeek(confirm.courseId, confirm.currentWeek)
                            DeleteMode.DELETE_ALL ->
                                viewModel.deleteCourseCompletely(confirm.courseId)
                        }
                        selectedCourse = null
                        showDeleteConfirm = null
                    }) {
                        Text("确定删除", color = Color(0xFFE53935))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
                },
            )
        }

        // ── 添加课程表单弹窗 ──
        showAddForm?.let { target ->
            var name by remember { mutableStateOf("") }
            var location by remember { mutableStateOf("") }
            var teacher by remember { mutableStateOf("") }
            var startPeriodText by remember { mutableStateOf(target.startPeriod.toString()) }
            var periodCountText by remember { mutableStateOf("1") }
            val totalWeeks = state.schedule?.totalWeeks ?: 20
            var selectedWeeks by remember { mutableStateOf(setOf(state.currentWeek)) }
            val dayLabels = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

            AlertDialog(
                onDismissRequest = { showAddForm = null },
                title = { Text("添加课程") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("星期: ${dayLabels[target.day]}", fontWeight = FontWeight.Medium, fontSize = 14.sp)

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("课程名称 (可选)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("上课地点 (可选)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = teacher,
                            onValueChange = { teacher = it },
                            label = { Text("授课教师 (可选)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startPeriodText,
                                onValueChange = { startPeriodText = it },
                                label = { Text("开始节次") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            OutlinedTextField(
                                value = periodCountText,
                                onValueChange = { periodCountText = it },
                                label = { Text("持续节数") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }

                        // 周次点选
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("上课周次:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { selectedWeeks = (1..totalWeeks).toSet() },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) { Text("全选", fontSize = 12.sp, color = Purple40) }
                            TextButton(onClick = { selectedWeeks = emptySet() },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) { Text("全不选", fontSize = 12.sp, color = Purple40) }
                        }
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (w in 1..totalWeeks) {
                                val selected = w in selectedWeeks
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            color = if (selected) Purple40 else Purple40.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(6.dp),
                                        )
                                        .clickable {
                                            selectedWeeks = if (selected) selectedWeeks - w else selectedWeeks + w
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$w",
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Color.White else Purple40,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val startPeriod = startPeriodText.toIntOrNull()?.coerceIn(1, 12) ?: target.startPeriod
                        val periodCount = periodCountText.toIntOrNull()?.coerceIn(1, 12) ?: 1
                        val endPeriod = (startPeriod + periodCount - 1).coerceAtMost(12)
                        val weeks = selectedWeeks.toList().sorted().ifEmpty { (1..totalWeeks).toList() }

                        val dayCourses = state.schedule?.courses?.filter { it.day == target.day } ?: emptyList()
                        val usedColors = dayCourses.map { it.color }.toSet()
                        val assignedColor = CourseColors.firstOrNull { it !in usedColors } ?: CourseColors.first()

                        viewModel.addCourse(Course(
                            name = name.trim().ifEmpty { "未命名课程" },
                            day = target.day,
                            startPeriod = startPeriod,
                            endPeriod = endPeriod,
                            weeks = weeks,
                            location = location.trim(),
                            teacher = teacher.trim(),
                            color = assignedColor,
                        ))
                        showAddForm = null
                    }) {
                        Text("添加", color = Purple40)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddForm = null }) { Text("取消") }
                },
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("$label: ", fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(value, fontSize = 14.sp, color = TimeTextColor)
    }
}

private fun generateWeekDays(weekNumber: Int, firstMonday: LocalDate?): List<WeekDay> {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val today = LocalDate.now()
    val weekMonday = if (firstMonday != null) {
        firstMonday.plusWeeks((weekNumber - 1).toLong())
    } else {
        today.with(WeekFields.of(Locale.CHINA).dayOfWeek(), 1)
    }
    val todayMonday = today.with(WeekFields.of(Locale.CHINA).dayOfWeek(), 1)
    val isThisWeek = weekMonday == todayMonday
    val todayDay = today.dayOfWeek.value.let { if (it == 7) 7 else it }
    return (0..6).map { offset ->
        val day = weekMonday.plusDays(offset.toLong())
        WeekDay(
            dayOfWeek = offset + 1,
            label = labels[offset],
            date = day.format(DateTimeFormatter.ofPattern("d")),
            isToday = isThisWeek && (offset + 1) == todayDay,
        )
    }
}

private fun formatDateForWeek(weekNumber: Int, firstMonday: LocalDate?): String {
    if (firstMonday == null) return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/M/d"))
    val weekMonday = firstMonday.plusWeeks((weekNumber - 1).toLong())
    val weekSunday = weekMonday.plusDays(6)
    val fmt = DateTimeFormatter.ofPattern("M/d")
    return "${weekMonday.format(fmt)} - ${weekSunday.format(fmt)}"
}

// ── 对话框辅助类型 ──

private data class DeleteConfirm(
    val courseId: String,
    val courseName: String,
    val currentWeek: Int,
    val mode: DeleteMode,
)

private enum class DeleteMode { DELETE_WEEK, DELETE_ALL }

private data class AddFormTarget(
    val day: Int,
    val startPeriod: Int,
)
