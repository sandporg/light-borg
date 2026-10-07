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
    val weekdayPlans: List<WeekdayPlan> = emptyList(),
    val hiddenNutrients: List<String> = emptyList(),
    val sex: String? = null,
    val age: Int? = null,
    val height: Double? = null,
    val weight: Double? = null,
    val activity: String? = null,
    val waterUnit: String = "ml",
    val units: String = "imperial",
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
    val weekStart: String = "",
    val lineageId: String = "",
)

@Serializable
internal data class WeekdayPlan(
    val weekday: Int,
    val weekStart: String,
    val groupIds: List<String> = emptyList(),
)

@Serializable
internal data class PlannedExercise(
    val id: String,
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val weight: Double = 0.0,
)

@Serializable
internal data class ExerciseCheck(
    val date: String,
    val plannedExerciseId: String,
)

internal fun newId(): String = UUID.randomUUID().toString()

internal fun newUserExerciseId(): String = "user-${UUID.randomUUID()}"

internal fun encodeLifestyle(data: LifestyleData): String = lifestyleJson.encodeToString(data)

internal const val LIFESTYLE_DATA_VERSION = 2

internal fun decodeLifestyle(text: String): LifestyleData =
    lifestyleJson.decodeFromString<LifestyleData>(text).migrated()

internal fun LifestyleData.migrated(): LifestyleData {
    if (version >= LIFESTYLE_DATA_VERSION) return this
    val hidden = (hiddenNutrients + Nutrients.hiddenByDefault)
        .filter { it != Nutrients.CALORIES }
        .distinct()
        .sorted()
    return copy(version = LIFESTYLE_DATA_VERSION, hiddenNutrients = hidden)
}

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

internal fun monthYearTitle(month: YearMonth): String {
    val name = month.month.getDisplayName(TextStyle.FULL, Locale.US)
    return "$name ${month.year}"
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

internal fun isMetric(units: String): Boolean = units == "metric"

internal fun formatHeight(inches: Double, metric: Boolean): String {
    if (metric) {
        val centimeters = kotlin.math.round(inches * 2.54).toInt()
        return "$centimeters cm"
    }
    val total = kotlin.math.round(inches).toInt().coerceAtLeast(0)
    val feet = total / 12
    val inch = total % 12
    return "$feet' $inch\""
}

internal fun formatWeight(pounds: Double, metric: Boolean): String {
    if (metric) {
        val kilograms = kotlin.math.round(pounds * 0.45359237).toInt()
        return "$kilograms kg"
    }
    return "${kotlin.math.round(pounds).toInt()} lb"
}

internal fun inchesFromCentimeters(centimeters: Int): Double = centimeters / 2.54

internal fun centimetersFromInches(inches: Double): Int = kotlin.math.round(inches * 2.54).toInt()

internal fun poundsFromKilograms(kilograms: Double): Double = kilograms / 0.45359237

internal fun kilogramsFromPounds(pounds: Double): Int = kotlin.math.round(pounds * 0.45359237).toInt()

internal val activityLevels: List<String> = listOf(
    "Sedentary",
    "Light",
    "Moderate",
    "Active",
    "Very active",
)

internal fun LifestyleData.isNutrientHidden(id: String): Boolean =
    id != Nutrients.CALORIES && id != Nutrients.WATER && id in hiddenNutrients

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

internal fun weekStart(date: LocalDate): String = dateOnWeek(date, DayOfWeek.SUNDAY).toString()

internal fun exerciseDetail(sets: Int, reps: Int, weightPounds: Double, metric: Boolean): String {
    val base = "$sets × $reps"
    if (weightPounds <= 0.0) return base
    val amount = if (metric) weightPounds * 0.45359237 else weightPounds
    val unit = if (metric) "kg" else "lb"
    return "$base × ${formatAmount(amount)} $unit"
}

internal data class WeekFork(
    val data: LifestyleData,
    val groupIds: Map<String, String> = emptyMap(),
    val plannedIds: Map<String, String> = emptyMap(),
)

internal fun LifestyleData.groupsFor(date: LocalDate): List<ExerciseGroup> {
    val week = weekStart(date)
    val weekday = date.dayOfWeek.value
    val plan = weekdayPlans
        .filter { it.weekday == weekday && it.weekStart <= week }
        .maxByOrNull { it.weekStart }
    if (plan != null) {
        return plan.groupIds.mapNotNull { id -> groups.find { it.id == id } }
    }
    return groups.filter { it.weekday == weekday && it.weekStart.isEmpty() }
}

internal fun LifestyleData.editableWeek(date: LocalDate): WeekFork {
    val week = weekStart(date)
    val weekday = date.dayOfWeek.value
    if (weekdayPlans.any { it.weekday == weekday && it.weekStart == week }) {
        return WeekFork(this)
    }
    val visible = groupsFor(date)
    if (visible.isEmpty() || visible.all { it.weekStart == week }) {
        val ids = visible.map { it.id }
        val plans = if (ids.isEmpty() || weekdayPlans.any { it.weekday == weekday && it.weekStart == week }) {
            weekdayPlans
        } else {
            weekdayPlans + WeekdayPlan(weekday, week, ids)
        }
        return WeekFork(copy(weekdayPlans = plans))
    }
    val groupIds = mutableMapOf<String, String>()
    val plannedIds = mutableMapOf<String, String>()
    val copies = visible.map { group ->
        val newGroupId = newId()
        groupIds[group.id] = newGroupId
        group.copy(
            id = newGroupId,
            weekStart = week,
            lineageId = group.lineageId.ifEmpty { group.id },
            exercises = group.exercises.map { planned ->
                val newPlannedId = newId()
                plannedIds[planned.id] = newPlannedId
                planned.copy(id = newPlannedId)
            },
        )
    }
    val remappedChecks = checks.map { check ->
        val replacement = plannedIds[check.plannedExerciseId]
        if (check.date >= week && replacement != null) {
            check.copy(plannedExerciseId = replacement)
        } else {
            check
        }
    }
    return WeekFork(
        data = copy(
            groups = groups + copies,
            checks = remappedChecks,
            weekdayPlans = weekdayPlans + WeekdayPlan(weekday, week, copies.map { it.id }),
        ),
        groupIds = groupIds,
        plannedIds = plannedIds,
    )
}

internal fun LifestyleData.checkedIds(date: String): Set<String> =
    checks.filter { it.date == date }.map { it.plannedExerciseId }.toSet()

internal fun LifestyleData.addGroupOn(date: LocalDate, name: String): LifestyleData {
    val fork = editableWeek(date)
    val week = weekStart(date)
    val weekday = date.dayOfWeek.value
    val id = newId()
    val group = ExerciseGroup(
        id = id,
        weekday = weekday,
        name = name,
        weekStart = week,
        lineageId = id,
    )
    val plans = fork.data.weekdayPlans.toMutableList()
    val index = plans.indexOfFirst { it.weekday == weekday && it.weekStart == week }
    if (index == -1) {
        plans += WeekdayPlan(weekday, week, listOf(id))
    } else {
        plans[index] = plans[index].copy(groupIds = plans[index].groupIds + id)
    }
    return fork.data.copy(groups = fork.data.groups + group, weekdayPlans = plans)
}

internal fun LifestyleData.renameGroupOn(date: LocalDate, groupId: String, name: String): LifestyleData {
    val fork = editableWeek(date)
    val targetId = fork.groupIds[groupId] ?: groupId
    return fork.data.copy(
        groups = fork.data.groups.map { group ->
            if (group.id == targetId) group.copy(name = name) else group
        },
    )
}

internal fun LifestyleData.deleteGroupOn(date: LocalDate, groupId: String): LifestyleData {
    val fork = editableWeek(date)
    val targetId = fork.groupIds[groupId] ?: groupId
    val week = weekStart(date)
    val weekday = date.dayOfWeek.value
    val plannedIds = fork.data.groups.find { it.id == targetId }?.exercises?.map { it.id }?.toSet().orEmpty()
    return fork.data.copy(
        groups = fork.data.groups.filterNot { it.id == targetId },
        weekdayPlans = fork.data.weekdayPlans.map { plan ->
            if (plan.weekday == weekday && plan.weekStart == week) {
                plan.copy(groupIds = plan.groupIds.filterNot { it == targetId })
            } else {
                plan
            }
        },
        checks = fork.data.checks.filterNot { it.plannedExerciseId in plannedIds },
    )
}

internal fun LifestyleData.addPlannedOn(date: LocalDate, groupId: String, planned: PlannedExercise): LifestyleData {
    val fork = editableWeek(date)
    val targetId = fork.groupIds[groupId] ?: groupId
    return fork.data.copy(
        groups = fork.data.groups.map { group ->
            if (group.id == targetId) group.copy(exercises = group.exercises + planned) else group
        },
    )
}

internal fun LifestyleData.updatePlannedOn(
    date: LocalDate,
    plannedId: String,
    sets: Int,
    reps: Int,
    weight: Double,
): LifestyleData {
    val fork = editableWeek(date)
    val targetId = fork.plannedIds[plannedId] ?: plannedId
    return fork.data.copy(
        groups = fork.data.groups.map { group ->
            group.copy(
                exercises = group.exercises.map { planned ->
                    if (planned.id == targetId) {
                        planned.copy(sets = sets, reps = reps, weight = weight)
                    } else {
                        planned
                    }
                },
            )
        },
    )
}

internal fun LifestyleData.deletePlannedOn(date: LocalDate, plannedId: String): LifestyleData {
    val fork = editableWeek(date)
    val targetId = fork.plannedIds[plannedId] ?: plannedId
    return fork.data.copy(
        groups = fork.data.groups.map { group ->
            group.copy(exercises = group.exercises.filterNot { it.id == targetId })
        },
        checks = fork.data.checks.filterNot { it.plannedExerciseId == targetId },
    )
}

internal fun LifestyleData.toggleCheck(date: String, plannedId: String): LifestyleData {
    val checked = checks.any { it.date == date && it.plannedExerciseId == plannedId }
    val next = if (checked) {
        checks.filterNot { it.date == date && it.plannedExerciseId == plannedId }
    } else {
        checks + ExerciseCheck(date = date, plannedExerciseId = plannedId)
    }
    return copy(checks = next)
}
