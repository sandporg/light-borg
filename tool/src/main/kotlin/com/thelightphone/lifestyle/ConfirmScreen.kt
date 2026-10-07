package com.thelightphone.lifestyle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp

internal class ConfirmScreen(
    activity: SealedLightActivity,
    private val title: String,
    private val message: String,
    private val confirmLabel: String = "DELETE",
) : SimpleLightScreen<Boolean>(activity) {

    @Composable
    override fun Content() {
        LifestyleScaffold(
            title = title,
            bottomItems = listOf(
                textButton("CANCEL") { goBack(null) },
                closeButton { goBack(null) },
                textButton(confirmLabel) { goBack(true) },
            ),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
                contentAlignment = Alignment.Center,
            ) {
                LightText(
                    text = message,
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                )
            }
        }
    }
}
