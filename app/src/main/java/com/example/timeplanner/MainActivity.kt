package com.example.timeplanner

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.example.timeplanner.ui.PlannerViewModel
import com.example.timeplanner.ui.TimePlannerTheme

class MainActivity : ComponentActivity() {
    private val vm: PlannerViewModel by viewModels { PlannerViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            TimePlannerTheme {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                PlannerApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        i ?: return
        val id = i.getLongExtra(EXTRA_EDIT_TASK_ID, -1L)
        if (id > 0) vm.openTask(id)
        if (i.getBooleanExtra(EXTRA_OPEN_REVIEW, false)) vm.openReview.value = true
    }

    companion object {
        const val EXTRA_EDIT_TASK_ID = "edit_task_id"
        const val EXTRA_OPEN_REVIEW = "open_review"
    }
}
