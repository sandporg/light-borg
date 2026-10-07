package com.thelightphone.lifestyle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class PickedExercise(
    val id: String,
    val name: String,
)

private const val EXERCISE_ROW_HEIGHT = 5.5f

internal class ExercisePickerViewModel(
    private val repository: LifestyleRepository,
) : LightViewModel<PickedExercise>() {
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

    fun createExercise(raw: String, onCreated: (PickedExercise) -> Unit) {
        when (val name = validateName(raw)) {
            is NameValidation.Invalid -> _error.value = name.message
            is NameValidation.Ok -> viewModelScope.launch {
                val created = repository.addUserExercise(name.name)
                if (created != null) {
                    onCreated(PickedExercise(created.id, created.name))
                }
            }
        }
    }
}

internal class ExercisePickerScreen(
    activity: SealedLightActivity,
    private val repository: LifestyleRepository,
) : LightScreen<PickedExercise, ExercisePickerViewModel>(activity) {

    override val viewModelClass: Class<ExercisePickerViewModel> = ExercisePickerViewModel::class.java

    override fun createViewModel() = ExercisePickerViewModel(repository)

    @Composable
    override fun Content() {
        val ready by repository.ready.collectAsState()
        val exercises by repository.exercises.collectAsState()
        val query by viewModel.query.collectAsState()
        val localError by viewModel.error.collectAsState()
        val saveError by repository.error.collectAsState()
        val results = filterExercises(exercises, query)

        LifestyleScaffold(
            title = "Exercises",
            bottomItems = if (ready) {
                listOf(
                    null,
                    closeButton { goBack(null) },
                    textButton("ADD") {
                        navigateTo({
                            TextPromptScreen(
                                it,
                                title = "Add exercise",
                                initialValue = viewModel.query.value,
                                initialCaps = viewModel.query.value.isBlank(),
                                submitLabel = "SAVE",
                                showBackButton = false,
                                centerClose = true,
                            )
                        }) { raw ->
                            viewModel.createExercise(raw) { picked -> goBack(picked) }
                        }
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
            } else {
                LightTextField(
                    label = "Search",
                    value = query,
                    placeholder = "Name, muscle, or equipment",
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
                if (results.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 1.5f.gridUnitsAsDp()),
                    ) {
                        LightText(
                            text = if (query.isBlank()) "No exercises" else "No matches",
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
                        uniformItemHeightGridUnits = EXERCISE_ROW_HEIGHT,
                    ) {
                        items(results, key = { it.id }) { exercise ->
                            ExerciseResultRow(
                                exercise = exercise,
                                onClick = { goBack(PickedExercise(exercise.id, exercise.name)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseResultRow(
    exercise: Exercise,
    onClick: () -> Unit,
) {
    val detail = exercise.detailLine()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(EXERCISE_ROW_HEIGHT.gridUnitsAsDp())
            .lightClickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
    ) {
        LightText(
            text = exercise.name,
            variant = LightTextVariant.Copy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (detail.isNotEmpty()) {
            LightText(
                text = detail,
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
