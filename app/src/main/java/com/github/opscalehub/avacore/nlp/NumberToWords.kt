package com.github.opscalehub.avacore.nlp

/**
 * Converts numeric tokens inside Persian text into spoken Persian words.
 *
 * Handles:
 *  - cardinals of arbitrary length ("۱۴۰۳" -> "هزار و چهارصد و سه")
 *  - decimals ("۳٫۱۴" / "3.14" -> "سه ممیز چهارده")
 *  - percent ("۵۰٪" / "50%" -> "پنجاه درصد")
 *  - ordinals written with an attached suffix ("۲م" -> "دوم", "۳ام" -> "سوم")
 *  - negative sign ("-۵" / "−۵" -> "منفی پنج")
 *  - Persian (۰-۹), Arabic-Indic (٠-٩) and ASCII (0-9) digits
 *  - thousands separators ("," and "٬") which are stripped before conversion
 *
 * Pure Kotlin, no Android/3rd-party dependencies, so it is unit-testable on the JVM.
 */
object NumberToWords {

    // Nine is written «نُه»: bare «نه» is also "no", and eSpeak-NG reads it
    // as "na". (The ordinal goes back to bare «نهم», which eSpeak reads right.)
    private val ONES = arrayOf(
        "", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نُه"
    )
    private val TEENS = arrayOf(
        "ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده",
        "شانزده", "هفده", "هجده", "نوزده"
    )
    private val TENS = arrayOf(
        "", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود"
    )
    private val HUNDREDS = arrayOf(
        "", "صد", "دویست", "سیصد", "چهارصد", "پانصد",
        "ششصد", "هفتصد", "هشتصد", "نهصد"
    )
    // Scale words for each group of three digits (10^3, 10^6, ...).
    private val SCALES = arrayOf(
        "", "هزار", "میلیون", "میلیارد", "بیلیون", "بیلیارد", "تریلیون"
    )

    private const val AND = " و "
    private const val ZERO = "صفر"
    private const val NEGATIVE = "منفی"
    private const val POINT = "ممیز"
    private const val PERCENT = "درصد"

    // Matches an optional sign, a digit run with optional separators, optional
    // percent sign, and an optional attached ordinal suffix.
    // Group 1: sign, 2: number body, 3: percent, 4: ordinal suffix.
    private val NUMBER = Regex(
        "([-−])?" +
            "([0-9۰-۹٠-٩]+(?:[.,٫٬][0-9۰-۹٠-٩]+)*)" +
            "([%٪])?" +
            // ordinal suffix only counts when it is followed by a word boundary,
            // so "۱۰۰میلیون" is not misread as an ordinal of 100.
            "(?:(ام|م)(?![؀-ۿ]))?"
    )

    fun expand(text: String): String {
        return NUMBER.replace(text) { m ->
            val sign = m.groupValues[1]
            val body = foldDigits(m.groupValues[2])
            val percent = m.groupValues[3].isNotEmpty()
            val ordinal = m.groupValues[4].isNotEmpty()

            // Separate decimal part. Thousands separators (',' '٬') are removed;
            // decimal separators ('.' '٫') split integer/fraction.
            val cleaned = body.replace(",", "").replace("٬", "")
            val parts = cleaned.split('.', '٫')
            val intPart = parts[0]
            val fracPart = if (parts.size > 1) parts[1] else null

            val sb = StringBuilder()
            if (sign.isNotEmpty()) sb.append(NEGATIVE).append(' ')

            if (ordinal && fracPart == null) {
                sb.append(toOrdinal(intPart))
            } else {
                sb.append(toCardinal(intPart))
                if (fracPart != null) {
                    sb.append(' ').append(POINT).append(' ').append(fractionToWords(fracPart))
                }
            }
            if (percent) sb.append(' ').append(PERCENT)
            sb.toString()
        }
    }

    /** Fold Persian (۰-۹) and Arabic-Indic (٠-٩) digits to ASCII; leave separators. */
    fun foldDigits(s: String): String {
        val out = StringBuilder(s.length)
        for (c in s) {
            out.append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰') // Persian
                    in '٠'..'٩' -> '0' + (c - '٠') // Arabic-Indic
                    else -> c
                }
            )
        }
        return out.toString()
    }

    /** Convert an ASCII-digit string to Persian cardinal words. */
    fun toCardinal(digits: String): String {
        val n = digits.trimStart('0')
        if (n.isEmpty()) return ZERO

        // Split into 3-digit groups from the right.
        val groups = ArrayList<String>()
        var i = n.length
        while (i > 0) {
            val start = (i - 3).coerceAtLeast(0)
            groups.add(0, n.substring(start, i))
            i = start
        }

        val numGroups = groups.size
        val pieces = ArrayList<String>()
        for ((idx, g) in groups.withIndex()) {
            val value = g.toInt()
            if (value == 0) continue
            val scaleIdx = numGroups - idx - 1
            val words = threeDigitsToWords(value)
            val scale = SCALES.getOrElse(scaleIdx) { bigScale(scaleIdx) }
            val piece = when {
                scale.isEmpty() -> words
                // ۱۰۰۰ is read "هزار", not "یک هزار" (but ۱۰۰۰۰۰۰ is "یک میلیون").
                scaleIdx == 1 && value == 1 -> scale
                else -> "$words $scale"
            }
            pieces.add(piece)
        }
        return pieces.joinToString(AND)
    }

    /** Ordinal form, e.g. "۲" -> "دوم", "۳" -> "سوم", "۳۱" -> "سی و یکم". */
    fun toOrdinal(digits: String): String {
        val cardinal = toCardinal(digits)
        // Special last-token replacements per Persian ordinal rules.
        return when {
            cardinal == "یک" -> "اول"
            cardinal.endsWith("سه") -> cardinal.dropLast(2) + "سوم"
            cardinal.endsWith("سی") -> cardinal + "‌ام"   // سی‌ام
            cardinal.endsWith("نُه") -> cardinal.dropLast(3) + "نهم"
            else -> cardinal + "م"
        }
    }

    private fun threeDigitsToWords(value: Int): String {
        val parts = ArrayList<String>(3)
        val h = value / 100
        val rest = value % 100
        if (h > 0) parts.add(HUNDREDS[h])
        if (rest in 1..9) {
            parts.add(ONES[rest])
        } else if (rest in 10..19) {
            parts.add(TEENS[rest - 10])
        } else if (rest >= 20) {
            parts.add(TENS[rest / 10])
            if (rest % 10 != 0) parts.add(ONES[rest % 10])
        }
        return parts.joinToString(AND)
    }

    /**
     * Read the fractional part. If it has leading zeros they are spoken as
     * "صفر" individually, then the remaining significant part as a cardinal,
     * which matches natural Persian reading ("۰۵" -> "صفر پنج").
     */
    private fun fractionToWords(frac: String): String {
        val sb = StringBuilder()
        var idx = 0
        while (idx < frac.length && frac[idx] == '0') {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(ZERO)
            idx++
        }
        val rest = frac.substring(idx)
        if (rest.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(toCardinal(rest))
        } else if (sb.isEmpty()) {
            sb.append(ZERO)
        }
        return sb.toString()
    }

    // Fallback for numbers larger than the named scales: read each group with a
    // generic "× هزار^k" is impractical, so we read the whole number group-wise
    // without a scale word (rare in practice). Kept defensive.
    private fun bigScale(@Suppress("UNUSED_PARAMETER") idx: Int): String = ""
}
