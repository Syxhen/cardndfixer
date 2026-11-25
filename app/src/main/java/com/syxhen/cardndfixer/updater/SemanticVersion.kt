package com.syxhen.cardndfixer.updater

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String> = emptyList(),
) : Comparable<SemanticVersion> {

    val isPrerelease: Boolean
        get() = preRelease.isNotEmpty()

    override fun compareTo(other: SemanticVersion): Int {
        compareValues(major, other.major).let { if (it != 0) return it }
        compareValues(minor, other.minor).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }

        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1

        val minSize = minOf(preRelease.size, other.preRelease.size)
        for (i in 0 until minSize) {
            val a = preRelease[i]
            val b = other.preRelease[i]
            if (a == b) continue

            val aNum = a.toLongOrNull()
            val bNum = b.toLongOrNull()
            return when {
                aNum != null && bNum != null -> aNum.compareTo(bNum)
                aNum != null -> -1
                bNum != null -> 1
                else -> a.compareTo(b, ignoreCase = true)
            }
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        if (preRelease.isNotEmpty()) {
            append('-')
            append(preRelease.joinToString("."))
        }
    }

    companion object {
        private val CORE_REGEX = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$""")

        fun parseOrNull(raw: String): SemanticVersion? {
            val trimmed = raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('+')
                .removeSuffix("-debug")
            val match = CORE_REGEX.matchEntire(trimmed) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            val pre = match.groupValues[4]
                .takeIf { it.isNotBlank() }
                ?.split('.')
                ?.filter { it.isNotBlank() }
                .orEmpty()
            return SemanticVersion(
                major = major,
                minor = minor,
                patch = patch,
                preRelease = pre,
            )
        }
    }
}
