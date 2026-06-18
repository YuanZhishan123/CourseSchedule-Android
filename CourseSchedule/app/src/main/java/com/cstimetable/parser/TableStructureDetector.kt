package com.cstimetable.parser

import android.util.Log

/**
 * 表格结构检测器
 *
 * PdfBox 输出的布局：DAYS 为列（X 轴），PERIODS 为行（Y 轴）。
 *
 * 第一步：合并相邻字为词
 * 第二步：从词中检测星期标签 → 得到 Day 列的 X 边界
 * 第三步：从词中检测节次编号 → 得到 Period 行的 Y 边界
 * 第四步：输出合并后的词 + 网格布局
 */
class TableStructureDetector {

    private val dayCharMap = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '日' to 7,
    )
    private val TAG = "CSTimetable"

    data class DetectResult(
        val words: List<Glyph>,       // 合并后的词
        val dayCols: List<DayCol>,    // Day 列定义
        val periodRows: List<PeriodRow>, // Period 行定义
    )

    data class DayCol(val day: Int, val xMin: Float, val xMax: Float)
    data class PeriodRow(val period: Int, val yMin: Float, val yMax: Float)

    fun detect(glyphs: List<Glyph>): DetectResult {
        val words = mergeAdjacentGlyphs(glyphs)
        val dayCols = detectDayColumns(words)
        val periodRows = detectPeriodRows(words)
        Log.d(TAG, "Layout: ${dayCols.size} dayCols, ${periodRows.size} periodRows, ${words.size} words")
        return DetectResult(words, dayCols, periodRows)
    }

    private fun mergeAdjacentGlyphs(glyphs: List<Glyph>): List<Glyph> {
        val sorted = glyphs.sortedWith(compareBy({ it.page }, { it.y }, { it.x }))
        val merged = mutableListOf<Glyph>()
        var cur = mutableListOf(sorted[0])
        var curY = sorted[0].y
        var lastX = sorted[0].x

        for (i in 1 until sorted.size) {
            val g = sorted[i]
            val xGap = g.x - lastX
            val yDiff = kotlin.math.abs(g.y - curY)
            if (g.page == cur.last().page && yDiff < 6f && xGap <= 15f && xGap > 0f) {
                cur.add(g)
                curY = cur.map { it.y }.average().toFloat()
            } else {
                merged.add(buildWord(cur))
                cur = mutableListOf(g); curY = g.y
            }
            lastX = g.x
        }
        if (cur.isNotEmpty()) merged.add(buildWord(cur))
        return merged
    }

    private fun buildWord(glyphs: List<Glyph>): Glyph {
        val s = glyphs.sortedBy { it.x }
        return Glyph(text = s.joinToString("") { it.text }, x = s.first().x,
            y = glyphs.map { it.y }.average().toFloat(),
            page = glyphs.first().page, fontSize = glyphs.maxOf { it.fontSize })
    }

    /**
     * 从"星期X"标签检测 Day 列边界
     */
    private fun detectDayColumns(words: List<Glyph>): List<DayCol> {
        val labels = mutableListOf<Pair<Int, Float>>() // (dayNum, x)
        for (w in words) {
            if (w.text.length in 3..4 && w.text.startsWith("星期")) {
                val d = dayCharMap[w.text[2]] ?: continue
                labels.add(d to w.x)
            }
        }
        if (labels.isEmpty()) return emptyList()

        labels.sortBy { it.second } // 按 X 排序

        // 计算列边界：两列标签的中间点
        val result = mutableListOf<DayCol>()
        for (i in labels.indices) {
            val (day, cx) = labels[i]
            val leftEdge = if (i == 0) cx - 60f else (labels[i-1].second + cx) / 2f
            val rightEdge = if (i == labels.lastIndex) cx + 60f else (cx + labels[i+1].second) / 2f
            result.add(DayCol(day, leftEdge, rightEdge))
        }
        return result
    }

    /**
     * 从孤立的节次数字检测 Period 行边界
     */
    private fun detectPeriodRows(words: List<Glyph>): List<PeriodRow> {
        // 找孤立的数字"1"-"12"，位于Day列区域内
        val periods = mutableListOf<Pair<Int, Float>>()
        for (w in words) {
            val n = w.text.trim().toIntOrNull() ?: continue
            if (n !in 1..12) continue
            // 必须是孤立的数字（不是长文本的一部分）
            if (w.text.trim().length > 2) continue
            // 必须在 Day 1 列附近（x < 200）
            if (w.x > 200) continue
            periods.add(n to w.y)
        }
        if (periods.isEmpty()) return emptyList()

        periods.sortBy { it.second } // 按 Y 排序

        // 计算行边界
        val result = mutableListOf<PeriodRow>()
        for (i in periods.indices) {
            val (p, cy) = periods[i]
            val topEdge = if (i == 0) cy - 20f else (periods[i-1].second + cy) / 2f
            val bottomEdge = if (i == periods.lastIndex) cy + 30f else (cy + periods[i+1].second) / 2f
            result.add(PeriodRow(p, topEdge, bottomEdge))
        }
        return result
    }
}
