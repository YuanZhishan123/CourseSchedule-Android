package com.cstimetable.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstimetable.model.TimeSlot
import com.cstimetable.ui.theme.TimeNumberColor
import com.cstimetable.ui.theme.TimeTextColor

/**
 * 左侧时间轴
 *
 * 垂直显示第1~11节课的时间范围
 */
@Composable
fun TimeColumn(
    timeSlots: List<TimeSlot>,
    cellHeight: Int = 60, // dp
    modifier: Modifier = Modifier,
    columnWidth: Int = 48,
) {
    Column(
        modifier = modifier.width(columnWidth.dp),
    ) {
        // 顶部占位 (与星期选择器对齐)
        Spacer(modifier = Modifier.height(4.dp))

        timeSlots.forEach { slot ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cellHeight.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 节次编号
                    Text(
                        text = "${slot.period}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TimeNumberColor,
                    )
                    // 开始时间
                    Text(
                        text = slot.startTime,
                        fontSize = 9.sp,
                        color = TimeTextColor,
                        lineHeight = 12.sp,
                    )
                    // 结束时间
                    Text(
                        text = slot.endTime,
                        fontSize = 9.sp,
                        color = TimeTextColor,
                        lineHeight = 12.sp,
                    )
                }
            }
        }
    }
}
