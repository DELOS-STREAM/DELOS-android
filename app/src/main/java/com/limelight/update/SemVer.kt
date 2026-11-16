package com.limelight.update

/**
 * A version as Semantic Versioning 2.0.0 defines it (docs/VERSIONING.md): `MAJOR.MINOR.PATCH` with an optional
 * pre-release (`-rc.1`) and optional build metadata (`+g912bc0d`). Precedence follows section 11 of the specification;
 * build metadata is ignored, so two versions that differ only in it compare equal.
 */
class SemVer private constructor(val major: Long, val minor: Long, val patch: Long, private val pre: List<String>) : Comparable<SemVer> {
    val isPreRelease: Boolean get() = pre.isNotEmpty()

    override fun compareTo(other: SemVer): Int {
        major.compareTo(other.major).let { if (it != 0) return it }
        minor.compareTo(other.minor).let { if (it != 0) return it }
        patch.compareTo(other.patch).let { if (it != 0) return it }
        // A version with a pre-release precedes the same version without one
        if (pre.isEmpty() && other.pre.isEmpty()) return 0
        if (pre.isEmpty()) return 1
        if (other.pre.isEmpty()) return -1
        for (i in 0 until minOf(pre.size, other.pre.size)) {
            val c = comparePre(pre[i], other.pre[i])
            if (c != 0) return c
        }
        return pre.size.compareTo(other.pre.size)
    }

    override fun equals(other: Any?): Boolean = other is SemVer && compareTo(other) == 0

    override fun hashCode(): Int = listOf(major, minor, patch, pre).hashCode()

    /** The version without build metadata: `1.0.0-rc.1`. */
    override fun toString(): String = "$major.$minor.$patch" + if (pre.isEmpty()) "" else "-" + pre.joinToString(".")

    companion object {
        private val NUMERIC = Regex("0|[1-9][0-9]*")
        private val IDENTIFIER = Regex("[0-9A-Za-z-]+")

        /** Numeric identifiers rank by value and below alphanumeric ones, which rank by ASCII order. */
        private fun comparePre(a: String, b: String): Int {
            val an = NUMERIC.matches(a)
            val bn = NUMERIC.matches(b)
            return when {
                an && bn -> if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)
                an -> -1
                bn -> 1
                else -> a.compareTo(b)
            }
        }

        /** Parses a version such as `1.2.3`, `1.0.0-rc.1` or `1.0.0+build`; null when [text] is not a valid SemVer 2.0.0 string. */
        fun parse(text: String): SemVer? {
            val main = text.substringBefore('+')
            val build = if ('+' in text) text.substringAfter('+') else null
            if (build != null && (build.isEmpty() || !build.split('.').all { IDENTIFIER.matches(it) })) return null
            val core = main.substringBefore('-')
            val preText = if ('-' in main) main.substringAfter('-') else null
            val numbers = core.split('.')
            if (numbers.size != 3 || !numbers.all { NUMERIC.matches(it) }) return null
            val values = numbers.map { it.toLongOrNull() ?: return null }
            val pre = preText?.split('.') ?: emptyList()
            if (preText != null) {
                if (pre.any { !IDENTIFIER.matches(it) }) return null
                if (pre.any { it.all(Char::isDigit) && !NUMERIC.matches(it) }) return null
            }
            return SemVer(values[0], values[1], values[2], pre)
        }

        /** Parses a release tag: `v1.2.3` (the `v` is a convention of tag names, not part of the version). */
        fun parseTag(tag: String): SemVer? = parse(tag.removePrefix("v"))
    }
}
