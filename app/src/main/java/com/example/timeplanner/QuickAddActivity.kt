package com.example.timeplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.timeplanner.ui.QuickAddContent
import com.example.timeplanner.ui.TimePlannerTheme
import kotlinx.coroutines.launch

/**
 * 快速新增：長按桌面圖示的捷徑（或自行放到桌面）直接開一個小視窗，
 * 打字 → Enter 就存進 Inbox（或雜碎），背景 App 不會被切走或重載。
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TimePlannerApplication
        setContent {
            TimePlannerTheme {
                Dialog(onDismissRequest = { finish() }) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                        QuickAddContent(
                            onAdd = { title, misc ->
                                app.appScope.launch {
                                    if (misc) app.repository.addMiscTask(title) else app.repository.addInboxTask(title)
                                }
                            },
                            onClose = { finish() }
                        )
                    }
                }
            }
        }
    }
}
