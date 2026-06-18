package com.cstimetable.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstimetable.ui.theme.*

/**
 * 星期导航条
 *
 * 横向排列周一至周日，显示星期名 + 日期数字
 * 支持滑动和点击切换，当天高亮
 */
@Composable
fun WeekSelector(
    currentDay: Int,           // 当前选中的星期 (1-7)
    weekDays: List<WeekDay>,   // 星期数据列表
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(BackgroundGradientStart, BackgroundGradientEnd)
                )
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(weekDays) { weekDay ->
            WeekDayItem(
                weekDay = weekDay,
                isSelected = weekDay.dayOfWeek == currentDay,
                onClick = { onDaySelected(weekDay.dayOfWeek) }
            )
        }
    }
}

data class WeekDay(
    val dayOfWeek: Int,        // 1=周一, 7=周日
    val label: String,         // "一", "二", ...
    val date: String,          // "13", "14", ...
    val isToday: Boolean = false,
)

@Composable
private fun WeekDayItem(
    weekDay: WeekDay,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val textColor = when {
        isSelected -> WeekSelectedText
        weekDay.isToday -> WeekTodayIndicator
        else -> WeekUnselectedText
    }
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        // 星期标签
        Text(
            text = weekDay.label,
            fontSize = 12.sp,
            fontWeight = fontWeight,
            color = textColor,
        )
        Spacer(modifier = Modifier.height(2.dp))
        // 日期数字
        Text(
            text = weekDay.date,
            fontSize = 12.sp,
            fontWeight = fontWeight,
            color = textColor,
        )
        // 选中指示条
        if (isSelected) {
            Spacer(modifier = Modifier.height(1.dp))
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(WeekTodayIndicator)
            )
        }
    }
}
