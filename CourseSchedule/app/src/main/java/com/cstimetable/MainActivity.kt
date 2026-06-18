package com.cstimetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cstimetable.ui.screen.ScheduleScreen
import com.cstimetable.ui.theme.CStdTheme

/**
 * 主 Activity
 *
 * 课程表 APP 的唯一入口
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CStdTheme {
                ScheduleScreen()
            }
        }
    }
}
