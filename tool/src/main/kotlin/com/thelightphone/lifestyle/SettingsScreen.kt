package com.thelightphone.lifestyle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class SettingsScreen(
    activity: SealedLightActivity,
    private val repository: LifestyleRepository,
) : SimpleLightScreen<Unit>(activity) {

    @Composable
    override fun Content() {
        LifestyleScaffold(
            title = "Settings",
            bottomItems = listOf(closeButton { goBack() }),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                settingsRow("Profile") { navigateTo({ ProfileScreen(it, repository) }) }
                settingsRow("Macros") { navigateTo({ GoalsScreen(it, repository) }) }
                settingsRow("Water") { navigateTo({ WaterScreen(it, repository) }) }
            }
        }
    }
}

@Composable
private fun settingsRow(label: String, onClick: () -> Unit) {
    LightText(
        text = label,
        variant = LightTextVariant.Copy,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 1f.gridUnitsAsDp()),
    )
}

internal class ProfileViewModel(
    private val repository: LifestyleRepository,
) : LightViewModel<Unit>() {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { repository.ensureLoaded() }
    }

    fun dismissError() {
        _error.value = null
    }

    fun setSex(sex: String) {
        viewModelScope.launch { repository.setSex(sex) }
    }

    fun setAge(raw: String) {
        val age = parseCount(raw)
        if (age == null || age > 120) {
            _error.value = "Enter an age from 1 to 120."
            return
        }
        viewModelScope.launch { repository.setAge(age) }
    }

    fun setUnits(units: String) {
        viewModelScope.launch { repository.setUnits(units) }
    }

    fun setHeightCentimeters(raw: String) {
        val centimeters = parseCount(raw)
        if (centimeters == null) {
            _error.value = "Enter a number."
            return
        }
        viewModelScope.launch { repository.setHeight(inchesFromCentimeters(centimeters)) }
    }

    fun setWeight(raw: String) {
        val amount = parseAmount(raw)
        if (amount == null || amount <= 0.0) {
            _error.value = "Enter a number."
            return
        }
        val pounds = if (isMetric(repository.data.value.units)) {
            poundsFromKilograms(amount)
        } else {
            amount
        }
        viewModelScope.launch { repository.setWeight(pounds) }
    }

    fun setActivity(activity: String) {
        viewModelScope.launch { repository.setActivity(activity) }
    }
}

internal class ProfileScreen(
    activity: SealedLightActivity,
    private val repository: LifestyleRepository,
) : LightScreen<Unit, ProfileViewModel>(activity) {

    override val viewModelClass: Class<ProfileViewModel> = ProfileViewModel::class.java

    override fun createViewModel() = ProfileViewModel(repository)

    @Composable
    override fun Content() {
        val ready by repository.ready.collectAsState()
        val data by repository.data.collectAsState()
        val localError by viewModel.error.collectAsState()
        val saveError by repository.error.collectAsState()

        LifestyleScaffold(
            title = "Profile",
            onBack = { goBack() },
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
                        LightTextField(
                            label = "Units",
                            value = if (isMetric(data.units)) "Metric" else "Imperial",
                            placeholder = "",
                            onClick = {
                                navigateTo({
                                    OptionScreen(
                                        it,
                                        "Units",
                                        listOf("Imperial", "Metric"),
                                        if (isMetric(data.units)) "Metric" else "Imperial",
                                    )
                                }) { choice ->
                                    if (choice != null) {
                                        viewModel.setUnits(if (choice == "Metric") "metric" else "imperial")
                                    }
                                }
                            },
                        )
                        LightTextField(
                            label = "Sex",
                            value = sexLabel(data.sex),
                            placeholder = "",
                            onClick = {
                                navigateTo({
                                    OptionScreen(
                                        it,
                                        "Sex",
                                        listOf("Male", "Female"),
                                        sexLabel(data.sex).ifEmpty { null },
                                    )
                                }) { choice ->
                                    if (choice != null) viewModel.setSex(choice)
                                }
                            },
                        )
                        LightTextField(
                            label = "Age",
                            value = data.age?.toString().orEmpty(),
                            placeholder = "",
                            onClick = {
                                navigateTo({
                                    numberPrompt(it, "Age", data.age?.toString().orEmpty(), allowDecimal = false)
                                }) { value -> viewModel.setAge(value) }
                            },
                        )
                        LightTextField(
                            label = "Height",
                            value = data.height?.let { formatHeight(it, isMetric(data.units)) }.orEmpty(),
                            placeholder = "",
                            onClick = {
                                if (isMetric(data.units)) {
                                    val initial = data.height?.let { centimetersFromInches(it).toString() }.orEmpty()
                                    navigateTo({
                                        DialpadScreen(it, "Height", initial, allowDecimal = false)
                                    }) { value -> viewModel.setHeightCentimeters(value) }
                                } else {
                                    navigateTo({ HeightSelectorScreen(it, repository) })
                                }
                            },
                        )
                        LightTextField(
                            label = "Weight",
                            value = data.weight?.let { formatWeight(it, isMetric(data.units)) }.orEmpty(),
                            placeholder = "",
                            onClick = {
                                val metric = isMetric(data.units)
                                val initial = data.weight?.let { pounds ->
                                    if (metric) {
                                        kilogramsFromPounds(pounds).toString()
                                    } else {
                                        kotlin.math.round(pounds).toInt().toString()
                                    }
                                }.orEmpty()
                                navigateTo({
                                    numberPrompt(it, "Weight", initial)
                                }) { value -> viewModel.setWeight(value) }
                            },
                        )
                        LightTextField(
                            label = "Activity level",
                            value = data.activity.orEmpty(),
                            placeholder = "",
                            onClick = {
                                navigateTo({
                                    OptionScreen(it, "Activity level", activityLevels, data.activity)
                                }) { choice ->
                                    if (choice != null) viewModel.setActivity(choice)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

internal class WaterViewModel(
    private val repository: LifestyleRepository,
) : LightViewModel<Unit>() {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { repository.ensureLoaded() }
    }

    fun dismissError() {
        _error.value = null
    }

    fun setUnit(unit: String) {
        viewModelScope.launch { repository.setWaterUnit(unit) }
    }

    fun setGoal(raw: String) {
        val amount = parseAmount(raw)
        if (amount == null || amount <= 0.0) {
            _error.value = "Enter a number."
            return
        }
        viewModelScope.launch { repository.setWaterGoal(amount) }
    }
}

internal class WaterScreen(
    activity: SealedLightActivity,
    private val repository: LifestyleRepository,
) : LightScreen<Unit, WaterViewModel>(activity) {

    override val viewModelClass: Class<WaterViewModel> = WaterViewModel::class.java

    override fun createViewModel() = WaterViewModel(repository)

    @Composable
    override fun Content() {
        val ready by repository.ready.collectAsState()
        val data by repository.data.collectAsState()
        val localError by viewModel.error.collectAsState()
        val saveError by repository.error.collectAsState()
        val goal = waterAmountForDisplay(
            resolvedGoals(data.goals)[Nutrients.WATER] ?: 2500.0,
            data.waterUnit,
        )

        LifestyleScaffold(
            title = "Water",
            onBack = { goBack() },
            errorMessage = localError ?: saveError,
            onDismissError = {
                viewModel.dismissError()
                repository.dismissError()
            },
        ) {
            if (!ready) {
                LoadingBody()
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    LightTextField(
                        label = "Unit",
                        value = waterUnitLabel(data.waterUnit),
                        placeholder = "",
                        onClick = {
                            navigateTo({
                                OptionScreen(
                                    it,
                                    "Unit",
                                    listOf("Milliliter", "US fluid ounce"),
                                    waterUnitLabel(data.waterUnit),
                                )
                            }) { choice ->
                                if (choice != null) {
                                    viewModel.setUnit(if (choice == "US fluid ounce") "oz" else "ml")
                                }
                            }
                        },
                    )
                    LightTextField(
                        label = "Goal (${data.waterUnit})",
                        value = formatAmount(goal),
                        placeholder = "0",
                        onClick = {
                            navigateTo({
                                numberPrompt(it, "Goal (${data.waterUnit})", formatAmount(goal))
                            }) { value -> viewModel.setGoal(value) }
                        },
                    )
                }
            }
        }
    }
}

internal class OptionScreen(
    activity: SealedLightActivity,
    private val title: String,
    private val options: List<String>,
    private val selected: String?,
) : SimpleLightScreen<String>(activity) {

    @Composable
    override fun Content() {
        LifestyleScaffold(
            title = title,
            onBack = { goBack(null) },
        ) {
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp())) {
                    options.forEach { option ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable { goBack(option) }
                                .padding(vertical = 1f.gridUnitsAsDp()),
                        ) {
                            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                                LightText(
                                    text = option,
                                    variant = LightTextVariant.Copy,
                                )
                                Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3f.designVerticalPxToDp())
                                        .background(
                                            if (option == selected) {
                                                LightThemeTokens.colors.content
                                            } else {
                                                LightThemeTokens.colors.background
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun waterUnitLabel(unit: String): String = when (unit) {
    "oz" -> "US fluid ounce"
    else -> "Milliliter"
}

private fun sexLabel(stored: String?): String = when (stored) {
    "M", "Male" -> "Male"
    "F", "Female" -> "Female"
    else -> ""
}

private fun numberPrompt(
    activity: SealedLightActivity,
    title: String,
    initialValue: String,
    allowDecimal: Boolean = true,
) = DialpadScreen(
    activity,
    title = title,
    initialValue = initialValue,
    allowDecimal = allowDecimal,
)
