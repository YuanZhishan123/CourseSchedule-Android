package com.cstimetable.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstimetable.model.Course
import com.cstimetable.ui.theme.CourseCardText

/**
 * 课程卡片
 *
 * 彩色圆角矩形，白色文字，显示课程名、地点、教师
 */
@Composable
fun CourseCard(
    course: Course,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentWeek: Int = 0,
    allCourses: List<Course> = emptyList(),
) {
    val bgColor = Color(course.color)

    // 仅当前周有实际重叠时才显示标记
    val hasActiveOverlap = currentWeek > 0 && course.overlappingCourseIds.any { oid ->
        allCourses.any { it.id == oid && currentWeek in it.weeks }
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        if (hasActiveOverlap) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xCCE53935))  // 半透明红色
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "重叠",
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            // 课程名称
            Text(
                text = course.name
                    .replace("[实验]", "")
                    .replace("[考试]", "")
                    .trim(),
                color = CourseCardText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 4,
                overflow = TextOverflow.Clip,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // 地点
            if (course.location.isNotEmpty()) {
                Text(
                    text = course.location,
                    color = CourseCardText.copy(alpha = 0.9f),
                    fontSize = 8.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 10.sp,
                )
            }

            // 教师
            if (course.teacher.isNotEmpty() && course.teacher != "未知") {
                Text(
                    text = course.teacher,
                    color = CourseCardText.copy(alpha = 0.85f),
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 10.sp,
                )
            }
        }
    }
}
