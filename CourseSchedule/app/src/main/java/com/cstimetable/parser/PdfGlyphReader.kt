package com.cstimetable.parser

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.Writer

/**
 * PDF 字形提取器
 *
 * 使用 PdfBox-Android 的 PDFTextStripper，逐字符提取 PDF 原生文本流中的
 * 文字、坐标、字号信息。不需要渲染页面，不需要 OCR。
 *
 * 重要：使用前需在 Application 中调用 PDFBoxResourceLoader.init(context)。
 *
 * 输出 List<Glyph> 包含页面中每一个非空白字符的 (text, x, y, page, fontSize)。
 * 依赖：com.tom-roush:pdfbox-android:2.0.27.0 (~2MB)
 */
class PdfGlyphReader {

    /**
     * 从 PDF 文件提取所有字形
     */
    fun extractGlyphs(pdfFile: File): List<Glyph> {
        val glyphs = mutableListOf<Glyph>()

        PDDocument.load(pdfFile).use { document ->
            val stripper = GlyphStripper(glyphs)
            stripper.sortByPosition = true

            // 逐页处理
            for (pageNum in 0 until document.numberOfPages) {
                stripper.currentPage = pageNum
                stripper.setStartPage(pageNum + 1)
                stripper.setEndPage(pageNum + 1)
                // writeText 触发文本提取 — 输出丢弃，数据已在 glyphs 中
                stripper.writeText(document, NullWriter())
            }
        }

        return glyphs
    }

    /**
     * 内部 PDFTextStripper 子类
     *
     * 重写 writeString 获取每个字符的文本和坐标。
     * pdfbox-android 中 getXDirAdj()/getYDirAdj() 的坐标原点在左上角 (0,0)，
     * Y 向下增大，即标准的屏幕坐标系。
     */
    private class GlyphStripper(
        private val glyphs: MutableList<Glyph>,
    ) : PDFTextStripper() {

        var currentPage: Int = 0

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            for (tp in textPositions) {
                val unicode = tp.unicode
                if (unicode.isBlank()) continue

                glyphs.add(
                    Glyph(
                        text = unicode,
                        x = tp.xDirAdj,
                        y = tp.yDirAdj,
                        page = currentPage,
                        fontSize = tp.fontSizeInPt,
                    )
                )
            }
        }
    }
}

/**
 * 空 Writer — 丢弃 PDFTextStripper 默认的文本输出，
 * 我们只需要 writeString 回调中的坐标数据。
 */
private class NullWriter : Writer() {
    override fun write(cbuf: CharArray, off: Int, len: Int) {}
    override fun flush() {}
    override fun close() {}
}
