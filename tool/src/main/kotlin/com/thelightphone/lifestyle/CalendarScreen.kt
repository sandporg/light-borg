package com.thelightphone.lifestyle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun ColumnScope.MonthCalendar(
    month: YearMonth,
    selected: LocalDate,
    marked: Set<String>,
    onSelect: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp()),
    ) {
        daysOfWeek.forEach { day ->
            LightText(
                text = weekdayShortLabel(day),
                variant = LightTextVariant.Detail,
                align = TextAlign.Center,
                lighten = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
    ) {
        monthCells(month).chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { date ->
                    CalendarDay(
                        date = date,
                        selected = date == selected,
                        marked = date != null && date.toString() in marked,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.CalendarDay(
    date: LocalDate?,
    selected: Boolean,
    marked: Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .then(
                if (date == null) {
                    Modifier
                } else {
                    Modifier.lightClickable { onSelect(date) }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LightText(
                    text = date.dayOfMonth.toString(),
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                    underline = selected,
                    lighten = !marked && !selected,
                )
                Spacer(modifier = Modifier.height(0.2f.gridUnitsAsDp()))
                Box(
                    modifier = Modifier
                        .size(0.28f.gridUnitsAsDp())
                        .background(
                            if (marked) LightThemeTokens.colors.content else LightThemeTokens.colors.background,
                            CircleShape,
                        ),
                )
            }
        }
    }
}
