package com.thelightphone.lifestyle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

private const val DELETE = "delete"
private const val MAX_ENTRY_LENGTH = 8

internal class DialpadScreen(
    activity: SealedLightActivity,
    private val title: String,
    private val initialValue: String,
    private val allowDecimal: Boolean = true,
) : SimpleLightScreen<String>(activity) {

    @Composable
    override fun Content() {
        var entry by rememberSaveable { mutableStateOf(initialValue) }
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(if (allowDecimal) "." else "", "0", DELETE),
        )
        LifestyleScaffold(
            title = title,
            bottomItems = listOf(
                null,
                closeButton { goBack(null) },
                textButton("SAVE") { goBack(entry) },
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
            ) {
                LightText(
                    text = entry,
                    variant = LightTextVariant.Heading,
                    align = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3f.designVerticalPxToDp())
                        .background(LightThemeTokens.colors.content),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        row.forEach { key ->
                            DialKey(key) {
                                entry = nextEntry(entry, key, allowDecimal)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DialKey(key: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(if (key.isEmpty()) Modifier else Modifier.lightClickable(onClick = onClick)),
        contentAlignment = Alignment.Center,
    ) {
        when (key) {
            "" -> Unit
            DELETE -> LightIcon(
                icon = LightIcons.BACK,
                contentDescription = "Delete",
            )
            else -> LightText(
                text = key,
                variant = LightTextVariant.Heading,
                align = TextAlign.Center,
            )
        }
    }
}

private fun nextEntry(current: String, key: String, allowDecimal: Boolean): String {
    if (key == DELETE) return current.dropLast(1)
    if (key == ".") {
        if (!allowDecimal || current.contains('.')) return current
        return if (current.isEmpty()) "0." else current + key
    }
    if (current.length >= MAX_ENTRY_LENGTH) return current
    if (current == "0") return key
    return current + key
}
