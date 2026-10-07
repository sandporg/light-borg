package com.thelightphone.lifestyle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBarItem
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens

internal class TextPromptScreen(
    activity: SealedLightActivity,
    private val title: String,
    private val initialValue: String,
    private val initialCaps: Boolean = false,
    private val submitLabel: String = "SUBMIT",
    private val submitIcon: LightIconConfiguration? = null,
    private val showBackButton: Boolean = true,
    private val centerClose: Boolean = false,
) : SimpleLightScreen<String>(activity) {

    @Composable
    override fun Content() {
        val keyboardOptionsFlow = rememberKeyboardOptions()
        val textState = rememberTextFieldState(initialValue)
        val themeColors by LightThemeController.colors.collectAsState()
        val dismiss = { goBack(null) }
        val submit = { goBack(textState.text.toString()) }
        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = title,
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result -> goBack(result.toString()) },
                onBack = dismiss,
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
                submitLabel = submitLabel,
                submitIcon = submitIcon,
                showBackButton = showBackButton,
                singleLine = true,
                initialCaps = initialCaps,
                bottomItems = if (centerClose) {
                    listOf(
                        null,
                        closeButton(onClick = dismiss),
                        promptSubmitButton(submitLabel, submitIcon, submit),
                    )
                } else {
                    null
                },
            )
        }
    }
}

private fun promptSubmitButton(
    label: String,
    icon: LightIconConfiguration?,
    onClick: () -> Unit,
): LightBottomBarItem = when (icon) {
    null -> textButton(label, onClick)
    else -> LightBarButton.LightIcon(
        icon = icon,
        onClick = onClick,
        contentDescription = label,
    )
}
