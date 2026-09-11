package com.nanopixel.sendspinsatellite.connection

object PlayerNamePolicy {
    const val maxCodePoints = 64
    const val defaultName = "Sendspin Satellite"

    data class Validation(
        val normalized: String?,
        val error: String?,
    ) {
        val isValid: Boolean
            get() = normalized != null
    }

    fun defaultFor(deviceModel: String): String {
        val model = collapseWhitespace(deviceModel)
        if (model.isEmpty()) return defaultName
        return truncateToCodePoints("$defaultName $model")
    }

    fun validate(input: String): Validation {
        val normalized = input.trim()
        return when {
            normalized.isEmpty() -> Validation(null, "Player name cannot be blank.")
            normalized.any { Character.isISOControl(it) } ->
                Validation(null, "Player name contains unsupported control characters.")
            normalized.codePointCount(0, normalized.length) > maxCodePoints ->
                Validation(null, "Player name must be 64 characters or fewer.")
            else -> Validation(normalized, null)
        }
    }

    private fun collapseWhitespace(value: String): String =
        value.trim().split(Regex("\\s+")).filter(String::isNotEmpty).joinToString(" ")

    private fun truncateToCodePoints(value: String): String {
        val codePointCount = value.codePointCount(0, value.length)
        if (codePointCount <= maxCodePoints) return value
        val end = value.offsetByCodePoints(0, maxCodePoints)
        return value.substring(0, end)
    }
}