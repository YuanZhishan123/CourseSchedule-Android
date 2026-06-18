package com.cstimetable.parser

/**
 * PDF 字形 — 从 PDF 文本流中提取的单个字符/词片段
 */
data class Glyph(
    val text: String,
    val x: Float,          // 水平位置 (从左到右)
    val y: Float,          // 垂直位置 (从上到下，0=页面顶部)
    val page: Int,         // 页码 (0-based)
    val fontSize: Float,   // 字号 (pt)
)

/**
 * 星期行定义 — 表格中每一天对应的 Y 坐标范围
 */
data class DayRow(
    val day: Int,          // 1=周一, 7=周日
    val yTop: Float,       // 该行顶部 Y
    val yBottom: Float,    // 该行底部 Y
    val page: Int,
)

/**
 * 解析出的课程中间表示
 */
data class ParsedCourse(
    val name: String,
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: List<Int>,
    val location: String,
    val teacher: String,
)

/**
 * 单元格内容 — 一个 (day, period) 格子里聚合后的文本
 */
data class CellContent(
    val day: Int,
    val startPeriod: Int,  // 由所在列推导
    val text: String,      // 合并后的完整文本
    val maxFontSize: Float, // 格内最大字号（用于识别课程名）
    val page: Int = 0,     // 页码（用于区分不同页面的内容）
)

/**
 * 课程块的中间表示 — 一坨连续的字形集合
 */
data class TextBlock(
    val page: Int,
    val x: Float,
    val y: Float,
    val lines: List<String>,  // 按 Y 排序的文本行
    val maxFontSize: Float,
)

/**
 * 表格布局 — 检测器输出
 */
data class TableLayout(
    val dayRows: List<DayRow>,
    val periodCols: Map<Int, Float>,  // period → x
)
