package com.thelightphone.lifestyle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightBottomBarItem
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightProgressBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarButton
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp

@Composable
internal fun LifestyleScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    leftButton: LightTopBarButton? = null,
    rightButton: LightTopBarButton? = null,
    onTitleClick: (() -> Unit)? = null,
    bottomItems: List<LightBottomBarItem?> = emptyList(),
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val themeColors by LightThemeController.colors.collectAsState()
    LightTheme(colors = themeColors) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = leftButton ?: onBack?.let { backButton(it) },
                    center = LightTopBarCenter.Text(title, onClick = onTitleClick),
                    rightButton = rightButton,
                    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                )
                content()
                if (bottomItems.isNotEmpty()) {
                    LightBottomBar(items = bottomItems)
                }
            }
            if (errorMessage != null) {
                LightFullscreenModal(
                    message = errorMessage,
                    onClose = onDismissError,
                )
            }
        }
    }
}

@Composable
internal fun ColumnScope.LoadingBody() {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = "Loading…",
            variant = LightTextVariant.Copy,
            lighten = true,
        )
    }
}

@Composable
internal fun ColumnScope.EmptyDayMessage(text: String) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = text,
            variant = LightTextVariant.Copy,
            align = TextAlign.Center,
            lighten = true,
        )
    }
}

@Composable
internal fun CalorieHero(current: Double, goal: Double, unit: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 0.5f.gridUnitsAsDp(),
                bottom = 1.5f.gridUnitsAsDp(),
            ),
    ) {
        LightText(
            text = formatAmount(current),
            variant = LightTextVariant.Subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LightText(
            text = "of ${formatAmount(goal)} $unit",
            variant = LightTextVariant.Detail,
            lighten = true,
        )
        Spacer(modifier = Modifier.height(0.75f.gridUnitsAsDp()))
        LightProgressBar(
            colors = LightThemeTokens.colors,
            progress = progressFraction(current, goal),
        )
    }
}

@Composable
internal fun NutrientGoalRow(
    label: String,
    current: Double,
    goal: Double,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 1.25f.gridUnitsAsDp()),
    ) {
        LightText(
            text = label,
            variant = LightTextVariant.Copy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LightText(
            text = "${formatAmount(current)} / ${formatAmount(goal)} $unit",
            variant = LightTextVariant.Detail,
            lighten = true,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
        LightProgressBar(
            colors = LightThemeTokens.colors,
            progress = progressFraction(current, goal),
        )
    }
}

@Composable
internal fun MealAmountFields(
    name: String,
    amounts: Map<String, Double>,
    onEditName: () -> Unit,
    onEditAmount: (Nutrient) -> Unit,
    nutrients: List<Nutrient> = Nutrients.all,
) {
    LightTextField(
        label = "Name",
        value = name,
        placeholder = "Meal name",
        onClick = onEditName,
    )
    nutrients.forEach { nutrient ->
        LightTextField(
            label = nutrient.fieldLabel(),
            value = formatAmount(amounts[nutrient.id] ?: 0.0),
            placeholder = "0",
            onClick = { onEditAmount(nutrient) },
        )
    }
}

internal fun backButton(onClick: () -> Unit): LightTopBarButton =
    LightBarButton.LightIcon(
        icon = LightIcons.BACK,
        onClick = onClick,
        contentDescription = "Back",
    )

internal fun textButton(text: String, onClick: () -> Unit): LightBarButton.Text =
    LightBarButton.Text(
        text = text,
        onClick = onClick,
        contentDescription = text,
    )

internal fun closeButton(
    contentDescription: String = "Back",
    onClick: () -> Unit,
): LightBarButton.LightIcon =
    LightBarButton.LightIcon(
        icon = LightIcons.CLOSE,
        onClick = onClick,
        contentDescription = contentDescription,
    )

internal fun chevronButton(previous: Boolean, onClick: () -> Unit): LightBarButton.LightIcon =
    LightBarButton.LightIcon(
        icon = if (previous) LightIcons.BACK else LightIcons.ARROW_RIGHT,
        onClick = onClick,
        contentDescription = if (previous) "Previous" else "Next",
    )
