package com.thelightphone.lifestyle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.gridUnitsAsDp
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TodayViewModel internal constructor(
    private val repository: LifestyleRepository,
) : LightViewModel<Unit>() {
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    private val _viewingMonth = MutableStateFlow(false)
    val viewingMonth: StateFlow<Boolean> = _viewingMonth.asStateFlow()

    init {
        viewModelScope.launch { repository.ensureLoaded() }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _visibleMonth.value = YearMonth.from(date)
        _viewingMonth.value = false
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
}

@InitialScreen
class TodayScreen(
    activity: SealedLightActivity,
) : LightScreen<Unit, TodayViewModel>(activity) {

    private val repository = LifestyleRepository.get(lightContext.filesDir, lightContext::readAsset)

    override val viewModelClass: Class<TodayViewModel> = TodayViewModel::class.java

    override fun createViewModel() = TodayViewModel(repository)

    @Composable
    override fun Content() {
        val ready by repository.ready.collectAsState()
        val data by repository.data.collectAsState()
        val selectedDate by viewModel.selectedDate.collectAsState()
        val visibleMonth by viewModel.visibleMonth.collectAsState()
        val viewingMonth by viewModel.viewingMonth.collectAsState()
        val saveError by repository.error.collectAsState()
        val date = selectedDate.toString()
        val totals = totalsForDate(data.meals, date)
        val goals = resolvedGoals(data.goals)
        val calories = Nutrients.byId(Nutrients.CALORIES)
        val macros = Nutrients.all.filter { it.id != Nutrients.CALORIES && !data.isNutrientHidden(it.id) }
        val showingToday = selectedDate == LocalDate.now()

        LifestyleScaffold(
            title = if (viewingMonth) monthYearTitle(visibleMonth) else compactDateTitle(selectedDate),
            onTitleClick = if (ready) viewModel::toggleCalendar else null,
            leftButton = if (ready && viewingMonth) {
                chevronButton(previous = true) { viewModel.shiftMonth(-1) }
            } else {
                null
            },
            rightButton = when {
                !ready -> null
                viewingMonth -> chevronButton(previous = false) { viewModel.shiftMonth(1) }
                !showingToday -> null
                else -> LightBarButton.LightIcon(
                    icon = LightIcons.SETTINGS,
                    onClick = { navigateTo({ SettingsScreen(it, repository) }) },
                    contentDescription = "Settings",
                )
            },
            bottomItems = when {
                !ready -> emptyList()
                viewingMonth || !showingToday -> listOf(
                    textButton("VIEW TODAY") { viewModel.jumpToToday() },
                )
                else -> listOf(
                    textButton("MEALS") { navigateTo({ MealsScreen(it, repository) }) },
                    textButton("EXERCISE") { navigateTo({ WorkoutScreen(it, repository) }) },
                )
            },
            errorMessage = saveError,
            onDismissError = repository::dismissError,
        ) {
            if (!ready || calories == null) {
                LoadingBody()
            } else if (viewingMonth) {
                MonthCalendar(
                    month = visibleMonth,
                    selected = selectedDate,
                    marked = loggedDates(data),
                    onSelect = viewModel::selectDate,
                )
            } else {
                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                    ) {
                        CalorieHero(
                            current = totals[calories.id] ?: 0.0,
                            goal = goals[calories.id] ?: calories.defaultGoal,
                            unit = calories.unit,
                        )
                        macros.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(1.5f.gridUnitsAsDp()),
                            ) {
                                row.forEach { nutrient ->
                                    val storedCurrent = totals[nutrient.id] ?: 0.0
                                    val storedGoal = goals[nutrient.id] ?: nutrient.defaultGoal
                                    val water = nutrient.id == Nutrients.WATER
                                    NutrientGoalRow(
                                        label = nutrient.label,
                                        current = if (water) {
                                            waterAmountForDisplay(storedCurrent, data.waterUnit)
                                        } else {
                                            storedCurrent
                                        },
                                        goal = if (water) {
                                            waterAmountForDisplay(storedGoal, data.waterUnit)
                                        } else {
                                            storedGoal
                                        },
                                        unit = if (water) data.waterUnit else nutrient.unit,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
