package com.thelightphone.lifestyle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DEFAULT_INCHES = 68.0
private const val MIN_INCHES = 36
private const val MAX_INCHES = 144
private const val MIN_CENTIMETERS = 90
private const val MAX_CENTIMETERS = 250

internal class HeightSelectorViewModel(
    private val repository: LifestyleRepository,
) : LightViewModel<Unit>() {
    private val _display = MutableStateFlow("")
    val display: StateFlow<String> = _display.asStateFlow()

    private var inches = DEFAULT_INCHES
    private var metric = false
    private var ready = false

    init {
        viewModelScope.launch {
            repository.ensureLoaded()
            val data = repository.data.value
            metric = isMetric(data.units)
            inches = data.height ?: DEFAULT_INCHES
            ready = true
            publish()
        }
    }

    fun decrease() = step(-1)

    fun increase() = step(1)

    private fun step(delta: Int) {
        if (!ready) return
        if (metric) {
            val next = (centimetersFromInches(inches) + delta).coerceIn(MIN_CENTIMETERS, MAX_CENTIMETERS)
            inches = inchesFromCentimeters(next)
        } else {
            inches = (kotlin.math.round(inches).toInt() + delta).coerceIn(MIN_INCHES, MAX_INCHES).toDouble()
        }
        publish()
        viewModelScope.launch { repository.setHeight(inches) }
    }

    private fun publish() {
        _display.value = formatHeight(inches, metric)
    }
}

internal class HeightSelectorScreen(
    activity: SealedLightActivity,
    private val repository: LifestyleRepository,
) : LightScreen<Unit, HeightSelectorViewModel>(activity) {

    override val viewModelClass: Class<HeightSelectorViewModel> = HeightSelectorViewModel::class.java

    override fun createViewModel() = HeightSelectorViewModel(repository)

    @Composable
    override fun Content() {
        val display by viewModel.display.collectAsState()
        ValueStepper(
            title = "Height",
            value = display,
            onBack = { goBack() },
            onDecrease = viewModel::decrease,
            onIncrease = viewModel::increase,
        )
    }
}

@Composable
private fun ValueStepper(
    title: String,
    value: String,
    onBack: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    LifestyleScaffold(
        title = title,
        onBack = onBack,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = value,
                variant = LightTextVariant.Subtitle,
                align = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1.5f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                text = "−",
                variant = LightTextVariant.Subtitle,
                modifier = Modifier
                    .lightClickable(onClick = onDecrease)
                    .padding(1f.gridUnitsAsDp()),
            )
            Box(modifier = Modifier.weight(1f))
            LightText(
                text = "+",
                variant = LightTextVariant.Subtitle,
                modifier = Modifier
                    .lightClickable(onClick = onIncrease)
                    .padding(1f.gridUnitsAsDp()),
            )
        }
    }
}
