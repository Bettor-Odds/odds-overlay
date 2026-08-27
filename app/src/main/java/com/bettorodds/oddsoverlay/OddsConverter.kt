package com.bettorodds.oddsoverlay

/**
 * Implied probability shown as a percentage, converted to American odds.
 *
 * Prices outside [MIN_PERCENT, MAX_PERCENT] are rejected rather than displayed: at the extremes a
 * single decimal place of input maps to a range of American odds too wide to quote honestly. At
 * 99.0% the true price lies anywhere in -9423..-10426, so we show nothing instead of a false -9900.
 */
object OddsConverter {

    private const val MIN_PERCENT = 0.5
    private const val MAX_PERCENT = 99.0

    /** Matches "43.4%", "43%", "7.5 %". Rejects thousands-separated or multi-decimal tokens. */
    private val PERCENT_PATTERN = Regex("""(?<!\d)(\d{1,2}(?:\.\d)?|100(?:\.0)?)\s?%""")

    fun toAmerican(percent: Double): Int? {
        if (percent < MIN_PERCENT || percent > MAX_PERCENT) return null
        val p = percent / 100.0
        val american = if (p > 0.5) -(100.0 * p / (1.0 - p)) else 100.0 * (1.0 - p) / p
        val rounded = Math.round(american).toInt()
        return if (rounded == 0) null else rounded
    }

    fun format(american: Int): String = if (american > 0) "+$american" else "$american"

    /** Converts a percentage token to its display string, or null if it is not a quotable price. */
    fun convertToken(token: String): String? {
        val percent = token.trimEnd('%', ' ').toDoubleOrNull() ?: return null
        return toAmerican(percent)?.let(::format)
    }

    /**
     * Extracts every percentage in [text] with its character offsets, so a caller holding a
     * bounding box for the whole string can narrow it to the matched substring.
     */
    fun findPercentages(text: String): List<Match> =
        PERCENT_PATTERN.findAll(text).mapNotNull { m ->
            val percent = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            val american = toAmerican(percent) ?: return@mapNotNull null
            Match(
                raw = m.value,
                percent = percent,
                display = format(american),
                startIndex = m.range.first,
                endIndex = m.range.last + 1
            )
        }.toList()

    data class Match(
        val raw: String,
        val percent: Double,
        val display: String,
        val startIndex: Int,
        val endIndex: Int
    )
}
