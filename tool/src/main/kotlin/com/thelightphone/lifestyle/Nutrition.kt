package com.thelightphone.lifestyle

import java.util.Locale

/**
 * Daily nutrients tracked by the tool.
 *
 * Add an entry here to show it on Today, meals, and goals. Logged amounts and
 * goals are stored by [Nutrient.id], so existing saves keep working.
 * Set [Nutrient.hiddenByDefault] so a new nutrient stays off the home screen
 * until the user turns it on in Macros.
 */
internal data class Nutrient(
    val id: String,
    val label: String,
    val unit: String,
    val defaultGoal: Double,
    val hiddenByDefault: Boolean = false,
) {
    fun fieldLabel(waterUnit: String = unit): String {
        val shown = if (id == Nutrients.WATER) waterUnit else unit
        return "$label ($shown)"
    }
}

internal object Nutrients {
    const val CALORIES = "calories"
    const val WATER = "water"

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
        Nutrient("saturated_fat", "Saturated fat", "g", 20.0, hiddenByDefault = true),
        Nutrient("cholesterol", "Cholesterol", "mg", 300.0, hiddenByDefault = true),
        Nutrient("calcium", "Calcium", "mg", 1300.0, hiddenByDefault = true),
        Nutrient("iron", "Iron", "mg", 18.0, hiddenByDefault = true),
        Nutrient("vitamin_d", "Vitamin D", "mcg", 20.0, hiddenByDefault = true),
        Nutrient("vitamin_a", "Vitamin A", "mcg", 900.0, hiddenByDefault = true),
        Nutrient("vitamin_c", "Vitamin C", "mg", 90.0, hiddenByDefault = true),
        Nutrient("vitamin_e", "Vitamin E", "mg", 15.0, hiddenByDefault = true),
        Nutrient("vitamin_k", "Vitamin K", "mcg", 120.0, hiddenByDefault = true),
        Nutrient("thiamin", "Thiamin", "mg", 1.2, hiddenByDefault = true),
        Nutrient("riboflavin", "Riboflavin", "mg", 1.3, hiddenByDefault = true),
        Nutrient("niacin", "Niacin", "mg", 16.0, hiddenByDefault = true),
        Nutrient("vitamin_b6", "Vitamin B6", "mg", 1.7, hiddenByDefault = true),
        Nutrient("folate", "Folate", "mcg", 400.0, hiddenByDefault = true),
        Nutrient("vitamin_b12", "Vitamin B12", "mcg", 2.4, hiddenByDefault = true),
        Nutrient("biotin", "Biotin", "mcg", 30.0, hiddenByDefault = true),
        Nutrient("pantothenic_acid", "Pantothenic acid", "mg", 5.0, hiddenByDefault = true),
        Nutrient("phosphorus", "Phosphorus", "mg", 1250.0, hiddenByDefault = true),
        Nutrient("iodine", "Iodine", "mcg", 150.0, hiddenByDefault = true),
        Nutrient("magnesium", "Magnesium", "mg", 420.0, hiddenByDefault = true),
        Nutrient("zinc", "Zinc", "mg", 11.0, hiddenByDefault = true),
        Nutrient("selenium", "Selenium", "mcg", 55.0, hiddenByDefault = true),
        Nutrient("copper", "Copper", "mg", 0.9, hiddenByDefault = true),
        Nutrient("manganese", "Manganese", "mg", 2.3, hiddenByDefault = true),
        Nutrient("chromium", "Chromium", "mcg", 35.0, hiddenByDefault = true),
        Nutrient("molybdenum", "Molybdenum", "mcg", 45.0, hiddenByDefault = true),
        Nutrient("chloride", "Chloride", "mg", 2300.0, hiddenByDefault = true),
        Nutrient("choline", "Choline", "mg", 550.0, hiddenByDefault = true),
    )

    val defaults: Map<String, Double> = all.associate { it.id to it.defaultGoal }

    val hiddenByDefault: List<String> = all.filter { it.hiddenByDefault }.map { it.id }

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

internal fun mealSummary(amounts: Map<String, Double>, waterUnit: String = "ml"): String {
    val calories = amounts[Nutrients.CALORIES] ?: 0.0
    if (calories > 0.0) return "${formatAmount(calories)} kcal"
    val other = Nutrients.all.firstOrNull { nutrient ->
        nutrient.id != Nutrients.CALORIES && (amounts[nutrient.id] ?: 0.0) > 0.0
    }
    if (other == null) return "0 kcal"
    val stored = amounts[other.id] ?: 0.0
    val amount = if (other.id == Nutrients.WATER) waterAmountForDisplay(stored, waterUnit) else stored
    val unit = if (other.id == Nutrients.WATER) waterUnit else other.unit
    return "${formatAmount(amount)} $unit ${other.label.lowercase(Locale.US)}"
}

private const val MILLILITERS_PER_FLUID_OUNCE = 29.5735295625

internal fun millilitersToOunces(milliliters: Double): Double =
    kotlin.math.round(milliliters / MILLILITERS_PER_FLUID_OUNCE * 10.0) / 10.0

internal fun ouncesToMilliliters(ounces: Double): Double =
    kotlin.math.round(ounces * MILLILITERS_PER_FLUID_OUNCE * 10.0) / 10.0

internal fun waterAmountForDisplay(storedMilliliters: Double, unit: String): Double =
    if (unit == "oz") millilitersToOunces(storedMilliliters) else storedMilliliters

internal fun waterAmountForStorage(entered: Double, unit: String): Double =
    if (unit == "oz") ouncesToMilliliters(entered) else entered
