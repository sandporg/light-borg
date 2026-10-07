package com.thelightphone.lifestyle

import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class LifestyleRepository(
    private val filesDir: File,
    private val readAsset: (String) -> ByteArray,
) {
    private val mutex = Mutex()

    @Volatile
    private var loaded = false

    private val _data = MutableStateFlow(LifestyleData())
    val data: StateFlow<LifestyleData> = _data.asStateFlow()

    private val _catalog = MutableStateFlow<List<Exercise>>(emptyList())

    private val _exercises = MutableStateFlow<List<Exercise>>(emptyList())
    val exercises: StateFlow<List<Exercise>> = _exercises.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun dismissError() {
        _error.value = null
    }

    fun exercise(id: String): Exercise? = _exercises.value.find { it.id == id }

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return@withLock
            loadLocked()
        }
    }

    suspend fun saveMeal(meal: Meal): Boolean = mutate { it.upsertMeal(meal) }

    suspend fun deleteMeal(mealId: String): Boolean = mutate { it.deleteMeal(mealId) }

    suspend fun keepMeal(name: String, amounts: Map<String, Double>): Boolean =
        mutate { it.keepMeal(name, amounts) }

    suspend fun saveSavedMeal(meal: SavedMeal): Boolean = mutate { it.upsertSavedMeal(meal) }

    suspend fun deleteSavedMeal(savedId: String): Boolean = mutate { it.deleteSavedMeal(savedId) }

    suspend fun logSavedMeal(savedId: String, date: String): Boolean {
        ensureLoaded()
        if (_data.value.savedMeals.none { it.id == savedId }) return false
        return mutate { it.logSavedMeal(savedId, date) }
    }

    suspend fun setGoal(nutrientId: String, amount: Double): Boolean {
        if (Nutrients.byId(nutrientId) == null) return false
        return mutate { it.withGoal(nutrientId, amount) }
    }

    suspend fun setNutrientVisible(nutrientId: String, visible: Boolean): Boolean {
        if (Nutrients.byId(nutrientId) == null) return false
        return mutate { it.withNutrientVisible(nutrientId, visible) }
    }

    suspend fun addUserExercise(name: String): Exercise? {
        val stored = UserExercise(
            id = newUserExerciseId(),
            name = name.trim(),
            source = USER_EXERCISE_SOURCE,
        )
        val saved = mutate { it.copy(userExercises = it.userExercises + stored) }
        if (!saved) return null
        return Exercise(
            id = stored.id,
            name = stored.name,
            source = ExerciseSource.USER,
        )
    }

    suspend fun addGroup(weekday: DayOfWeek, name: String): Boolean =
        mutate { it.addGroup(weekday, name) }

    suspend fun renameGroup(groupId: String, name: String): Boolean =
        mutate { it.renameGroup(groupId, name) }

    suspend fun deleteGroup(groupId: String): Boolean = mutate { it.deleteGroup(groupId) }

    suspend fun addPlannedExercise(
        groupId: String,
        exerciseId: String,
        sets: Int,
        reps: Int,
    ): Boolean = mutate {
        it.addPlanned(
            groupId,
            PlannedExercise(
                id = newId(),
                exerciseId = exerciseId,
                sets = sets,
                reps = reps,
            ),
        )
    }

    suspend fun updatePlannedExercise(plannedId: String, sets: Int, reps: Int): Boolean =
        mutate { it.updatePlanned(plannedId, sets, reps) }

    suspend fun deletePlannedExercise(plannedId: String): Boolean =
        mutate { it.deletePlanned(plannedId) }

    suspend fun toggleCheck(date: String, plannedId: String): Boolean =
        mutate { it.toggleCheck(date, plannedId) }

    private suspend fun loadLocked() {
        val catalog = withContext(Dispatchers.IO) {
            runCatching { parseCatalog(readAsset(EXERCISE_CATALOG_ASSET).decodeToString()) }
        }
        _catalog.value = catalog.getOrElse {
            _error.value = "Could not read exercises."
            emptyList()
        }
        _data.value = readStored()
        _exercises.value = mergeExercises(_catalog.value, _data.value.userExercises)
        loaded = true
        _ready.value = true
    }

    private suspend fun readStored(): LifestyleData {
        val file = File(filesDir, LIFESTYLE_STORE_FILE)
        if (!file.exists()) return LifestyleData().migrated()
        val text = withContext(Dispatchers.IO) { runCatching { file.readText() }.getOrNull() }
        if (text == null) {
            _error.value = "Saved data could not be read. Starting fresh."
            return LifestyleData().migrated()
        }
        return runCatching {
            val parsed = lifestyleJson.decodeFromString<LifestyleData>(text)
            val migrated = parsed.migrated()
            if (migrated.version != parsed.version) {
                withContext(Dispatchers.IO) { write(migrated) }
            }
            migrated
        }.getOrElse {
            withContext(Dispatchers.IO) {
                runCatching { file.copyTo(File(filesDir, "$LIFESTYLE_STORE_FILE.bak"), overwrite = true) }
            }
            _error.value = "Saved data could not be read. Starting fresh."
            LifestyleData()
        }
    }

    private suspend fun mutate(block: (LifestyleData) -> LifestyleData): Boolean {
        ensureLoaded()
        return try {
            mutex.withLock {
                val next = block(_data.value)
                withContext(Dispatchers.IO) { write(next) }
                _data.value = next
                _exercises.value = mergeExercises(_catalog.value, next.userExercises)
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _error.value = "Could not save."
            false
        }
    }

    private fun write(data: LifestyleData) {
        if (!filesDir.exists() && !filesDir.mkdirs()) {
            throw IOException("Could not create ${filesDir.path}")
        }
        val dest = File(filesDir, LIFESTYLE_STORE_FILE)
        val tmp = File(filesDir, "$LIFESTYLE_STORE_FILE.tmp")
        tmp.writeText(encodeLifestyle(data))
        if (dest.exists() && !dest.delete()) {
            throw IOException("Could not replace $LIFESTYLE_STORE_FILE")
        }
        if (!tmp.renameTo(dest)) {
            dest.writeText(tmp.readText())
            if (!tmp.delete()) {
                tmp.deleteOnExit()
            }
        }
    }

    companion object {
        @Volatile
        private var instance: LifestyleRepository? = null

        fun get(filesDir: File, readAsset: (String) -> ByteArray): LifestyleRepository {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: LifestyleRepository(filesDir, readAsset).also { instance = it }
            }
        }
    }
}
