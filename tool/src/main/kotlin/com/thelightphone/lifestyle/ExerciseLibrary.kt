package com.thelightphone.lifestyle

import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Catalog bundled from the public-domain free-exercise-db
// (https://github.com/yuhonas/free-exercise-db, Unlicense).
// Images, level, and instructions are not included.
internal const val EXERCISE_CATALOG_ASSET = "exercises.json"

internal enum class ExerciseSource {
    CATALOG,
    USER,
}

internal data class Exercise(
    val id: String,
    val name: String,
    val source: ExerciseSource,
    val force: String? = null,
    val mechanic: String? = null,
    val equipment: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val category: String? = null,
)

@Serializable
internal data class CatalogExercise(
    val id: String,
    val name: String,
    val force: String? = null,
    val mechanic: String? = null,
    val equipment: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val category: String? = null,
)

private val catalogJson = Json { ignoreUnknownKeys = true }

internal fun parseCatalog(text: String): List<Exercise> =
    catalogJson.decodeFromString<List<CatalogExercise>>(text).map { entry ->
        Exercise(
            id = entry.id,
            name = entry.name,
            source = ExerciseSource.CATALOG,
            force = entry.force,
            mechanic = entry.mechanic,
            equipment = entry.equipment,
            primaryMuscles = entry.primaryMuscles,
            secondaryMuscles = entry.secondaryMuscles,
            category = entry.category,
        )
    }

internal fun mergeExercises(
    catalog: List<Exercise>,
    userExercises: List<UserExercise>,
): List<Exercise> {
    val custom = userExercises.map { stored ->
        Exercise(
            id = stored.id,
            name = stored.name,
            source = ExerciseSource.USER,
        )
    }
    return (catalog + custom).sortedBy { it.name.lowercase(Locale.US) }
}

internal fun filterExercises(exercises: List<Exercise>, query: String): List<Exercise> {
    val tokens = query.trim().lowercase(Locale.US).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return exercises
    return exercises.filter { exercise ->
        val haystack = exercise.searchText()
        tokens.all { token -> haystack.contains(token) }
    }
}

internal fun Exercise.detailLine(): String {
    val parts = mutableListOf<String>()
    val muscles = primaryMuscles.map { it.toDisplayLabel() }.filter { it.isNotEmpty() }
    if (muscles.isNotEmpty()) {
        parts += muscles.joinToString(", ")
    } else {
        category?.toDisplayLabel()?.takeIf { it.isNotEmpty() }?.let { parts += it }
    }
    equipment?.toDisplayLabel()?.takeIf { it.isNotEmpty() }?.let { parts += it }
    if (source == ExerciseSource.USER) parts += "Yours"
    return parts.joinToString(" · ")
}

private fun Exercise.searchText(): String {
    val yours = if (source == ExerciseSource.USER) "yours user" else ""
    return listOf(name, equipment, category, force, mechanic, yours)
        .plus(primaryMuscles)
        .plus(secondaryMuscles)
        .filterNotNull()
        .joinToString(" ")
        .lowercase(Locale.US)
}

internal fun String.toDisplayLabel(): String =
    split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
            }
        }
