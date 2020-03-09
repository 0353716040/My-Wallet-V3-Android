package info.blockchain.balance

import java.math.BigDecimal
import java.util.Locale

interface Money {

    /**
     * Use [symbol] for user display. This can be used by APIs etc.
     */
    val currencyCode: String
    /**
     * User displayable symbol
     */
    val symbol: String

    val isZero: Boolean
    val isPositive: Boolean

    val maxDecimalPlaces: Int

    /**
     * Where a Money type can store more decimal places than is necessary,
     * this property can be used to limit it for user input and display.
     */
    val userDecimalPlaces: Int
        get() = maxDecimalPlaces

    fun toBigDecimal(): BigDecimal

    fun toZero(): Money

    /**
     * String formatted in the specified locale, or the systems default locale.
     * Includes symbol, which may appear on either side of the number.
     */
    fun toStringWithSymbol(locale: Locale = Locale.getDefault()): String

    /**
     * String formatted in the specified locale, or the systems default locale.
     * Without symbol.
     */
    fun toStringWithoutSymbol(locale: Locale = Locale.getDefault()): String

    /**
     * The formatted string in parts in the specified locale, or the systems default locale.
     */
    fun toStringParts(locale: Locale = Locale.getDefault()) =
        toStringWithoutSymbol(locale).let {
            val index = it.lastIndexOf(LocaleDecimalFormat[locale].decimalFormatSymbols.decimalSeparator)
            if (index != -1) {
                Parts(
                    symbol = symbol,
                    major = it.substring(0, index),
                    minor = it.substring(index + 1),
                    majorAndMinor = it
                )
            } else {
                Parts(
                    symbol = symbol,
                    major = it,
                    minor = "",
                    majorAndMinor = it
                )
            }
        }

    class Parts(
        val symbol: String,
        val major: String,
        val minor: String,
        val majorAndMinor: String
    )

    fun formatOrSymbolForZero() =
        if (isZero) {
            symbol
        } else {
            toStringWithSymbol()
        }
}

class ComparisonException(
    lhsSymbol: String,
    rhsSymbol: String
) : ValueTypeMismatchException("compare", lhsSymbol, rhsSymbol)

open class ValueTypeMismatchException(
    verb: String,
    lhsSymbol: String,
    rhsSymbol: String
) : RuntimeException("Can't $verb $lhsSymbol and $rhsSymbol")

operator fun Money.compareTo(other: Money): Int {
    return when (this) {
        is FiatValue -> {
            compareTo(
                other as? FiatValue ?: throw ComparisonException(currencyCode, other.currencyCode)
            )
        }
        is CryptoValue -> {
            compareTo(
                other as? CryptoValue ?: throw ComparisonException(currencyCode, other.currencyCode)
            )
        }
        else -> throw IllegalArgumentException()
    }
}
