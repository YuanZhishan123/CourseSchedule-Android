package com.cstimetable

import android.app.Application
import com.cstimetable.notify.CourseAlarmReceiver
import com.cstimetable.notify.CourseNotificationHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Application 入口
 *
 * 全局初始化 PDFBox 资源加载器 + 课程通知服务
 */
class CStdApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        PDFBoxResourceLoader.init(this)

        // 初始化课程通知提醒（课前通知→手环）
        CourseNotificationHelper.init(this)
        CourseAlarmReceiver.schedule(this)
    }

    companion object {
        lateinit var instance: CStdApplication
            private set
    }
}
