package com.thelightphone.lifestyle

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val USER_EXERCISE_SOURCE = "user"
internal const val LIFESTYLE_STORE_FILE = "lifestyle.json"

internal val lifestyleJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = true
}

@Serializable
internal data class LifestyleData(
    val version: Int = 1,
    val goals: Map<String, Double> = emptyMap(),
    val meals: List<Meal> = emptyList(),
    val savedMeals: List<SavedMeal> = emptyList(),
    val userExercises: List<UserExercise> = emptyList(),
    val groups: List<ExerciseGroup> = emptyList(),
    val checks: List<ExerciseCheck> = emptyList(),
    val hiddenNutrients: List<String> = emptyList(),
)

@Serializable
internal data class Meal(
    val id: String,
    val name: String,
    val date: String,
    val amounts: Map<String, Double> = emptyMap(),
)

@Serializable
internal data class SavedMeal(
    val id: String,
    val name: String,
    val amounts: Map<String, Double> = emptyMap(),
)

@Serializable
internal data class UserExercise(
    val id: String,
    val name: String,
    val source: String = USER_EXERCISE_SOURCE,
)

@Serializable
internal data class ExerciseGroup(
    val id: String,
    val weekday: Int,
    val name: String,
    val exercises: List<PlannedExercise> = emptyList(),
)

@Serializable
internal data class PlannedExercise(
    val id: String,
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
)

@Serializable
internal data class ExerciseCheck(
    val date: String,
    val plannedExerciseId: String,
)

internal fun newId(): String = UUID.randomUUID().toString()

internal fun newUserExerciseId(): String = "user-${UUID.randomUUID()}"

internal fun encodeLifestyle(data: LifestyleData): String = lifestyleJson.encodeToString(data)

internal fun decodeLifestyle(text: String): LifestyleData = lifestyleJson.decodeFromString(text)

internal fun dateOnWeek(anchor: LocalDate, weekday: DayOfWeek): LocalDate {
    val sunday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val daysFromSunday = if (weekday == DayOfWeek.SUNDAY) 0L else weekday.value.toLong()
    return sunday.plusDays(daysFromSunday)
}

internal fun weekdayName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Monday"
    DayOfWeek.TUESDAY -> "Tuesday"
    DayOfWeek.WEDNESDAY -> "Wednesday"
    DayOfWeek.THURSDAY -> "Thursday"
    DayOfWeek.FRIDAY -> "Friday"
    DayOfWeek.SATURDAY -> "Saturday"
    DayOfWeek.SUNDAY -> "Sunday"
}

internal fun shortDate(date: LocalDate): String {
    val month = date.month.getDisplayName(TextStyle.SHORT, Locale.US)
    return "$month ${date.dayOfMonth}"
}

internal fun compactDateTitle(date: LocalDate): String {
    val weekday = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }
    val month = date.month.getDisplayName(TextStyle.SHORT, Locale.US)
    return "$weekday $month ${date.dayOfMonth}"
}

internal enum class HistoryKind {
    MEALS,
    EXERCISE,
}

internal fun historyDates(data: LifestyleData, kind: HistoryKind): Set<String> = when (kind) {
    HistoryKind.MEALS -> data.meals.map { it.date }.toSet()
    HistoryKind.EXERCISE -> data.checks.map { it.date }.toSet()
}

internal fun loggedDates(data: LifestyleData): Set<String> =
    historyDates(data, HistoryKind.MEALS) + historyDates(data, HistoryKind.EXERCISE)

internal fun monthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value % 7
    val cells = MutableList<LocalDate?>(lead) { null }
    for (day in 1..month.lengthOfMonth()) {
        cells += month.atDay(day)
    }
    while (cells.size % 7 != 0) cells += null
    return cells
}

internal fun LifestyleData.isNutrientHidden(id: String): Boolean =
    id != Nutrients.CALORIES && id in hiddenNutrients

internal fun LifestyleData.withNutrientVisible(id: String, visible: Boolean): LifestyleData {
    if (id == Nutrients.CALORIES) {
        return copy(hiddenNutrients = hiddenNutrients.filterNot { it == Nutrients.CALORIES })
    }
    val hidden = hiddenNutrients.toMutableSet()
    if (visible) hidden.remove(id) else hidden.add(id)
    return copy(hiddenNutrients = hidden.sorted())
}

internal val daysOfWeek: List<DayOfWeek> = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)

internal fun weekdayShortLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Mo"
    DayOfWeek.TUESDAY -> "Tu"
    DayOfWeek.WEDNESDAY -> "We"
    DayOfWeek.THURSDAY -> "Th"
    DayOfWeek.FRIDAY -> "Fr"
    DayOfWeek.SATURDAY -> "Sa"
    DayOfWeek.SUNDAY -> "Su"
}

internal fun LifestyleData.upsertMeal(meal: Meal): LifestyleData {
    val index = meals.indexOfFirst { it.id == meal.id }
    if (index == -1) return copy(meals = meals + meal)
    val next = meals.toMutableList()
    next[index] = meal
    return copy(meals = next)
}

internal fun LifestyleData.deleteMeal(mealId: String): LifestyleData =
    copy(meals = meals.filterNot { it.id == mealId })

internal fun LifestyleData.upsertSavedMeal(meal: SavedMeal): LifestyleData {
    val index = savedMeals.indexOfFirst { it.id == meal.id }
    if (index == -1) return copy(savedMeals = savedMeals + meal)
    val next = savedMeals.toMutableList()
    next[index] = meal
    return copy(savedMeals = next)
}

internal fun LifestyleData.keepMeal(name: String, amounts: Map<String, Double>): LifestyleData {
    val existing = savedMeals.find { it.name.equals(name, ignoreCase = true) }
    return upsertSavedMeal(
        SavedMeal(
            id = existing?.id ?: newId(),
            name = name,
            amounts = amounts,
        ),
    )
}

internal fun LifestyleData.deleteSavedMeal(savedId: String): LifestyleData =
    copy(savedMeals = savedMeals.filterNot { it.id == savedId })

internal fun LifestyleData.logSavedMeal(savedId: String, date: String): LifestyleData {
    val saved = savedMeals.find { it.id == savedId } ?: return this
    return upsertMeal(
        Meal(
            id = newId(),
            name = saved.name,
            date = date,
            amounts = saved.amounts,
        ),
    )
}

internal fun LifestyleData.withGoal(nutrientId: String, amount: Double): LifestyleData =
    copy(goals = goals + (nutrientId to amount))

internal fun LifestyleData.groupsOn(weekday: DayOfWeek): List<ExerciseGroup> =
    groups.filter { it.weekday == weekday.value }

internal fun LifestyleData.checkedIds(date: String): Set<String> =
    checks.filter { it.date == date }.map { it.plannedExerciseId }.toSet()

internal fun LifestyleData.addGroup(weekday: DayOfWeek, name: String, id: String = newId()): LifestyleData =
    copy(
        groups = groups + ExerciseGroup(
            id = id,
            weekday = weekday.value,
            name = name,
        ),
    )

internal fun LifestyleData.renameGroup(groupId: String, name: String): LifestyleData =
    copy(groups = groups.map { group -> if (group.id == groupId) group.copy(name = name) else group })

internal fun LifestyleData.deleteGroup(groupId: String): LifestyleData {
    val plannedIds = groups.find { it.id == groupId }?.exercises?.map { it.id }?.toSet().orEmpty()
    return copy(
        groups = groups.filterNot { it.id == groupId },
        checks = checks.filterNot { it.plannedExerciseId in plannedIds },
    )
}

internal fun LifestyleData.addPlanned(groupId: String, planned: PlannedExercise): LifestyleData =
    copy(
        groups = groups.map { group ->
            if (group.id == groupId) group.copy(exercises = group.exercises + planned) else group
        },
    )

internal fun LifestyleData.updatePlanned(plannedId: String, sets: Int, reps: Int): LifestyleData =
    copy(
        groups = groups.map { group ->
            group.copy(
                exercises = group.exercises.map { planned ->
                    if (planned.id == plannedId) planned.copy(sets = sets, reps = reps) else planned
                },
            )
        },
    )

internal fun LifestyleData.deletePlanned(plannedId: String): LifestyleData =
    copy(
        groups = groups.map { group ->
            group.copy(exercises = group.exercises.filterNot { it.id == plannedId })
        },
        checks = checks.filterNot { it.plannedExerciseId == plannedId },
    )

internal fun LifestyleData.toggleCheck(date: String, plannedId: String): LifestyleData {
    val checked = checks.any { it.date == date && it.plannedExerciseId == plannedId }
    val next = if (checked) {
        checks.filterNot { it.date == date && it.plannedExerciseId == plannedId }
    } else {
        checks + ExerciseCheck(date = date, plannedExerciseId = plannedId)
    }
    return copy(checks = next)
}
