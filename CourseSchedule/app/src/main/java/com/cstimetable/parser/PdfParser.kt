package com.cstimetable.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cstimetable.model.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfParser {
    private const val TAG = "CSTimetable"

    private val glyphReader = PdfGlyphReader()
    private val layoutDetector = TableStructureDetector()
    private val classifier = CellClassifier()
    private val extractor = SemanticExtractor()
    private val mapper = ModelMapper()

    suspend fun parse(context: Context, uri: Uri): Result<Schedule> =
        withContext(Dispatchers.IO) {
            try {
                val tempFile = File(context.cacheDir, "temp_schedule.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext Result.failure(Exception("无法读取文件"))

                // 1. 提取字形
                val glyphs = glyphReader.extractGlyphs(tempFile)
                Log.d(TAG, "Parsing: ${glyphs.size} glyphs extracted")

                // 2. 检测表格布局 + 合并词 →
                val detectResult = layoutDetector.detect(glyphs)

                // 3. 网格映射 → 单元格
                val cells = classifier.classify(detectResult)
                Log.d(TAG, "Cells (${cells.size}):")
                cells.forEach { c ->
                    Log.d(TAG, "  D${c.day} P${c.startPeriod} fs=${"%.1f".format(c.maxFontSize)} page${c.page} | ${c.text.take(100)}")
                }

                // 4. 结构化提取 → 课程
                val parsedCourses = extractor.extract(cells)
                Log.d(TAG, "ParsedCourses (${parsedCourses.size}):")
                parsedCourses.forEach { pc ->
                    Log.d(TAG, "  ${pc.name} | D${pc.day} P${pc.startPeriod}-${pc.endPeriod} | weeks=${pc.weeks} | ${pc.location} | ${pc.teacher}")
                }

                // 5. 映射为 Schedule
                val allText = detectResult.words.joinToString("") { it.text }
                val metadata = extractMetadata(allText)
                val schedule = mapper.mapToSchedule(
                    parsedCourses = parsedCourses,
                    semester = metadata.semester,
                    studentName = metadata.studentName,
                    studentId = metadata.studentId,
                )
                Log.d(TAG, "Parsed ${schedule.courses.size} courses, semester=${schedule.semester}")

                tempFile.delete()
                Result.success(schedule)
            } catch (e: Exception) {
                Log.e(TAG, "PDF parse error", e)
                Result.failure(Exception("PDF 解析失败: ${e.message}", e))
            }
        }

    /**
     * 从全部文本中提取元数据（学期、学生姓名、学号）
     */
    private fun extractMetadata(allText: String): PdfMetadata {
        // 学期：匹配 "2025-2026学年第2学期" 格式
        val semesterRegex = Regex("(\\d{4}-\\d{4}\\s*学年第?\\s*\\d\\s*学期)")
        val semester = semesterRegex.find(allText)?.value?.trim() ?: "未知学期"

        // 学号
        val studentIdRegex = Regex("学号[:：]\\s*(\\d+)")
        val studentId = studentIdRegex.find(allText)?.groupValues?.get(1) ?: ""

        // 学生姓名：尝试匹配 "XXX课表" 或 "XXX 课表"
        val nameRegex = Regex("([\\u4e00-\\u9fff]{2,4})\\s*课表")
        val studentName = nameRegex.find(allText)?.groupValues?.get(1) ?: "未知"

        return PdfMetadata(semester, studentName, studentId)
    }
}

/**
 * PDF 元数据
 */
private data class PdfMetadata(
    val semester: String,
    val studentName: String,
    val studentId: String,
)
