package com.thelightphone.lifestyle

import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LifestyleLogicTest {
    @Test
    fun amountsAndGoalsStayExtensible() {
        val stored = LifestyleData(
            goals = mapOf(Nutrients.CALORIES to 1800.0),
            meals = listOf(
                Meal(
                    id = "meal",
                    name = "Orange",
                    date = "2026-10-06",
                    amounts = mapOf(
                        Nutrients.CALORIES to 60.0,
                        "vitamin_c" to 70.0,
                    ),
                ),
            ),
        )
        val decoded = decodeLifestyle(encodeLifestyle(stored))
        assertEquals(70.0, decoded.meals.single().amounts["vitamin_c"])
        val goals = resolvedGoals(decoded.goals)
        assertEquals(1800.0, goals[Nutrients.CALORIES])
        assertEquals(Nutrients.defaults.getValue("protein"), goals.getValue("protein"))
        assertEquals(60.0, totalsForDate(decoded.meals, "2026-10-06").getValue(Nutrients.CALORIES))
        assertEquals(0.0, totalsForDate(decoded.meals, "2026-10-07")[Nutrients.CALORIES] ?: 0.0)
    }

    @Test
    fun weekdayChecksResetTheFollowingWeek() {
        val tuesday = LocalDate.of(2026, 10, 6)
        val thisMonday = dateOnWeek(tuesday, DayOfWeek.MONDAY).toString()
        val nextMonday = dateOnWeek(tuesday.plusWeeks(1), DayOfWeek.MONDAY).toString()
        assertEquals("2026-10-05", thisMonday)
        assertEquals("2026-10-12", nextMonday)
        assertEquals("Oct 6", shortDate(tuesday))
        assertEquals("Tue Oct 6", compactDateTitle(tuesday))
        assertEquals("October 2026", monthYearTitle(java.time.YearMonth.of(2026, 10)))
        assertEquals(DayOfWeek.SUNDAY, dateOnWeek(tuesday, DayOfWeek.SUNDAY).dayOfWeek)

        val data = LifestyleData().toggleCheck(thisMonday, "planned")
        assertTrue("planned" in data.checkedIds(thisMonday))
        assertFalse("planned" in data.checkedIds(nextMonday))
    }

    @Test
    fun hiddenCountsAndCalendarMonthStartOnMonday() {
        val hidden = LifestyleData().withNutrientVisible("protein", false)
        assertEquals(listOf("protein"), hidden.hiddenNutrients)
        assertTrue(hidden.withNutrientVisible("protein", true).hiddenNutrients.isEmpty())
        assertTrue(hidden.withNutrientVisible(Nutrients.CALORIES, false).hiddenNutrients == listOf("protein"))

        val legacy = decodeLifestyle(
            """{"version":1,"goals":{},"meals":[],"savedMeals":[],"userExercises":[],"groups":[],"checks":[],"hiddenNutrients":["protein"]}""",
        )
        assertEquals(2, legacy.version)
        assertTrue(legacy.isNutrientHidden("protein"))
        assertTrue(legacy.isNutrientHidden("vitamin_c"))
        assertTrue(legacy.isNutrientHidden("saturated_fat"))
        assertFalse(legacy.isNutrientHidden("fat"))
        assertFalse(legacy.isNutrientHidden(Nutrients.CALORIES))
        assertFalse(legacy.withNutrientVisible("vitamin_c", true).isNutrientHidden("vitamin_c"))
        assertTrue(LifestyleData().migrated().isNutrientHidden("iron"))
        assertFalse(LifestyleData().migrated().isNutrientHidden("protein"))

        val cells = monthCells(java.time.YearMonth.of(2026, 10))
        assertEquals(null, cells.first())
        assertEquals(java.time.LocalDate.of(2026, 10, 1), cells[4])
        assertEquals(java.time.LocalDate.of(2026, 10, 4), dateOnWeek(java.time.LocalDate.of(2026, 10, 6), DayOfWeek.SUNDAY))
        assertEquals(31, cells.count { it != null })
        val logged = LifestyleData(
            meals = listOf(Meal("m", "Oats", "2026-10-06")),
            checks = listOf(ExerciseCheck("2026-10-07", "planned")),
        )
        assertEquals(setOf("2026-10-06", "2026-10-07"), loggedDates(logged))
    }

    @Test
    fun deletingAGroupRemovesItsChecks() {
        val groupId = "group"
        val plannedId = "planned"
        val data = LifestyleData(
            groups = listOf(
                ExerciseGroup(
                    id = groupId,
                    weekday = DayOfWeek.MONDAY.value,
                    name = "Push",
                    exercises = listOf(
                        PlannedExercise(
                            id = plannedId,
                            exerciseId = "bench",
                            sets = 3,
                            reps = 8,
                        ),
                    ),
                ),
            ),
        ).toggleCheck("2026-10-05", plannedId)

        val deleted = data.deleteGroup(groupId)
        assertTrue(deleted.groups.isEmpty())
        assertTrue(deleted.checks.isEmpty())
    }

    @Test
    fun savedMealsCanBeLoggedAgain() {
        val kept = LifestyleData().keepMeal("Oats", mapOf(Nutrients.CALORIES to 300.0))
        val updated = kept.keepMeal("oats", mapOf(Nutrients.CALORIES to 320.0, "protein" to 12.0))
        assertEquals(1, updated.savedMeals.size)
        assertEquals(kept.savedMeals.single().id, updated.savedMeals.single().id)
        assertEquals(320.0, updated.savedMeals.single().amounts.getValue(Nutrients.CALORIES))

        val logged = updated.logSavedMeal(updated.savedMeals.single().id, "2026-10-06")
        assertEquals(1, logged.savedMeals.size)
        assertEquals("oats", logged.meals.single().name)
        assertEquals("2026-10-06", logged.meals.single().date)
        assertEquals(320.0, totalsForDate(logged.meals, "2026-10-06").getValue(Nutrients.CALORIES))

        val again = logged.logSavedMeal(logged.savedMeals.single().id, "2026-10-06")
        assertEquals(2, again.meals.size)
        val decoded = decodeLifestyle(encodeLifestyle(again))
        assertEquals(again.savedMeals.single().id, decoded.savedMeals.single().id)
        assertEquals(listOf("oats"), filterSavedMeals(decoded.savedMeals, "oa").map { it.name })
        assertTrue(filterSavedMeals(decoded.savedMeals, "salad").isEmpty())
    }

    @Test
    fun userExercisesShareTheSearchList() {
        val catalog = listOf(
            Exercise(
                id = "curl",
                name = "Dumbbell Curl",
                source = ExerciseSource.CATALOG,
                equipment = "dumbbell",
                primaryMuscles = listOf("biceps"),
            ),
        )
        val merged = mergeExercises(
            catalog,
            listOf(UserExercise(id = "user-1", name = "Wrist Circles", source = USER_EXERCISE_SOURCE)),
        )
        assertEquals(listOf("Dumbbell Curl", "Wrist Circles"), merged.map { it.name })
        assertEquals(ExerciseSource.USER, merged.single { it.id == "user-1" }.source)
        assertEquals("Yours", merged.single { it.id == "user-1" }.detailLine())

        val found = filterExercises(merged, "dumb bell")
        assertEquals(listOf("curl"), found.map { it.id })
        assertEquals(listOf("user-1"), filterExercises(merged, "yours").map { it.id })
        assertTrue(filterExercises(merged, "biceps dumbbell").single().id == "curl")
    }

    @Test
    fun catalogParserIgnoresImagesLevelAndInstructions() {
        val parsed = parseCatalog(
            """
            [{"id":"Curl","name":"Curl","level":"beginner","instructions":["Lift"],"images":["Curl/0.jpg"],"primaryMuscles":["biceps"]}]
            """.trimIndent(),
        )
        assertEquals("Curl", parsed.single().name)
        assertEquals(ExerciseSource.CATALOG, parsed.single().source)
        assertEquals(listOf("biceps"), parsed.single().primaryMuscles)
    }

    @Test
    fun bundledCatalogIsThinned() {
        val file = catalogFile()
        val text = file.readText()
        val exercises = parseCatalog(text)
        assertTrue(exercises.size >= 800)
        assertTrue(exercises.any { it.id == "Alternate_Incline_Dumbbell_Curl" })
        assertTrue(exercises.any { it.id == "3_4_Sit-Up" && it.name == "3/4 Sit-Up" })
        assertTrue(exercises.all { it.source == ExerciseSource.CATALOG })
        assertFalse(text.contains("\"instructions\""))
        assertFalse(text.contains("\"images\""))
        assertFalse(text.contains("\"level\""))
    }

    @Test
    fun inputParsingAndMealSummary() {
        assertEquals("80.5", formatAmount(80.54))
        assertEquals("2000", formatAmount(2000.0))
        assertEquals(0.0, parseAmount(""))
        assertEquals(12.5, parseAmount("12.49"))
        assertNull(parseAmount("nope"))
        assertNull(parseAmount("-1"))
        assertEquals(3, parseCount("3"))
        assertNull(parseCount("0"))
        assertTrue(validateName("  ") is NameValidation.Invalid)
        assertEquals("Oats", (validateName("  Oats  ") as NameValidation.Ok).name)
        assertEquals(
            "250 ml water",
            mealSummary(mapOf("water" to 250.0)),
        )
        assertEquals(84.5, waterAmountForDisplay(2500.0, "oz"))
        assertEquals("5' 10\"", formatHeight(70.0, false))
        assertEquals("6' 0\"", formatHeight(72.0, false))
        assertEquals("178 cm", formatHeight(70.0, true))
        assertEquals("150 lb", formatWeight(150.0, false))
        assertEquals("68 kg", formatWeight(150.0, true))
        assertEquals(8.5, mealSummary(mapOf("water" to 250.0), "oz").substringBefore(" ").toDouble())
        assertEquals(1f, progressFraction(20.0, 0.0))
        assertEquals(0.5f, progressFraction(1000.0, 2000.0))
    }

    @Test
    fun repositoryStoresUserExercisesAndMeals() = runBlocking {
        val dir = createTempDirectory("lifestyle").toFile()
        try {
            val repository = LifestyleRepository(dir) {
                """[{"id":"curl","name":"Curl","equipment":"dumbbell"}]""".toByteArray()
            }
            repository.ensureLoaded()
            val created = repository.addUserExercise("Farmer Carry")
            assertNotNull(created)
            assertEquals(ExerciseSource.USER, created.source)
            val saved = File(dir, LIFESTYLE_STORE_FILE).readText()
            assertTrue(saved.contains("\"source\": \"user\"") || saved.contains("\"source\":\"user\""))
            val decoded = decodeLifestyle(saved)
            assertEquals(USER_EXERCISE_SOURCE, decoded.userExercises.single().source)

            val listed = filterExercises(repository.exercises.value, "farmer")
            assertEquals(created.id, listed.single().id)

            assertTrue(
                repository.saveMeal(
                    Meal(
                        id = "breakfast",
                        name = "Oats",
                        date = "2026-10-06",
                        amounts = mapOf(Nutrients.CALORIES to 300.0, "protein" to 12.0),
                    ),
                ),
            )
            assertTrue(repository.addGroup(DayOfWeek.TUESDAY, "Pull"))
            val group = repository.data.value.groups.single()
            assertTrue(repository.addPlannedExercise(group.id, created.id, 4, 6))
            val planned = repository.data.value.groups.single().exercises.single()
            assertTrue(repository.toggleCheck("2026-10-06", planned.id))
            assertTrue(repository.toggleCheck("2026-10-06", planned.id))
            assertTrue(repository.data.value.checks.isEmpty())

            val reloaded = LifestyleRepository(dir) {
                """[{"id":"curl","name":"Curl"}]""".toByteArray()
            }
            reloaded.ensureLoaded()
            assertEquals("Farmer Carry", reloaded.exercises.value.single { it.source == ExerciseSource.USER }.name)
            assertEquals(300.0, totalsForDate(reloaded.data.value.meals, "2026-10-06").getValue(Nutrients.CALORIES))
        } finally {
            dir.deleteRecursively()
        }
    }
}

private fun catalogFile(): File {
    val candidates = listOf(
        File("src/main/assets/exercises.json"),
        File("tool/src/main/assets/exercises.json"),
    )
    return candidates.firstOrNull { it.isFile }
        ?: error("exercises.json not found from ${File(".").absoluteFile}")
}
