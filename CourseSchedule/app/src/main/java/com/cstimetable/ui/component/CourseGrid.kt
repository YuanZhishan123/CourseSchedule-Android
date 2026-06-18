package com.cstimetable.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstimetable.model.Course
import com.cstimetable.model.TimeSlot
import com.cstimetable.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CourseGrid(
    courses: List<Course>,
    timeSlots: List<TimeSlot>,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
    currentWeek: Int = 0,
    allCourses: List<Course> = emptyList(),
    weekDays: List<WeekDay> = emptyList(),
    currentDay: Int = 0,
    onAddCourseRequest: ((day: Int, startPeriod: Int) -> Unit)? = null,
) {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp
    val timeColumnWidth = 40
    val cellWidth = (screenWidth - timeColumnWidth) / 7
    val cellHeight = 56
    val totalRows = timeSlots.size
    val totalWidth = timeColumnWidth + cellWidth * 7
    val verticalScroll = rememberScrollState()

    // 加号浮层目标 (day, period)
    var addOverlayTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(verticalScroll)
    ) {
        // 表头行
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 1.dp)
        ) {
            Box(Modifier.width(timeColumnWidth.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("W${currentWeek}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Purple40, textAlign = TextAlign.Center)
            }
            if (weekDays.isNotEmpty()) {
                weekDays.forEach { day ->
                    Box(Modifier.width(cellWidth.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day.label, fontSize = 11.sp,
                                fontWeight = if (day.dayOfWeek == currentDay) FontWeight.Bold else FontWeight.Normal,
                                color = when { day.isToday -> WeekTodayIndicator; day.dayOfWeek == currentDay -> WeekSelectedText; else -> WeekUnselectedText })
                            Text(day.date, fontSize = 10.sp,
                                fontWeight = if (day.dayOfWeek == currentDay) FontWeight.Bold else FontWeight.Normal,
                                color = when { day.isToday -> WeekTodayIndicator; day.dayOfWeek == currentDay -> WeekSelectedText; else -> WeekUnselectedText })
                        }
                    }
                }
            } else {
                listOf("一","二","三","四","五","六","日").forEach { label ->
                    Box(Modifier.width(cellWidth.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TimeTextColor)
                    }
                }
            }
        }

        // 网格主体
        Box(
            modifier = Modifier.width(totalWidth.dp).height((totalRows * cellHeight).dp)
        ) {
            GridLines(rows = totalRows, cols = 7, cellWidth = cellWidth, cellHeight = cellHeight, timeColumnWidth = timeColumnWidth)
            TimeColumn(timeSlots = timeSlots, cellHeight = cellHeight, modifier = Modifier.align(Alignment.TopStart), columnWidth = timeColumnWidth)

            // 课程卡片
            courses.forEach { course ->
                val x = timeColumnWidth + (course.day - 1) * cellWidth
                val y = (course.startPeriod - 1) * cellHeight
                val h = (course.endPeriod - course.startPeriod + 1) * cellHeight
                CourseCard(course = course, onClick = {
                    addOverlayTarget = null  // 点击课程卡片时清除加号
                    onCourseClick(course)
                },
                    modifier = Modifier.offset(x = x.dp, y = y.dp).width((cellWidth - 2).dp).height((h - 2).dp).padding(0.5.dp),
                    currentWeek = currentWeek, allCourses = allCourses)
            }

            // 空单元格长按检测（仅当回调已提供时）
            if (onAddCourseRequest != null) {
                for (day in 1..7) {
                    for (period in 1..totalRows) {
                        val isOccupied = courses.any { it.day == day && period in it.startPeriod..it.endPeriod }
                        if (!isOccupied) {
                            val px = timeColumnWidth + (day - 1) * cellWidth
                            val py = (period - 1) * cellHeight
                            Box(
                                modifier = Modifier
                                    .offset(x = px.dp, y = py.dp)
                                    .size(cellWidth.dp, cellHeight.dp)
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { /* 单击空单元格：清除加号 */ addOverlayTarget = null },
                                        onLongClick = {
                                            if (addOverlayTarget == day to period) addOverlayTarget = null
                                            else addOverlayTarget = day to period
                                        }
                                    )
                            )
                        }
                    }
                }

                // 透明遮罩：点击空白处关闭加号（必须在 FAB 之下，FAB 才能响应点击）
                if (addOverlayTarget != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { addOverlayTarget = null }
                    )
                }

                // 加号浮层（在遮罩之上）
                addOverlayTarget?.let { (day, period) ->
                    val fabX = timeColumnWidth + (day - 1) * cellWidth + cellWidth / 2 - 18
                    val fabY = (period - 1) * cellHeight + cellHeight / 2 - 18
                    FloatingActionButton(
                        onClick = {
                            onAddCourseRequest(day, period)
                            addOverlayTarget = null
                        },
                        modifier = Modifier
                            .offset(x = fabX.dp, y = fabY.dp)
                            .size(36.dp),
                        containerColor = Purple40,
                        contentColor = Color.White,
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Filled.Add, "添加课程", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GridLines(rows: Int, cols: Int, cellWidth: Int, cellHeight: Int, timeColumnWidth: Int) {
    Box(modifier = Modifier.fillMaxSize().drawWithCache {
        val lineColor = DividerColor; val sw = 0.5f * density
        onDrawBehind {
            for (i in 0..rows) { val y = i * cellHeight * density; drawLine(lineColor, Offset(timeColumnWidth * density, y), Offset(size.width, y), sw) }
            for (i in 0..cols) { val x = (timeColumnWidth + i * cellWidth) * density; drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), sw) }
        }
    })
}
