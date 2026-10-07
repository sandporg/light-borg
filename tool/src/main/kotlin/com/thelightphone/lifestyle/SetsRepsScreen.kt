package com.thelightphone.lifestyle

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SetsReps(
    val sets: Int,
    val reps: Int,
)

internal class SetsRepsViewModel(
    val exerciseName: String,
    sets: Int,
    reps: Int,
) : LightViewModel<SetsReps>() {
    private val _sets = MutableStateFlow(sets.coerceIn(1, MAX_COUNT).toString())
    val sets: StateFlow<String> = _sets.asStateFlow()

    private val _reps = MutableStateFlow(reps.coerceIn(1, MAX_COUNT).toString())
    val reps: StateFlow<String> = _reps.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun dismissError() {
        _error.value = null
    }

    fun setSets(raw: String) {
        val parsed = parseCount(raw)
        if (parsed == null) {
            _error.value = "Enter sets from 1 to 999."
        } else {
            _sets.value = parsed.toString()
        }
    }

    fun setReps(raw: String) {
        val parsed = parseCount(raw)
        if (parsed == null) {
            _error.value = "Enter reps from 1 to 999."
        } else {
            _reps.value = parsed.toString()
        }
    }

    fun result(): SetsReps? {
        val sets = parseCount(_sets.value) ?: return null
        val reps = parseCount(_reps.value) ?: return null
        return SetsReps(sets, reps)
    }
}

internal class SetsRepsScreen(
    activity: SealedLightActivity,
    private val exerciseName: String,
    private val initialSets: Int,
    private val initialReps: Int,
) : LightScreen<SetsReps, SetsRepsViewModel>(activity) {

    override val viewModelClass: Class<SetsRepsViewModel> = SetsRepsViewModel::class.java

    override fun createViewModel() = SetsRepsViewModel(exerciseName, initialSets, initialReps)

    @Composable
    override fun Content() {
        val sets by viewModel.sets.collectAsState()
        val reps by viewModel.reps.collectAsState()
        val error by viewModel.error.collectAsState()

        LifestyleScaffold(
            title = "Sets",
            bottomItems = listOf(
                null,
                closeButton { goBack(null) },
                textButton("SAVE") {
                    val result = viewModel.result()
                    if (result == null) {
                        viewModel.setSets(sets)
                    } else {
                        goBack(result)
                    }
                },
            ),
            errorMessage = error,
            onDismissError = viewModel::dismissError,
        ) {
            LightText(
                text = viewModel.exerciseName,
                variant = LightTextVariant.Copy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            )
            LightTextField(
                label = "Sets",
                value = sets,
                placeholder = "3",
                onClick = {
                    navigateTo({
                        TextPromptScreen(
                            it,
                            title = "Sets",
                            initialValue = sets,
                            submitLabel = "SAVE",
                            showBackButton = false,
                            centerClose = true,
                        )
                    }) { value -> viewModel.setSets(value) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            )
            LightTextField(
                label = "Reps",
                value = reps,
                placeholder = "10",
                onClick = {
                    navigateTo({
                        TextPromptScreen(
                            it,
                            title = "Reps",
                            initialValue = reps,
                            submitLabel = "SAVE",
                            showBackButton = false,
                            centerClose = true,
                        )
                    }) { value -> viewModel.setReps(value) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
