package com.thelightphone.lifestyle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.gridUnitsAsDp
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class MealDraft(
    val id: String,
    val isNew: Boolean,
    val name: String,
    val date: String,
    val amounts: Map<String, Double>,
)

internal class MealViewModel(
    private val repository: LifestyleRepository,
    private val mealId: String?,
    private val date: LocalDate,
) : LightViewModel<Unit>() {
    private val _draft = MutableStateFlow<MealDraft?>(null)
    val draft: StateFlow<MealDraft?> = _draft.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _close = MutableStateFlow(false)
    val close: StateFlow<Boolean> = _close.asStateFlow()

    private var missing = false
    private var saving = false

    init {
        viewModelScope.launch {
            repository.ensureLoaded()
            if (mealId == null) {
                _draft.value = MealDraft(
                    id = newId(),
                    isNew = true,
                    name = "",
                    date = date.toString(),
                    amounts = emptyMap(),
                )
            } else {
                val meal = repository.data.value.meals.find { it.id == mealId }
                if (meal == null) {
                    missing = true
                    _error.value = "That meal is gone."
                } else {
                    _draft.value = MealDraft(
                        id = meal.id,
                        isNew = false,
                        name = meal.name,
                        date = meal.date,
                        amounts = meal.amounts,
                    )
                }
            }
        }
    }

    fun dismissError() {
        _error.value = null
        if (missing) _close.value = true
    }

    fun setName(raw: String) {
        val draft = _draft.value ?: return
        when (val name = validateName(raw)) {
            is NameValidation.Invalid -> _error.value = name.message
            is NameValidation.Ok -> _draft.value = draft.copy(name = name.name)
        }
    }

    fun setAmount(nutrientId: String, raw: String) {
        val draft = _draft.value ?: return
        val amount = parseAmount(raw)
        if (amount == null) {
            _error.value = "Enter a number."
            return
        }
        _draft.value = draft.copy(amounts = draft.amounts.withAmount(nutrientId, amount))
    }

    fun saveTemplate() {
        val draft = _draft.value ?: return
        if (saving) return
        when (val name = validateName(draft.name)) {
            is NameValidation.Invalid -> _error.value = name.message
            is NameValidation.Ok -> viewModelScope.launch {
                val kept = repository.keepMeal(name.name, sanitizeAmounts(draft.amounts))
                if (kept) {
                    val current = _draft.value
                    if (current?.id == draft.id) {
                        _draft.value = current.copy(name = name.name)
                    }
                    _error.value = "Saved."
                }
            }
        }
    }

    fun addToDay(onAdded: () -> Unit) {
        val draft = _draft.value ?: return
        if (saving) return
        when (val name = validateName(draft.name)) {
            is NameValidation.Invalid -> _error.value = name.message
            is NameValidation.Ok -> {
                saving = true
                viewModelScope.launch {
                    val saved = repository.saveMeal(
                        Meal(
                            id = draft.id,
                            name = name.name,
                            date = draft.date,
                            amounts = sanitizeAmounts(draft.amounts),
                        ),
                    )
                    if (saved) onAdded() else saving = false
                }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val draft = _draft.value ?: return
        if (draft.isNew || saving) return
        saving = true
        viewModelScope.launch {
            val deleted = repository.deleteMeal(draft.id)
            if (deleted) onDeleted() else saving = false
        }
    }
}

internal class MealScreen(
    activity: SealedLightActivity,
    private val repository: LifestyleRepository,
    private val mealId: String?,
    private val date: LocalDate,
) : LightScreen<Unit, MealViewModel>(activity) {

    override val viewModelClass: Class<MealViewModel> = MealViewModel::class.java

    override fun createViewModel() = MealViewModel(repository, mealId, date)

    @Composable
    override fun Content() {
        val draft by viewModel.draft.collectAsState()
        val localError by viewModel.error.collectAsState()
        val saveError by repository.error.collectAsState()
        val close by viewModel.close.collectAsState()
        val current = draft

        LaunchedEffect(close) {
            if (close) goBack()
        }

        LifestyleScaffold(
            title = if (mealId == null) "New meal" else "Edit meal",
            bottomItems = when {
                current == null -> emptyList()
                current.isNew -> listOf(
                    textButton("SAVE") { viewModel.saveTemplate() },
                    closeButton { goBack() },
                    textButton("ONCE") { viewModel.addToDay { goBack() } },
                )
                else -> listOf(
                    textButton("DELETE") {
                        navigateTo({
                            ConfirmScreen(
                                it,
                                title = "Delete meal",
                                message = "Delete ${current.name}?",
                            )
                        }) { confirmed ->
                            if (confirmed) viewModel.delete { goBack() }
                        }
                    },
                    closeButton { goBack() },
                    textButton("SAVE") { viewModel.addToDay { goBack() } },
                )
            },
            errorMessage = localError ?: saveError,
            onDismissError = {
                viewModel.dismissError()
                repository.dismissError()
            },
        ) {
            if (current == null) {
                LoadingBody()
            } else {
                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp())) {
                        MealAmountFields(
                            name = current.name,
                            amounts = current.amounts,
                            onEditName = {
                                navigateTo({
                                    TextPromptScreen(
                                        it,
                                        title = "Name",
                                        initialValue = current.name,
                                        initialCaps = current.name.isBlank(),
                                        submitLabel = "SAVE",
                                        showBackButton = false,
                                        centerClose = true,
                                    )
                                }) { value -> viewModel.setName(value) }
                            },
                            onEditAmount = { nutrient ->
                                val amount = current.amounts[nutrient.id] ?: 0.0
                                navigateTo({
                                    TextPromptScreen(
                                        it,
                                        title = nutrient.fieldLabel(),
                                        initialValue = formatAmount(amount),
                                        submitLabel = "SAVE",
                                        showBackButton = false,
                                        centerClose = true,
                                    )
                                }) { value -> viewModel.setAmount(nutrient.id, value) }
                            },
                        )
                    }
                }
            }
        }
    }
}
