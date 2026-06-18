package com.cstimetable.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    // 标题 - 周次
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    // 日期
    titleSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = TimeTextColor,
    ),
    // 星期标签
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    // 节次编号
    labelSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
    // 课程名 (卡片内)
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
    // 地点/教师 (卡片内)
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 12.sp,
    ),
    // 底部导航
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)
