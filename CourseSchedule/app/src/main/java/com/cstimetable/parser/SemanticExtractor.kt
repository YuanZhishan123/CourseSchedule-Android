package com.cstimetable.parser

/**
 * 语义信息提取器 v2
 *
 * 从合并后的单元格文本中提取：课程名、教师、地点、周次、节次。
 *
 * 方正教务 PDF 的元数据格式：
 *   (3-5节)1-13周,16-17周/校区:下沙/楼号:广宇楼/场地:(东)广宇楼kB406/教师:陈薇/教学班:(2025-2026-2)-...
 *
 * 关键词：场地=教室，"楼号"或"楼宇"=建筑，"教师"=教师，"教学班"=课程编号
 */
class SemanticExtractor {

    fun extract(cells: List<CellContent>): List<ParsedCourse> {
        return cells.mapNotNull { cell -> parseCell(cell) }
    }

    private fun parseCell(cell: CellContent): ParsedCourse? {
        val lines = cell.text.split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        // Step 1: 找课程名 — 找包含中文、不含"/"、最像课程名的行
        val nameCandidates = mutableListOf<String>()
        val detailCandidates = mutableListOf<String>()

        for (line in lines) {
            val t = line.trim()
            if (t.isEmpty()) continue
            // 包含 "/" 或 "教师" "场地" "楼号" "校区" 的是详情行
            if (t.contains("/") || t.contains("教师") || t.contains("场地")
                || t.contains("楼号") || t.contains("校区") || t.contains("教学班")) {
                detailCandidates.add(t)
            } else {
                nameCandidates.add(t)
            }
        }

        // 课程名 = 不含详情的行中，最长的含中文且不含冒号的行
        val courseName = nameCandidates
            .filter { line ->
                line.any { c -> c in '一'..'鿿' } &&
                ':' !in line && '：' !in line &&
                !line.startsWith("(")  // 不以括号开头（那是节次标记）
            }
            .maxByOrNull { it.length }
            ?.trim()
            ?: return null

        // 过滤噪音
        if (courseName.length <= 1 || courseName.length > 60) return null
        if (courseName in setOf("上午", "下午", "晚上")) return null
        if (courseName.startsWith("实践课程") || courseName.startsWith("实验课程")) return null
        if (courseName.contains("打印时间")) return null
        if (courseName.startsWith("教学班") || courseName.startsWith("学班")) return null
        if (courseName.startsWith("组成") || courseName.startsWith("选课")) return null

        // Step 2: 合并详情行，解析 "/" 分隔字段
        val fullDetail = detailCandidates.joinToString("")
        val fields = parseSlashFields(fullDetail)

        // Step 3: 提取各字段
        val teacher = fields["教师"] ?: fields["老师"] ?: "未知"

        // 地点：优先"场地"，其次"教室"
        val location = fields["场地"] ?: fields["教室"] ?: run {
            val bld = fields["楼号"] ?: fields["楼宇"]
            if (bld != null) bld else "未知地点"
        }

        // 周次
        val weeks = extractWeeks(fullDetail)

        // 节次
        val periodRange = extractPeriodRange(fullDetail)
        val sp = periodRange?.first ?: cell.startPeriod
        val ep = periodRange?.second ?: cell.startPeriod

        return ParsedCourse(
            name = courseName,
            day = cell.day,
            startPeriod = sp,
            endPeriod = ep,
            weeks = weeks,
            location = location,
            teacher = teacher,
        )
    }

    /**
     * 解析 "/" 分隔的键值对
     */
    private fun parseSlashFields(detail: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        // 先按 "/" 分割
        val parts = detail.split("/")
        for (part in parts) {
            val trimmed = part.trim()
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx > 0 && colonIdx < trimmed.length - 1) {
                val key = trimmed.substring(0, colonIdx).trim()
                val value = trimmed.substring(colonIdx + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty() && key.length < 10) {
                    result[key] = value
                }
            }
        }
        return result
    }

    private fun extractWeeks(text: String): List<Int> {
        val weeks = mutableSetOf<Int>()
        val rangeRegex = Regex("(\\d+)[-–—](\\d+)周")
        for (m in rangeRegex.findAll(text)) {
            val s = m.groupValues[1].toIntOrNull() ?: continue
            val e = m.groupValues[2].toIntOrNull() ?: continue
            if (s in 1..30 && e in 1..30 && s <= e) weeks.addAll(s..e)
        }
        val singleRegex = Regex("(?<![-–—\\d])(\\d+)周")
        for (m in singleRegex.findAll(text)) {
            val w = m.groupValues[1].toIntOrNull() ?: continue
            if (w in 1..30) weeks.add(w)
        }
        return weeks.toList().sorted()
    }

    private fun extractPeriodRange(text: String): Pair<Int, Int>? {
        val r = Regex("[（(](\\d+)[-–—](\\d+)节[)）]")
        val m = r.find(text) ?: return null
        val s = m.groupValues[1].toIntOrNull() ?: return null
        val e = m.groupValues[2].toIntOrNull() ?: return null
        return if (s in 1..12 && e in 1..12 && s <= e) s to e else null
    }

    /** 判断是否为课程名行 */
    @Suppress("unused")
    private fun isCourseNameCandidate(text: String): Boolean {
        return text.any { it in '一'..'鿿' } && text.length in 2..50
            && !text.contains("/") && !text.contains("：") && !text.contains(":")
    }
}
