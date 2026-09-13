package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.buildDatabase
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Prefix shown before every routine name in the list ("[x] " or "[ ] ") - used to estimate line fit too. */
private const val ROUTINE_CHECKBOX_PREFIX = "[ ] "
private const val DEFAULT_NEW_ROUTINE_TARGET = 3
private const val MIN_WEEKLY_TARGET = 1
private const val MAX_WEEKLY_TARGET = 7

sealed class HomeMode {
    data object RoutineList : HomeMode()
    data object EditRoutineList : HomeMode()
    data object QuarterOverview : HomeMode()
    data class SelectObjective(val objectives: List<ObjectiveWithRoutines>) : HomeMode()
    data class EnterRoutineName(
        val objectiveId: Long,
        val objectiveTitle: String,
        val initialName: String = "",
        val editingRoutineId: Long? = null,
    ) : HomeMode()
    data class EnterWeeklyTarget(
        val objectiveId: Long,
        val objectiveTitle: String,
        val name: String,
        val target: Int,
        val editingRoutineId: Long? = null,
    ) : HomeMode()
}

class HomeScreenViewModel(
    private val repository: HabitTrackerRepository,
) : LightViewModel<Unit>() {

    private val _overview = MutableStateFlow<PriorityOverview?>(null)
    val overview: StateFlow<PriorityOverview?> = _overview.asStateFlow()

    private val _quarterOverview = MutableStateFlow<QuarterOverview?>(null)
    val quarterOverview: StateFlow<QuarterOverview?> = _quarterOverview.asStateFlow()

    private val _mode = MutableStateFlow<HomeMode>(HomeMode.RoutineList)
    val mode: StateFlow<HomeMode> = _mode.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        reload()
    }

    fun toggleRoutine(routineId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleRoutineToday(routineId)
            reload()
        }
    }

    fun startAddRoutine() {
        val objectives = _overview.value?.objectives ?: return
        _mode.value = HomeMode.SelectObjective(objectives)
    }

    fun selectObjective(objectiveId: Long, objectiveTitle: String) {
        _mode.value = HomeMode.EnterRoutineName(objectiveId, objectiveTitle)
    }

    fun startEditRoutineList() {
        _mode.value = HomeMode.EditRoutineList
    }

    fun showQuarterOverview() {
        _mode.value = HomeMode.QuarterOverview
        viewModelScope.launch(Dispatchers.IO) {
            _quarterOverview.value = repository.getQuarterOverview()
        }
    }

    fun startEditRoutine(objectiveId: Long, objectiveTitle: String, routine: RoutineWithProgress) {
        _mode.value = HomeMode.EnterRoutineName(
            objectiveId = objectiveId,
            objectiveTitle = objectiveTitle,
            initialName = routine.name,
            editingRoutineId = routine.id,
        )
    }

    fun submitRoutineName(name: String) {
        val current = _mode.value as? HomeMode.EnterRoutineName ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _mode.value = HomeMode.EnterWeeklyTarget(
            objectiveId = current.objectiveId,
            objectiveTitle = current.objectiveTitle,
            name = trimmed,
            target = DEFAULT_NEW_ROUTINE_TARGET,
            editingRoutineId = current.editingRoutineId,
        )
    }

    fun adjustWeeklyTarget(delta: Int) {
        val current = _mode.value as? HomeMode.EnterWeeklyTarget ?: return
        _mode.value = current.copy(target = (current.target + delta).coerceIn(MIN_WEEKLY_TARGET, MAX_WEEKLY_TARGET))
    }

    fun saveRoutine() {
        val current = _mode.value as? HomeMode.EnterWeeklyTarget ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val editingId = current.editingRoutineId
            if (editingId != null) {
                repository.updateRoutine(editingId, current.name, current.target)
            } else {
                repository.addRoutine(current.objectiveId, current.name, current.target)
            }
            _mode.value = HomeMode.RoutineList
            reload()
        }
    }

    fun deleteRoutine() {
        val editingId = (_mode.value as? HomeMode.EnterWeeklyTarget)?.editingRoutineId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteRoutine(editingId)
            _mode.value = HomeMode.RoutineList
            reload()
        }
    }

    fun cancelAdd() {
        _mode.value = HomeMode.RoutineList
    }

    private fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.seedIfEmpty()
            _overview.value = repository.getOverview()
        }
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeScreenViewModel>(sealedActivity) {

    private val repository = HabitTrackerRepository.getInstance {
        lightContext.buildDatabase(HabitTrackerDatabase::class.java, HabitTrackerRepository.DATABASE_NAME)
    }

    override val viewModelClass: Class<HomeScreenViewModel>
        get() = HomeScreenViewModel::class.java

    override fun createViewModel(): HomeScreenViewModel {
        return HomeScreenViewModel(repository)
    }

    @Composable
    override fun Content() {
        val overview by viewModel.overview.collectAsState()
        val mode by viewModel.mode.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            when (val currentMode = mode) {
                is HomeMode.RoutineList -> RoutineListContent(
                    overview = overview,
                    onToggleRoutine = viewModel::toggleRoutine,
                    onAddRoutine = viewModel::startAddRoutine,
                    onEditRoutines = viewModel::startEditRoutineList,
                    onShowQuarterOverview = viewModel::showQuarterOverview,
                )

                is HomeMode.EditRoutineList -> EditRoutineListContent(
                    overview = overview,
                    onSelectRoutine = { objectiveId, objectiveTitle, routine ->
                        viewModel.startEditRoutine(objectiveId, objectiveTitle, routine)
                    },
                    onExitEditMode = viewModel::cancelAdd,
                )

                is HomeMode.QuarterOverview -> {
                    val quarterOverview by viewModel.quarterOverview.collectAsState()
                    QuarterOverviewContent(
                        overview = quarterOverview,
                        onBack = viewModel::cancelAdd,
                    )
                }

                is HomeMode.SelectObjective -> SelectObjectiveContent(
                    objectives = currentMode.objectives,
                    onSelect = viewModel::selectObjective,
                    onBack = viewModel::cancelAdd,
                )

                is HomeMode.EnterRoutineName -> {
                    val textFieldState = rememberTextFieldState(currentMode.initialName)
                    val remainingChars = rememberRemainingCharsForOneLine(textFieldState.text.toString())
                    val title = if (currentMode.editingRoutineId != null) {
                        "Modifica nome (resta ~$remainingChars)"
                    } else {
                        "${currentMode.objectiveTitle}: nome (resta ~$remainingChars)"
                    }
                    LightTextInputEditor(
                        title = title,
                        state = textFieldState,
                        keyboardOptionsFlow = keyboardOptionsFlow,
                        onSubmit = { viewModel.submitRoutineName(it.toString()) },
                        onBack = viewModel::cancelAdd,
                        submitLabel = "AVANTI",
                        singleLine = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is HomeMode.EnterWeeklyTarget -> WeeklyTargetContent(
                    state = currentMode,
                    onAdjust = viewModel::adjustWeeklyTarget,
                    onSave = viewModel::saveRoutine,
                    onDelete = viewModel::deleteRoutine,
                    onBack = viewModel::cancelAdd,
                )
            }
        }
    }
}

/**
 * Estimates how many more characters of [text] would still fit on one line in the routine list
 * (at the same text style/width used there), by measuring real glyph widths and extrapolating
 * from the average character width typed so far. Approximate: exact for what's typed, a guess
 * for what isn't (proportional fonts don't have a fixed per-character width).
 */
@Composable
private fun rememberRemainingCharsForOneLine(text: String): Int {
    val measurer = rememberTextMeasurer()
    val style = LightThemeTokens.typography.copy
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    return remember(text) {
        val availablePx = with(density) { (screenWidthDp - 64.dp).toPx() } // matches the 32dp side padding used in the list
        val prefixWidthPx = measurer.measure(ROUTINE_CHECKBOX_PREFIX, style, maxLines = 1, softWrap = false).size.width
        val nameWidthPx = measurer.measure(text.ifEmpty { " " }, style, maxLines = 1, softWrap = false).size.width
        val avgCharWidthPx = if (text.isNotEmpty()) {
            nameWidthPx.toFloat() / text.length
        } else {
            measurer.measure("n", style, maxLines = 1, softWrap = false).size.width.toFloat()
        }
        val remainingPx = availablePx - prefixWidthPx - nameWidthPx
        if (avgCharWidthPx <= 0f) 0 else (remainingPx / avgCharWidthPx).toInt()
    }
}

@Composable
private fun RoutineListContent(
    overview: PriorityOverview?,
    onToggleRoutine: (Long) -> Unit,
    onAddRoutine: () -> Unit,
    onEditRoutines: () -> Unit,
    onShowQuarterOverview: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
    ) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.PENCIL,
                onClick = onEditRoutines,
                contentDescription = "Modifica routine",
            ),
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.ADD,
                onClick = onAddRoutine,
                contentDescription = "Nuova routine",
            ),
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (overview == null) {
            LightText(
                text = "Caricamento...",
                variant = LightTextVariant.Copy,
                lighten = true,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        } else {
            ObjectivesRoutinesColumn(
                overview = overview,
                onQuarterClick = onShowQuarterOverview,
                onRoutineClick = { _, _, routine -> onToggleRoutine(routine.id) },
            )
        }
    }
}

@Composable
private fun EditRoutineListContent(
    overview: PriorityOverview?,
    onSelectRoutine: (objectiveId: Long, objectiveTitle: String, routine: RoutineWithProgress) -> Unit,
    onExitEditMode: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
    ) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onExitEditMode),
            center = LightTopBarCenter.Text("Modifica routine"),
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (overview == null) {
            LightText(
                text = "Caricamento...",
                variant = LightTextVariant.Copy,
                lighten = true,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        } else {
            ObjectivesRoutinesColumn(overview, onQuarterClick = {}, onRoutineClick = onSelectRoutine)
        }
    }
}

/** Shared priority title + objective/routine list, reused by the normal and edit-mode screens. */
@Composable
private fun ObjectivesRoutinesColumn(
    overview: PriorityOverview,
    onQuarterClick: () -> Unit,
    onRoutineClick: (objectiveId: Long, objectiveTitle: String, routine: RoutineWithProgress) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 32.dp)) {
        LightText(
            text = overview.title,
            variant = LightTextVariant.Heading,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        LightText(
            text = overview.quarter,
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier
                .lightClickable(onClick = onQuarterClick)
                .padding(bottom = 12.dp),
        )

        LazyColumn {
            overview.objectives.forEach { objective ->
                item {
                    LightText(
                        text = objective.title.uppercase(),
                        variant = LightTextVariant.Subheading,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(objective.routines) { routine ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable { onRoutineClick(objective.id, objective.title, routine) }
                            .padding(vertical = 5.dp),
                    ) {
                        LightText(
                            text = "${if (routine.doneToday) "[x]" else "[ ]"} ${routine.name}",
                            variant = LightTextVariant.Copy,
                        )
                        LightText(
                            text = "${routine.completionsThisWeek}/${routine.weeklyTarget} questa settimana" +
                                " · streak ${routine.streak}",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectObjectiveContent(
    objectives: List<ObjectiveWithRoutines>,
    onSelect: (Long, String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
    ) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text("Scegli obiettivo"),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Column(modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)) {
            objectives.forEach { objective ->
                LightText(
                    text = objective.title,
                    variant = LightTextVariant.Copy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable { onSelect(objective.id, objective.title) }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun WeeklyTargetContent(
    state: HomeMode.EnterWeeklyTarget,
    onAdjust: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
    ) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text("Volte a settimana"),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LightText(
                text = state.name,
                variant = LightTextVariant.Copy,
                lighten = true,
                modifier = Modifier.padding(top = 24.dp),
            )
            Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LightIcon(
                    icon = LightIcons.UP,
                    modifier = Modifier.lightClickable { onAdjust(1) }.padding(16.dp),
                )
                LightText(text = state.target.toString(), variant = LightTextVariant.Title)
                LightIcon(
                    icon = LightIcons.DOWN,
                    modifier = Modifier.lightClickable { onAdjust(-1) }.padding(16.dp),
                )
            }
        }
        LightBottomBar(
            items = listOfNotNull(
                if (state.editingRoutineId != null) {
                    LightBarButton.LightIcon(icon = LightIcons.TRASH, onClick = onDelete, contentDescription = "Elimina")
                } else {
                    null
                },
                LightBarButton.LightIcon(icon = LightIcons.ACCEPT, onClick = onSave, contentDescription = "Salva"),
            ),
        )
    }
}

@Composable
private fun QuarterOverviewContent(
    overview: QuarterOverview?,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
    ) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(overview?.quarterLabel ?: ""),
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (overview == null) {
            LightText(
                text = "Caricamento...",
                variant = LightTextVariant.Copy,
                lighten = true,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(horizontal = 32.dp)) {
                overview.objectives.forEach { objective ->
                    item {
                        LightText(
                            text = objective.title.uppercase(),
                            variant = LightTextVariant.Subheading,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(objective.routines) { routine ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            LightText(
                                text = "${routine.name} · streak ${routine.streak}",
                                variant = LightTextVariant.Copy,
                            )
                            WeekGrid(
                                weeks = routine.weeks,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val WEEK_CELL_SIZE = 14.dp
private val WEEK_CELL_GAP = 3.dp

/** One small square per week: filled = target reached, outlined = missed, faint = not happened yet. */
@Composable
private fun WeekGrid(weeks: List<WeekStatus>, modifier: Modifier = Modifier) {
    val colors = LightThemeTokens.colors
    Row(modifier = modifier) {
        weeks.forEach { status ->
            Box(
                modifier = Modifier
                    .padding(end = WEEK_CELL_GAP)
                    .size(WEEK_CELL_SIZE)
                    .then(
                        when (status) {
                            WeekStatus.SUCCESS -> Modifier.background(colors.content)
                            WeekStatus.MISS -> Modifier.border(1.dp, colors.content)
                            WeekStatus.FUTURE -> Modifier.border(1.dp, colors.contentSecondary)
                        },
                    ),
            )
        }
    }
}
