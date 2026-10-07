package com.thelightphone.lifestyle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
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
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val MEAL_ROW_HEIGHT = 5.5f

internal class MealsViewModel(
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

    fun deleteMeal(mealId: String) {
        viewModelScope.launch { repository.deleteMeal(mealId) }
    }
}

internal class MealsScreen(
    activity: SealedLightActivity,
    private val repository: LifestyleRepository,
) : LightScreen<Unit, MealsViewModel>(activity) {

    override val viewModelClass: Class<MealsViewModel> = MealsViewModel::class.java

    override fun createViewModel() = MealsViewModel(repository)

    @Composable
    override fun Content() {
        val ready by repository.ready.collectAsState()
        val data by repository.data.collectAsState()
        val selectedDate by viewModel.selectedDate.collectAsState()
        val visibleMonth by viewModel.visibleMonth.collectAsState()
        val viewingMonth by viewModel.viewingMonth.collectAsState()
        val editing by viewModel.editing.collectAsState()
        val localError by viewModel.error.collectAsState()
        val saveError by repository.error.collectAsState()
        val shownDate = selectedDate
        val meals = data.meals.filter { it.date == shownDate.toString() }

        LifestyleScaffold(
            title = if (viewingMonth) monthYearTitle(visibleMonth) else compactDateTitle(shownDate),
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
                meals.isEmpty() -> listOf(
                    null,
                    closeButton("Home") { goBack() },
                    textButton("ADD") { openAdd(shownDate) },
                )
                else -> listOf(
                    textButton("EDIT") { viewModel.setEditing(true) },
                    closeButton("Home") { goBack() },
                    textButton("ADD") { openAdd(shownDate) },
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
                    selected = shownDate,
                    marked = loggedDates(data),
                    onSelect = viewModel::selectDate,
                )
            } else if (meals.isEmpty()) {
                EmptyDayMessage("No meals for ${weekdayName(shownDate.dayOfWeek)}")
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
                        meals.forEach { meal ->
                            DayMealRow(
                                meal = meal,
                                editing = editing,
                                onOpen = { openMeal(meal.id, shownDate) },
                                waterUnit = data.waterUnit,
                                onDelete = { confirmDelete(meal) },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openAdd(date: LocalDate) {
        navigateTo({ SavedMealPickerScreen(it, repository, date) })
    }

    private fun openMeal(mealId: String, date: LocalDate) {
        navigateTo({ MealScreen(it, repository, mealId, date) })
    }

    private fun confirmDelete(meal: Meal) {
        navigateTo({
            ConfirmScreen(
                it,
                title = "Delete meal",
                message = "Delete ${meal.name}?",
            )
        }) { confirmed ->
            if (confirmed) viewModel.deleteMeal(meal.id)
        }
    }
}

@Composable
private fun DayMealRow(
    meal: Meal,
    editing: Boolean,
    waterUnit: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.5f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (editing) Modifier.lightClickable(onClick = onOpen) else Modifier,
                )
                .padding(vertical = 0.25f.gridUnitsAsDp()),
        ) {
            LightText(
                text = meal.name,
                variant = LightTextVariant.Copy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            LightText(
                text = mealSummary(meal.amounts, waterUnit),
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (editing) {
            LightIcon(
                icon = LightIcons.TRASH,
                contentDescription = "Delete",
                modifier = Modifier.lightClickable(onClick = onDelete),
            )
        }
    }
}

internal class SavedMealPickerViewModel(
    private val repository: LifestyleRepository,
    private val date: LocalDate,
) : LightViewModel<Unit>() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { repository.ensureLoaded() }
    }

    fun dismissError() {
        _error.value = null
    }

    fun setQuery(raw: String) {
        _query.value = raw.trim().take(MAX_NAME_LENGTH)
    }

    fun addToDay(savedId: String, onAdded: () -> Unit) {
        viewModelScope.launch {
            val added = repository.logSavedMeal(savedId, date.toString())
            if (added) onAdded() else _error.value = "That meal is gone."
        }
    }
}

internal class SavedMealPickerScreen(
    activity: SealedLightActivity,
    private val repository: LifestyleRepository,
    private val date: LocalDate,
) : LightScreen<Unit, SavedMealPickerViewModel>(activity) {

    override val viewModelClass: Class<SavedMealPickerViewModel> = SavedMealPickerViewModel::class.java

    override fun createViewModel() = SavedMealPickerViewModel(repository, date)

    @Composable
    override fun Content() {
        val ready by repository.ready.collectAsState()
        val data by repository.data.collectAsState()
        val query by viewModel.query.collectAsState()
        val localError by viewModel.error.collectAsState()
        val saveError by repository.error.collectAsState()
        val meals = filterSavedMeals(data.savedMeals, query)

        LifestyleScaffold(
            title = "Meals",
            bottomItems = if (ready) {
                listOf(
                    null,
                    closeButton { goBack() },
                    textButton("NEW") {
                        navigateTo({ MealScreen(it, repository, mealId = null, date = date) })
                    },
                )
            } else {
                emptyList()
            },
            errorMessage = localError ?: saveError,
            onDismissError = {
                viewModel.dismissError()
                repository.dismissError()
            },
        ) {
            if (!ready) {
                LoadingBody()
            } else if (data.savedMeals.isEmpty()) {
                EmptyDayMessage("No saved meals")
            } else {
                LightTextField(
                    label = "Search",
                    value = query,
                    placeholder = "Meal name",
                    onClick = {
                        navigateTo({
                            TextPromptScreen(
                                it,
                                title = "Search",
                                initialValue = query,
                                submitLabel = "SAVE",
                                showBackButton = false,
                                centerClose = true,
                            )
                        }) { value -> viewModel.setQuery(value) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                )
                if (meals.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 1.5f.gridUnitsAsDp()),
                    ) {
                        LightText(
                            text = "No matches",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                        )
                    }
                } else {
                    LightLazyScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 1f.gridUnitsAsDp()),
                        uniformItemHeightGridUnits = MEAL_ROW_HEIGHT,
                    ) {
                        items(meals, key = { it.id }) { meal ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MEAL_ROW_HEIGHT.gridUnitsAsDp())
                                    .lightClickable {
                                        viewModel.addToDay(meal.id) { goBack() }
                                    },
                                verticalArrangement = Arrangement.Center,
                            ) {
                                LightText(
                                    text = meal.name,
                                    variant = LightTextVariant.Copy,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                LightText(
                                    text = mealSummary(meal.amounts, data.waterUnit),
                                    variant = LightTextVariant.Detail,
                                    lighten = true,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
