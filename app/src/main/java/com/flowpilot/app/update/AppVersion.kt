package com.flowpilot.app.update

/**
 * Numeric application version parsed from BuildConfig.VERSION_NAME or a GitHub release tag.
 * Build metadata is ignored and a hyphen suffix is treated as a pre-release.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val build: Int = 0,
    val preRelease: List<String> = emptyList(),
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }, { it.build })
            .takeIf { it != 0 }
            ?.let { return it }
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1
        val size = maxOf(preRelease.size, other.preRelease.size)
        for (index in 0 until size) {
            val left = preRelease.getOrNull(index)
            val right = other.preRelease.getOrNull(index)
            if (left == null) return -1
            if (right == null) return 1
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val result = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (result != 0) return result
        }
        return 0
    }

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        if (build > 0) append(".$build")
        if (preRelease.isNotEmpty()) append("-").append(preRelease.joinToString("."))
    }

    companion object {
        private const val MAX_COMPONENT = 1_000_000

        fun parse(raw: String): AppVersion? {
            val trimmed = raw.trim().removePrefix("v").removePrefix("V")
            if (trimmed.isBlank()) return null
            val withoutBuild = trimmed.substringBefore('+')
            val core = withoutBuild.substringBefore('-')
            val preRelease = withoutBuild
                .substringAfter('-', missingDelimiterValue = "")
                .takeIf { '-' in withoutBuild && it.isNotBlank() }
                ?.split('.', '-')
                ?.filter { it.isNotBlank() }
                .orEmpty()
            val parts = core.split('.')
            if (parts.isEmpty() || parts.size > 4) return null
            val numbers = parts.map { part ->
                part.toIntOrNull()?.takeIf { it in 0..MAX_COMPONENT } ?: return null
            }
            val padded = numbers + List(4 - numbers.size) { 0 }
            return parseNumbers(padded, preRelease)
        }

        private fun parseNumbers(numbers: List<Int>, preRelease: List<String>): AppVersion =
            AppVersion(
                major = numbers[0],
                minor = numbers[1],
                patch = numbers[2],
                build = numbers[3],
                preRelease = preRelease,
            )
    }
}
