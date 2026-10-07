package com.thelightphone.lifestyle

internal const val MAX_NAME_LENGTH = 80
internal const val MAX_AMOUNT = 1_000_000.0
internal const val MAX_COUNT = 999

internal sealed interface NameValidation {
    data class Ok(val name: String) : NameValidation
    data class Invalid(val message: String) : NameValidation
}

internal fun validateName(raw: String): NameValidation {
    val name = raw.trim()
    if (name.isEmpty()) return NameValidation.Invalid("Add a name.")
    if (name.length > MAX_NAME_LENGTH) return NameValidation.Invalid("Use a shorter name.")
    return NameValidation.Ok(name)
}

internal fun parseAmount(raw: String): Double? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return 0.0
    val value = trimmed.toDoubleOrNull() ?: return null
    if (!value.isFinite() || value < 0.0 || value > MAX_AMOUNT) return null
    return kotlin.math.round(value * 10.0) / 10.0
}

internal fun parseCount(raw: String): Int? {
    val value = raw.trim().toIntOrNull() ?: return null
    return value.takeIf { it in 1..MAX_COUNT }
}

internal fun formatAmount(value: Double): String {
    if (!value.isFinite()) return "0"
    val tenths = kotlin.math.round(value * 10.0).toLong()
    val whole = tenths / 10
    val fraction = kotlin.math.abs(tenths % 10).toInt()
    return if (fraction == 0) whole.toString() else "$whole.$fraction"
}
