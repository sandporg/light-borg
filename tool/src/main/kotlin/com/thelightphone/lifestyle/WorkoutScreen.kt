package com.thelightphone.lifestyle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DEFAULT_SETS = 3
private const val DEFAULT_REPS = 10

internal class WorkoutViewModel(
    private val repository: LifestyleRepository,
) : LightViewModel<Unit>() {
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    private val _viewingMonth = MutableStateFlow(false)
    val viewingMonth: StateFlow<Boolean> = _viewingMonth.asStateFlow()

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { repository.ensureLoaded() }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        _today.value = LocalDate.now()
    }

    fun dismissError() {
        _error.value = null
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _visibleMonth.value = YearMonth.from(date)
        _viewingMonth.value = false
    }

    fun shiftDay(days: Long) {
        selectDate(_selectedDate.value.plusDays(days))
    }

    fun shiftMonth(months: Long) {
        _visibleMonth.value = _visibleMonth.value.plusMonths(months)
    }

    fun toggleCalendar() {
        if (!_viewingMonth.value) {
            _visibleMonth.value = YearMonth.from(_selectedDate.value)
        }
        _viewingMonth.value = !_viewingMonth.value
    }

    fun jumpToToday() {
        selectDate(LocalDate.now())
    }

    fun setEditing(editing: Boolean) {
        _editing.value = editing
    }

    fun addGroup(raw: String) {
        when (val name = validateName(raw)) {
            is NameValidation.Invalid -> _error.value = name.message
            is NameValidation.Ok -> viewModelScope.launch {
                val saved = repository.addGroup(_selectedDate.value.dayOfWeek, name.name)
                if (saved) _editing.value = true
            }
        }
    }

    fun renameGroup(groupId: String, raw: String) {
        when (val name = validateName(raw)) {
            is NameValidation.Invalid -> _error.value = name.message
            is NameValidation.Ok -> viewModelScope.launch {
                repository.renameGroup(groupId, name.name)
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch { repository.deleteGroup(groupId) }
    }

    fun addExercise(groupId: String, exerciseId: String, sets: Int, reps: Int) {
        viewModelScope.launch { repository.addPlannedExercise(groupId, exerciseId, sets, reps) }
    }

    fun updateExercise(plannedId: String, sets: Int, reps: Int) {
        viewModelScope.launch { repository.updatePlannedExercise(plannedId, sets, reps) }
    }

    fun deleteExercise(plannedId: String) {
        viewModelScope.launch { repository.deletePlannedExercise(plannedId) }
    }

    fun toggleExercise(date: String, plannedId: String) {
        viewModelScope.launch { repository.toggleCheck(date, plannedId) }
    }
}

internal class WorkoutScreen(
    activity: SealedLightActivity,
    private val repository: LifestyleRepository,
) : LightScreen<Unit, WorkoutViewModel>(activity) {

    override val viewModelClass: Class<WorkoutViewModel> = WorkoutViewModel::class.java

    override fun createViewModel() = WorkoutViewModel(repository)

    @Composable
    override fun Content() {
        val ready by repository.ready.collectAsState()
        val data by repository.data.collectAsState()
        val exercises by repository.exercises.collectAsState()
        val selectedDate by viewModel.selectedDate.collectAsState()
        val visibleMonth by viewModel.visibleMonth.collectAsState()
        val viewingMonth by viewModel.viewingMonth.collectAsState()
        val editing by viewModel.editing.collectAsState()
        val localError by viewModel.error.collectAsState()
        val saveError by repository.error.collectAsState()
        val selected = selectedDate.dayOfWeek
        val date = selectedDate.toString()
        val groups = data.groupsOn(selected)
        val checked = data.checkedIds(date)
        val names = exercises.associateBy { it.id }

        LifestyleScaffold(
            title = if (viewingMonth) monthYearTitle(visibleMonth) else compactDateTitle(selectedDate),
            onTitleClick = viewModel::toggleCalendar,
            leftButton = chevronButton(previous = true) {
                if (viewingMonth) viewModel.shiftMonth(-1) else viewModel.shiftDay(-1)
            },
            rightButton = chevronButton(previous = false) {
                if (viewingMonth) viewModel.shiftMonth(1) else viewModel.shiftDay(1)
            },
            bottomItems = when {
                !ready -> emptyList()
                viewingMonth -> listOf(
                    textButton("VIEW TODAY") { viewModel.jumpToToday() },
                )
                editing -> listOf(
                    textButton("DONE") { viewModel.setEditing(false) },
                    null,
                )
                groups.isEmpty() -> listOf(
                    null,
                    closeButton("Home") { goBack() },
                    textButton("ADD") { promptForGroup() },
                )
                else -> listOf(
                    textButton("EDIT") { viewModel.setEditing(true) },
                    closeButton("Home") { goBack() },
                    textButton("ADD") { promptForGroup() },
                )
            },
            errorMessage = localError ?: saveError,
            onDismissError = {
                viewModel.dismissError()
                repository.dismissError()
            },
        ) {
            if (!ready) {
                LoadingBody()
            } else if (viewingMonth) {
                MonthCalendar(
                    month = visibleMonth,
                    selected = selectedDate,
                    marked = loggedDates(data),
                    onSelect = viewModel::selectDate,
                )
            } else if (groups.isEmpty()) {
                EmptyDayMessage("No groups for ${weekdayName(selected)}")
            } else {
                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = 1f.gridUnitsAsDp(),
                            end = 0.5f.gridUnitsAsDp(),
                            bottom = 1f.gridUnitsAsDp(),
                        ),
                    ) {
                        groups.forEach { group ->
                            GroupBlock(
                                group = group,
                                names = names,
                                checked = checked,
                                editing = editing,
                                onRename = { promptToRename(group) },
                                onDeleteGroup = { confirmDeleteGroup(group) },
                                onAddExercise = { openPicker(group.id) },
                                onToggle = { plannedId -> viewModel.toggleExercise(date, plannedId) },
                                onEditExercise = { planned, name -> openSets(planned, name) },
                                onDeleteExercise = { plannedId, exerciseName ->
                                    confirmDeleteExercise(plannedId, exerciseName)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun confirmDeleteGroup(group: ExerciseGroup) {
        navigateTo({
            ConfirmScreen(
                it,
                title = "Delete group",
                message = "Delete ${group.name}?",
            )
        }) { confirmed ->
            if (confirmed) viewModel.deleteGroup(group.id)
        }
    }

    private fun confirmDeleteExercise(plannedId: String, exerciseName: String) {
        navigateTo({
            ConfirmScreen(
                it,
                title = "Delete exercise",
                message = "Delete $exerciseName?",
            )
        }) { confirmed ->
            if (confirmed) viewModel.deleteExercise(plannedId)
        }
    }

    private fun promptForGroup() {
        navigateTo({
            TextPromptScreen(
                it,
                title = "Add group",
                initialValue = "",
                initialCaps = true,
                submitLabel = "SAVE",
                showBackButton = false,
                centerClose = true,
            )
        }) { raw -> viewModel.addGroup(raw) }
    }

    private fun promptToRename(group: ExerciseGroup) {
        navigateTo({
            TextPromptScreen(
                it,
                title = "Group name",
                initialValue = group.name,
                initialCaps = false,
                submitLabel = "SAVE",
                showBackButton = false,
                centerClose = true,
            )
        }) { raw -> viewModel.renameGroup(group.id, raw) }
    }

    private fun openPicker(groupId: String) {
        navigateTo({ ExercisePickerScreen(it, repository) }) { picked ->
            navigateTo({
                SetsRepsScreen(
                    it,
                    exerciseName = picked.name,
                    initialSets = DEFAULT_SETS,
                    initialReps = DEFAULT_REPS,
                )
            }) { setsReps ->
                viewModel.addExercise(groupId, picked.id, setsReps.sets, setsReps.reps)
            }
        }
    }

    private fun openSets(planned: PlannedExercise, name: String) {
        navigateTo({
            SetsRepsScreen(
                it,
                exerciseName = name,
                initialSets = planned.sets,
                initialReps = planned.reps,
            )
        }) { setsReps ->
            viewModel.updateExercise(planned.id, setsReps.sets, setsReps.reps)
        }
    }
}

@Composable
private fun GroupBlock(
    group: ExerciseGroup,
    names: Map<String, Exercise>,
    checked: Set<String>,
    editing: Boolean,
    onRename: () -> Unit,
    onDeleteGroup: () -> Unit,
    onAddExercise: () -> Unit,
    onToggle: (String) -> Unit,
    onEditExercise: (PlannedExercise, String) -> Unit,
    onDeleteExercise: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 1f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                text = group.name,
                variant = LightTextVariant.Subheading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (editing) Modifier.lightClickable(onClick = onRename) else Modifier,
                    ),
            )
            if (editing) {
                LightIcon(
                    icon = LightIcons.TRASH,
                    contentDescription = "Delete group",
                    modifier = Modifier.lightClickable(onClick = onDeleteGroup),
                )
            }
        }
        group.exercises.forEach { planned ->
            val name = names[planned.exerciseId]?.name ?: "Unknown exercise"
            PlannedExerciseRow(
                name = name,
                sets = planned.sets,
                reps = planned.reps,
                checked = planned.id in checked,
                editing = editing,
                onToggle = { onToggle(planned.id) },
                onEdit = { onEditExercise(planned, name) },
                onDelete = { onDeleteExercise(planned.id, name) },
            )
        }
        if (editing) {
            LightText(
                text = "Add exercise",
                variant = LightTextVariant.Copy,
                underline = true,
                modifier = Modifier
                    .lightClickable(onClick = onAddExercise)
                    .padding(vertical = 0.75f.gridUnitsAsDp()),
            )
        }
    }
}

@Composable
private fun PlannedExerciseRow(
    name: String,
    sets: Int,
    reps: Int,
    checked: Boolean,
    editing: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.5f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .lightClickable { if (editing) onEdit() else onToggle() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!editing) {
                LightIcon(
                    icon = if (checked) LightIcons.SELECT_ON else LightIcons.SELECT_OFF,
                    contentDescription = if (checked) "Done" else "Not done",
                )
                Spacer(modifier = Modifier.width(0.75f.gridUnitsAsDp()))
            }
            Column(modifier = Modifier.weight(1f)) {
                LightText(
                    text = name,
                    variant = LightTextVariant.Copy,
                    lighten = checked && !editing,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                LightText(
                    text = "$sets × $reps",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                )
            }
        }
        if (editing) {
            LightIcon(
                icon = LightIcons.TRASH,
                contentDescription = "Remove",
                modifier = Modifier.lightClickable(onClick = onDelete),
            )
        }
    }
}
