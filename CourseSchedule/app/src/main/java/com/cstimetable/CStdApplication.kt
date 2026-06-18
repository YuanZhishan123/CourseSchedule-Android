package com.cstimetable

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Application 入口
 *
 * 全局初始化 PDFBox 资源加载器（新 PDF 解析方案必需）
 */
class CStdApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        PDFBoxResourceLoader.init(this)
    }

    companion object {
        lateinit var instance: CStdApplication
            private set
    }
}
