package com.dayledger.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dayledger.app.util.Fmt
import kotlinx.coroutines.launch

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec("today", "今天", Icons.Rounded.Today),
    TabSpec("calendar", "日历", Icons.Rounded.CalendarMonth),
    TabSpec("report", "报告", Icons.Rounded.Insights),
    TabSpec("settings", "我的", Icons.Rounded.Settings),
)

@Composable
fun AppRoot() {
    val vm: MainViewModel = viewModel()
    val nav = rememberNavController()

    val day by vm.day.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val monthScores by vm.monthScores.collectAsStateWithLifecycle()
    val week by vm.weekReport.collectAsStateWithLifecycle()
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val reportMonth by vm.reportMonth.collectAsStateWithLifecycle()
    val monthReport by vm.monthReport.collectAsStateWithLifecycle()

    var addMode by remember { mutableStateOf<AddMode?>(null) }
    var editData by remember { mutableStateOf<EditData?>(null) }
    var showReview by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 通知权限：只在第一次开计时的时候问；同意后再真正开始，避免通知被丢掉
    var pendingTimerStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingTimerStart?.invoke()
        pendingTimerStart = null
    }
    val askNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "today"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            if (route != tab.route) {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (route == "today") {
                ExtendedFloatingActionButton(
                    onClick = { addMode = AddMode.Menu },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("记一笔") },
                )
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = "today", modifier = Modifier.padding(padding)) {
            composable("today") {
                TodayScreen(
                    day = day,
                    onAddExpense = { addMode = AddMode.Expense },
                    onAddTime = { addMode = AddMode.Time },
                    onAddMeal = { addMode = AddMode.Meal },
                    onAddEvent = { addMode = AddMode.Event },
                    onReview = { showReview = true },
                    onPrevDay = { vm.selectDate(Fmt.parse(day.date).minusDays(1).toString()) },
                    onNextDay = { vm.selectDate(Fmt.parse(day.date).plusDays(1).toString()) },
                    onToday = vm::goToday,
                    onStopTimer = vm::stopTimer,
                    onDeleteTx = vm::deleteTransaction,
                    onDeleteBlock = vm::deleteBlock,
                    onDeleteMeal = vm::deleteMeal,
                    onCompleteEvent = vm::completeEvent,
                    onSkipEvent = vm::skipEvent,
                    onDeleteEvent = vm::deleteEvent,
                    onEditRow = { row -> editData = editDataFor(day, row) },
                    onAddWater = vm::addWater,
                )
            }
            composable("calendar") {
                CalendarScreen(
                    month = month,
                    scores = monthScores,
                    onPrevMonth = { vm.shiftMonth(-1) },
                    onNextMonth = { vm.shiftMonth(1) },
                    onThisMonth = vm::goCurrentMonth,
                    onOpenDate = { date ->
                        vm.selectDate(date)
                        nav.navigate("today") {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("report") {
                ReportScreen(
                    day = day,
                    week = week,
                    weekStart = weekStart,
                    month = reportMonth,
                    monthReport = monthReport,
                    onPrevWeek = { vm.shiftWeek(-1) },
                    onNextWeek = { vm.shiftWeek(1) },
                    onThisWeek = vm::goCurrentWeek,
                    onPrevMonth = { vm.shiftReportMonth(-1) },
                    onNextMonth = { vm.shiftReportMonth(1) },
                    onThisMonth = vm::goCurrentReportMonth,
                )
            }
            composable("settings") {
                SettingsScreen(
                    vm = vm,
                    settings = settings,
                    onToast = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                )
            }
        }
    }

    if (addMode != null || editData != null) {
        QuickAddSheet(
            date = day.date,
            categories = day.snapshot.categories,
            activities = day.snapshot.activities,
            initialMode = addMode ?: AddMode.Menu,
            edit = editData,
            onDismiss = {
                addMode = null
                editData = null
            },
            onAddExpense = { cents, catId, note, at -> vm.addExpense(cents, catId, note, at) },
            onStartTimer = { id, name, target ->
                val start = { vm.startTimer(id, name, target) }
                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    pendingTimerStart = start
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    start()
                }
            },
            onAddBlock = { id, start, end -> vm.addBlock(id, start, end) },
            onAddMeal = { type, place, kcal, note, tags, at -> vm.addMeal(type, place, kcal, note, tags, at) },
            onAddEvent = { title, start, end, top3 ->
                // 日程提醒要用通知，第一次加日程时顺手申请
                askNotificationPermission()
                vm.addEvent(title, start, end, top3)
            },
            onUpdateExpense = { id, cents, catId, note -> vm.updateTransaction(id, cents, catId, note) },
            onUpdateBlock = { id, activityId, start, end -> vm.updateBlock(id, activityId, start, end) },
            onUpdateMeal = { id, type, place, kcal, note, tags -> vm.updateMeal(id, type, place, kcal, note, tags) },
            onUpdateEvent = { id, title, start, end, top3 -> vm.updateEvent(id, title, start, end, top3) },
        )
    }

    if (showReview) {
        ReviewSheet(
            date = day.date,
            review = day.snapshot.review,
            onDismiss = { showReview = false },
            onWater = vm::addWater,
            onSave = { mood, energy, bed, wake, noSpend, note ->
                vm.saveReview(mood, energy, bed, wake, noSpend, note)
                showReview = false
            },
        )
    }
}

private fun editDataFor(day: DayUi, row: TimelineRow): EditData? {
    val s = day.snapshot
    return when (row.kind) {
        TimelineRow.Kind.TX -> s.transactions.firstOrNull { it.id == row.refId }?.let {
            EditData(
                kind = AddMode.Expense,
                id = it.id,
                amountCents = it.amountCents,
                categoryId = it.categoryId,
                note = it.note,
            )
        }

        TimelineRow.Kind.TIME -> {
            val block = s.blocks.firstOrNull { it.id == row.refId }
            val end = block?.endAt
            if (block == null || end == null) null
            else EditData(
                kind = AddMode.Time,
                id = block.id,
                activityId = block.activityId,
                startAt = block.startAt,
                endAt = end,
                note = block.note,
            )
        }

        TimelineRow.Kind.MEAL -> s.meals.firstOrNull { it.id == row.refId }?.let {
            EditData(
                kind = AddMode.Meal,
                id = it.id,
                type = it.type,
                place = it.place,
                kcal = it.kcal,
                protein = it.proteinG,
                veg = it.vegG,
                note = it.note,
                tags = it.tags,
            )
        }

        TimelineRow.Kind.EVENT -> s.events.firstOrNull { it.id == row.refId }?.let {
            EditData(
                kind = AddMode.Event,
                id = it.id,
                title = it.title,
                startAt = it.startAt,
                endAt = it.endAt,
                top3 = it.top3,
            )
        }
    }
}
