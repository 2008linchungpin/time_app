@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.timeplanner

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.timeplanner.ui.InboxScreen
import com.example.timeplanner.ui.MiscScreen
import com.example.timeplanner.ui.OverviewScreen
import com.example.timeplanner.ui.PlannerViewModel
import com.example.timeplanner.ui.QuickAddContent
import com.example.timeplanner.ui.ReviewHistoryScreen
import com.example.timeplanner.ui.ReviewScreen
import com.example.timeplanner.ui.SettingsScreen
import com.example.timeplanner.ui.TimelineScreen

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Dest("timeline", "時間軸", Icons.Filled.Schedule),
    Dest("inbox", "Inbox", Icons.Filled.Inbox),
    Dest("misc", "雜碎", Icons.Filled.Bolt),
    Dest("review", "回顧", Icons.Filled.BarChart),
    Dest("settings", "設定", Icons.Filled.Settings)
)

private fun NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun PlannerApp(vm: PlannerViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val editRequest by vm.editRequest.collectAsStateWithLifecycle()
    val openReview by vm.openReview.collectAsStateWithLifecycle()
    var showQuick by remember { mutableStateOf(false) }

    // 通知點擊：開啟任務編輯 / 開啟每日回顧
    LaunchedEffect(editRequest) { if (editRequest != null) nav.navigateTab("timeline") }
    LaunchedEffect(openReview) {
        if (openReview) {
            nav.navigateTab("review")
            vm.openReview.value = false
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEach { d ->
                    NavigationBarItem(
                        selected = route == d.route || (d.route == "review" && (route == "history" || route == "overview")),
                        onClick = { nav.navigateTab(d.route) },
                        icon = { Icon(d.icon, d.label) },
                        label = { Text(d.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (route != "settings" && route != "history" && route != "overview") {
                FloatingActionButton(onClick = { showQuick = true }) { Icon(Icons.Filled.Add, "快速記下") }
            }
        }
    ) { inner ->
        NavHost(nav, startDestination = "timeline", modifier = Modifier.padding(inner)) {
            composable("timeline") { TimelineScreen(vm) }
            composable("inbox") { InboxScreen(vm) }
            composable("misc") { MiscScreen(vm) }
            composable("review") {
                ReviewScreen(vm, onOpenHistory = { nav.navigate("history") }, onOpenOverview = { nav.navigate("overview") })
            }
            composable("overview") { OverviewScreen(vm, onBack = { nav.popBackStack() }) }
            composable("history") { ReviewHistoryScreen(vm, onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(vm) }
        }
    }

    if (showQuick) {
        ModalBottomSheet(
            onDismissRequest = { showQuick = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            QuickAddContent(onAdd = { t, misc -> vm.quickAdd(t, misc) }, onClose = { showQuick = false })
        }
    }
}
