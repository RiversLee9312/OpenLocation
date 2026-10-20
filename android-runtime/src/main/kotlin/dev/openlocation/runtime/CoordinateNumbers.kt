package dev.openlocation.runtime

/** Human numeric input accepts localized decimal digits; exported coordinates remain ASCII. */
object CoordinateNumbers {
    fun parse(text: String): Double {
        val normalized = buildString {
            text.trim().forEach { char ->
                val digit = Character.digit(char, 10)
                append(
                    when {
                        digit >= 0 -> ('0'.code + digit).toChar()
                        char == ',' || char == '\u066b' -> '.'
                        char == '\u2212' -> '-'
                        else -> char
                    }
                )
            }
        }
        return normalized.toDouble().also { require(it.isFinite()) }
    }
}
