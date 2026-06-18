package com.cstimetable.parser

import android.util.Log

/**
 * 单元格分类器 v3 — 线性文本提取
 *
 * 不再依赖网格和单元格合并。对于每一天，收集该 Day 列内的全部词语，
 * 按 Y 排序，然后用正则检测课程名 + 详情块。
 */
class CellClassifier {
    private val TAG = "CSTimetable"

    fun classify(detectResult: TableStructureDetector.DetectResult): List<CellContent> {
        val words = detectResult.words
        val dayCols = detectResult.dayCols
        if (dayCols.isEmpty() || words.isEmpty()) return emptyList()

        Log.d(TAG, "Classifying: ${words.size} words, ${dayCols.size} dayCols")

        // 按 Day 列分组，然后按页分组
        val result = mutableListOf<CellContent>()

        for (col in dayCols) {
            val dayWords = words.filter { w ->
                w.x >= col.xMin && w.x < col.xMax &&
                w.x > 60f &&
                w.text.trim().isNotEmpty() &&
                w.text.trim().toIntOrNull() == null &&
                !w.text.startsWith("星期") &&
                w.text !in setOf("时间段", "节次", "上午", "下午", "晚上") &&
                !w.text.contains("打印时间") &&
                !w.text.startsWith("实践课程") && !w.text.startsWith("实验课程") &&
                !w.text.contains("集中实践") && !w.text.contains("讲课") &&
                w.text.length >= 2
            }

            // 按页分组
            val byPage = dayWords.groupBy { it.page }
            for ((page, pageWords) in byPage) {
                // 按 Y 排序
                val sorted = pageWords.sortedBy { it.y }

                // 合并相邻的 Y 接近的词语为行
                val lines = mergeToLines(sorted)

                // 从行中提取课程块：每遇到课程名开始新块，后续行属于该块
                val blocks = extractBlocks(lines)

                for (block in blocks) {
                    val name = block.name
                    val detail = block.detail
                    val sp = block.startPeriod
                    val ep = block.endPeriod
                    if (name.isNotEmpty() && sp > 0) {
                        result.add(CellContent(
                            day = col.day,
                            startPeriod = sp,
                            text = name + "\n" + detail,
                            maxFontSize = block.maxFontSize,
                            page = page,
                        ))
                    }
                }
            }
        }

        Log.d(TAG, "Classified: ${result.size} cells")
        return result
    }

    private data class Line(val text: String, val y: Float, val fontSize: Float)
    private data class CourseBlock(
        val name: String,
        val detail: String,
        val startPeriod: Int,
        val endPeriod: Int,
        val maxFontSize: Float,
    )

    private fun mergeToLines(words: List<Glyph>): List<Line> {
        if (words.isEmpty()) return emptyList()
        val result = mutableListOf<Line>()
        var cur = mutableListOf(words[0])
        var curY = words[0].y

        for (i in 1 until words.size) {
            val w = words[i]
            if (kotlin.math.abs(w.y - curY) < 6f) {
                cur.add(w)
                curY = cur.map { it.y }.average().toFloat()
            } else {
                result.add(buildLine(cur))
                cur = mutableListOf(w); curY = w.y
            }
        }
        if (cur.isNotEmpty()) result.add(buildLine(cur))
        return result
    }

    private fun buildLine(glyphs: List<Glyph>): Line {
        val s = glyphs.sortedBy { it.x }
        return Line(
            text = s.joinToString("") { it.text },
            y = glyphs.map { it.y }.average().toFloat(),
            fontSize = glyphs.maxOf { it.fontSize },
        )
    }

    private fun extractBlocks(lines: List<Line>): List<CourseBlock> {
        val blocks = mutableListOf<CourseBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            // 判断课程名时忽略★（★可能出现在课程名行末）
            if (!isCourseName(line)) { i++; continue }

            val nameParts = mutableListOf(line.text.replace("★", ""))
            val detailParts = mutableListOf<String>()
            var maxFs = line.fontSize
            var lastY = line.y
            var j = i + 1

            while (j < lines.size) {
                val next = lines[j]
                val yGap = kotlin.math.abs(next.y - lastY)

                if (isCourseName(next)) {
                    // 多行课程名续行：必须Y间距<20pt、合并不超20字、
                    // 且前面不能有详情行（有详情说明上一个课程块已结束）
                    val mergedLen = nameParts.joinToString("").length + next.text.length
                    if (detailParts.isEmpty() && yGap < 20f && mergedLen <= 20) {
                        nameParts.add(next.text.replace("★", ""))
                        maxFs = maxOf(maxFs, next.fontSize)
                        lastY = next.y; j++
                        continue
                    } else {
                        break
                    }
                }
                detailParts.add(next.text)
                lastY = next.y; j++
            }

            val fullDetail = detailParts.joinToString("")
            val (sp, ep) = extractPeriodRange(fullDetail) ?: Pair(0, 0)

            val finalName = nameParts.joinToString("").trim()
            if (finalName.isNotEmpty()) {
                blocks.add(CourseBlock(
                    name = finalName,
                    detail = fullDetail,
                    startPeriod = sp,
                    endPeriod = ep,
                    maxFontSize = maxFs,
                ))
            }

            i = j
        }

        return blocks
    }

    private fun isCourseName(line: Line): Boolean {
        if (line.fontSize < 8.5f) return false
        val t = line.text.trim()
        if (t.isEmpty()) return false
        if (t.length > 80) return false
        if (t.toIntOrNull() != null) return false  // 纯数字
        if (t.startsWith("星期")) return false
        if (t.startsWith("(")) return false  // 节次标记
        if (t.contains("学期")) return false
        if (t.contains("学号")) return false
        if (t.contains("课表")) return false
        if (t.contains("打印")) return false
        if (t.startsWith("实践课程") || t.startsWith("实验课程")) return false
        if (t.contains("集中实践")) return false
        if (t.startsWith(":") || t.startsWith("：")) return false
        return t.any { it in '一'..'鿿' }  // 必须含中文
    }

    private fun extractPeriodRange(text: String): Pair<Int, Int>? {
        val r = Regex("[（(](\\d+)[-–—](\\d+)节[)）]")
        val m = r.find(text) ?: return null
        val s = m.groupValues[1].toIntOrNull() ?: return null
        val e = m.groupValues[2].toIntOrNull() ?: return null
        return if (s in 1..12 && e in 1..12 && s <= e) s to e else null
    }
}
