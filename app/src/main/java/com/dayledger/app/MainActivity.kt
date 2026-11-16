package com.dayledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.dayledger.app.ui.AppRoot
import com.dayledger.app.ui.theme.DayTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 可选：adb 启动时带 --ez demo true，写入两周示例数据（空库才生效）
        if (intent?.getBooleanExtra("demo", false) == true) {
            val app = application as DayApp
            lifecycleScope.launch(Dispatchers.IO) { app.container.repo.seedDemoData() }
        }

        setContent {
            DayTheme {
                AppRoot()
            }
        }
    }
}
