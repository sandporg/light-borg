package com.thelightphone.lifestyle

import java.util.Locale

/**
 * Daily nutrients tracked by the tool.
 *
 * Add an entry here to show it on Today, meals, and goals. Logged amounts and
 * goals are stored by [Nutrient.id], so existing saves keep working.
 */
internal data class Nutrient(
    val id: String,
    val label: String,
    val unit: String,
    val defaultGoal: Double,
) {
    fun fieldLabel(): String = "$label ($unit)"
}

internal object Nutrients {
    const val CALORIES = "calories"

    val all: List<Nutrient> = listOf(
        Nutrient(CALORIES, "Calories", "kcal", 2000.0),
        Nutrient("protein", "Protein", "g", 150.0),
        Nutrient("carbs", "Carbs", "g", 250.0),
        Nutrient("fat", "Fat", "g", 65.0),
        Nutrient("water", "Water", "ml", 2500.0),
        Nutrient("sodium", "Sodium", "mg", 2300.0),
        Nutrient("fiber", "Fiber", "g", 28.0),
        Nutrient("sugar", "Sugar", "g", 50.0),
        Nutrient("potassium", "Potassium", "mg", 3400.0),
    )

    val defaults: Map<String, Double> = all.associate { it.id to it.defaultGoal }

    fun byId(id: String): Nutrient? = all.find { it.id == id }
}

internal fun resolvedGoals(stored: Map<String, Double>): Map<String, Double> {
    val resolved = Nutrients.defaults.toMutableMap()
    stored.forEach { (id, amount) ->
        if (id in resolved && amount.isFinite() && amount >= 0.0) {
            resolved[id] = amount
        }
    }
    return resolved
}

internal fun totalsForDate(meals: List<Meal>, date: String): Map<String, Double> {
    val totals = mutableMapOf<String, Double>()
    meals.filter { it.date == date }.forEach { meal ->
        meal.amounts.forEach { (id, amount) ->
            if (amount.isFinite()) {
                totals[id] = (totals[id] ?: 0.0) + amount
            }
        }
    }
    return totals
}

internal fun progressFraction(current: Double, goal: Double): Float {
    if (!current.isFinite() || current <= 0.0) return 0f
    if (!goal.isFinite() || goal <= 0.0) return 1f
    return (current / goal).toFloat().coerceIn(0f, 1f)
}

internal fun Map<String, Double>.withAmount(id: String, amount: Double): Map<String, Double> {
    if (!amount.isFinite() || amount <= 0.0) return this - id
    return this + (id to amount)
}

internal fun sanitizeAmounts(amounts: Map<String, Double>): Map<String, Double> =
    amounts.filter { (_, amount) -> amount.isFinite() && amount > 0.0 }

internal fun filterSavedMeals(meals: List<SavedMeal>, query: String): List<SavedMeal> {
    val sorted = meals.sortedBy { it.name.lowercase(Locale.US) }
    val tokens = query.trim().lowercase(Locale.US).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return sorted
    return sorted.filter { meal ->
        val haystack = meal.name.lowercase(Locale.US)
        tokens.all { token -> haystack.contains(token) }
    }
}

internal fun mealSummary(amounts: Map<String, Double>): String {
    val calories = amounts[Nutrients.CALORIES] ?: 0.0
    if (calories > 0.0) return "${formatAmount(calories)} kcal"
    val other = Nutrients.all.firstOrNull { nutrient ->
        nutrient.id != Nutrients.CALORIES && (amounts[nutrient.id] ?: 0.0) > 0.0
    }
    if (other == null) return "0 kcal"
    val amount = amounts[other.id] ?: 0.0
    return "${formatAmount(amount)} ${other.unit} ${other.label.lowercase(Locale.US)}"
}
