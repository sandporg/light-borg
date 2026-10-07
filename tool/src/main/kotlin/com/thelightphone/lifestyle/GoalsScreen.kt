package com.thelightphone.lifestyle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class GoalsViewModel(
    private val repository: LifestyleRepository,
) : LightViewModel<Unit>() {
    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { repository.ensureLoaded() }
    }

    fun dismissError() {
        _error.value = null
    }

    fun setEditing(editing: Boolean) {
        _editing.value = editing
    }

    fun setVisible(nutrientId: String, visible: Boolean) {
        viewModelScope.launch { repository.setNutrientVisible(nutrientId, visible) }
    }

    fun setGoal(nutrientId: String, raw: String) {
        val amount = parseAmount(raw)
        if (amount == null) {
            _error.value = "Enter a number."
            return
        }
        viewModelScope.launch { repository.setGoal(nutrientId, amount) }
    }
}

internal class GoalsScreen(
    activity: SealedLightActivity,
    private val repository: LifestyleRepository,
) : LightScreen<Unit, GoalsViewModel>(activity) {

    override val viewModelClass: Class<GoalsViewModel> = GoalsViewModel::class.java

    override fun createViewModel() = GoalsViewModel(repository)

    @Composable
    override fun Content() {
        val ready by repository.ready.collectAsState()
        val data by repository.data.collectAsState()
        val editing by viewModel.editing.collectAsState()
        val localError by viewModel.error.collectAsState()
        val saveError by repository.error.collectAsState()
        val goals = resolvedGoals(data.goals)

        LifestyleScaffold(
            title = "Goals",
            bottomItems = if (!ready) {
                emptyList()
            } else if (editing) {
                listOf(
                    textButton("DONE") { viewModel.setEditing(false) },
                    null,
                )
            } else {
                listOf(
                    textButton("EDIT") { viewModel.setEditing(true) },
                    closeButton { goBack() },
                    null,
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
            } else {
                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp())) {
                        Nutrients.all.forEach { nutrient ->
                            val amount = goals[nutrient.id] ?: nutrient.defaultGoal
                            val visible = !data.isNutrientHidden(nutrient.id)
                            val canHide = nutrient.id != Nutrients.CALORIES
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LightTextField(
                                    label = nutrient.fieldLabel(),
                                    value = formatAmount(amount),
                                    placeholder = "0",
                                    onClick = {
                                        navigateTo({
                                            TextPromptScreen(
                                                it,
                                                title = nutrient.fieldLabel(),
                                                initialValue = formatAmount(amount),
                                                submitLabel = "SAVE",
                                                showBackButton = false,
                                                centerClose = true,
                                            )
                                        }) { value -> viewModel.setGoal(nutrient.id, value) }
                                    },
                                    modifier = Modifier.weight(1f),
                                    lighten = canHide && !visible,
                                )
                                if (editing && canHide) {
                                    LightIcon(
                                        icon = if (visible) {
                                            LightIcons.TOGGLE_STATE_ON
                                        } else {
                                            LightIcons.TOGGLE_STATE_OFF
                                        },
                                        contentDescription = if (visible) "Shown" else "Hidden",
                                        modifier = Modifier.lightClickable {
                                            viewModel.setVisible(nutrient.id, !visible)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
