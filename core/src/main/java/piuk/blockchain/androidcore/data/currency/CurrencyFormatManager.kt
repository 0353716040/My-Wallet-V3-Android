package piuk.blockchain.androidcore.data.currency

import androidx.annotation.VisibleForTesting
import com.blockchain.extensions.exhaustive
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.balance.FormatPrecision
import info.blockchain.balance.format
import info.blockchain.balance.formatWithUnit
import org.web3j.utils.Convert
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.helperfunctions.InvalidatableLazy
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.ParseException
import java.util.Locale

@Deprecated(
    "Use FiatExchangeRates in conjunction with CryptoValue.toFiat and " +
        "FiatValue.toCrypto and their string formatting methods"
)
class CurrencyFormatManager(
    private val currencyState: CurrencyState,
    private val exchangeRateDataManager: ExchangeRateDataManager,
    private val prefs: PersistentPrefs,
    private val currencyFormatUtil: CurrencyFormatUtil,
    private val locale: Locale
) {

    private val invalidatable = InvalidatableLazy {
        prefs.selectedFiatCurrency
    }

    /**
     * Returns the currency's country code
     *
     * @return The currency abbreviation (USD, GBP etc)
     * @see ExchangeRateDataManager.getCurrencyLabels
     */
    val fiatCountryCode: String by invalidatable

    /**
     * Notifies the class that the fiat code has been reset. This allows [fiatCountryCode] to be
     * lazily loaded again in case of update.
     */
    fun invalidateFiatCode() = invalidatable.invalidate()

    // region Selected Coin methods based on CurrencyState.currencyState

    @VisibleForTesting
    fun getConvertedCoinValue(
        coinValue: BigDecimal,
        convertEthDenomination: ETHDenomination? = null,
        convertBtcDenomination: BTCDenomination? = BTCDenomination.SATOSHI
    ): BigDecimal {
        return if (convertEthDenomination != null) {
            when (convertEthDenomination) {
                ETHDenomination.ETH -> coinValue
                else -> coinValue.divide(ETH_DEC.toBigDecimal(), 18, RoundingMode.HALF_UP)
            }
        } else {
            when (convertBtcDenomination) {
                BTCDenomination.BTC -> coinValue
                else -> coinValue.divide(BTC_DEC.toBigDecimal(), 8, RoundingMode.HALF_UP)
            }
        }
    }

    fun getFormattedSelectedCoinValue(coinValue: BigInteger) =
        getFormattedCoinValue(CryptoValue(currencyState.cryptoCurrency, coinValue))

    private fun getFormattedCoinValue(cryptoValue: CryptoValue) =
        cryptoValue.format(precision = FormatPrecision.Full)

    fun getFormattedSelectedCoinValueWithUnit(coinValue: BigInteger) =
        getFormattedCoinValueWithUnit(CryptoValue(currencyState.cryptoCurrency, coinValue))

    private fun getFormattedCoinValueWithUnit(cryptoValue: CryptoValue) =
        cryptoValue.formatWithUnit(precision = FormatPrecision.Full)

    // endregion

    // region Fiat methods
    /**
     * Returns the symbol for the chosen currency, based on the passed currency code and the chosen
     * device [Locale].
     *
     * @param currencyCode The 3-letter currency code, eg. "GBP"
     * @param locale The current device [Locale]
     * @return The correct currency symbol (eg. "$")
     */
    @Deprecated("locale is injected in formatter, not needed",
        replaceWith = ReplaceWith("getFiatSymbol(currencyCode)"))
    fun getFiatSymbol(currencyCode: String, locale: Locale): String =
        currencyFormatUtil.getFiatSymbol(currencyCode, locale)

    fun getFiatSymbol(currencyCode: String): String =
        currencyFormatUtil.getFiatSymbol(currencyCode, locale)

    private fun getFiatValueFromSelectedCoin(
        coinValue: BigDecimal,
        convertEthDenomination: ETHDenomination? = null,
        convertBtcDenomination: BTCDenomination? = null
    ): BigDecimal {
        return if (convertEthDenomination != null) {
            when (currencyState.cryptoCurrency) {
                CryptoCurrency.ETHER -> getFiatValueFromEth(coinValue, convertEthDenomination)
                else -> throw IllegalArgumentException("${currencyState.cryptoCurrency} denomination not supported.")
            }
        } else {
            when (currencyState.cryptoCurrency) {
                CryptoCurrency.BTC -> throw IllegalArgumentException("BTC formatting should be done via CryptoValue.")
                CryptoCurrency.BCH -> getFiatValueFromBch(coinValue, convertBtcDenomination)
                CryptoCurrency.ETHER ->
                    throw IllegalArgumentException("${currencyState.cryptoCurrency} denomination not supported.")
                CryptoCurrency.XLM -> throw IllegalArgumentException("XLM formatting should be done via CryptoValue.")
                CryptoCurrency.PAX -> throw IllegalArgumentException("PAX formatting should be done via CryptoValue.")
                CryptoCurrency.STX -> TODO("STUB: STX NOT IMPLEMENTED")
            }.exhaustive
        }
    }

    private fun getFiatValueFromBch(
        coinValue: BigDecimal,
        convertBtcDenomination: BTCDenomination? = BTCDenomination.SATOSHI
    ): BigDecimal {
        val fiatUnit = fiatCountryCode

        val sanitizedDenomination = when (convertBtcDenomination) {
            BTCDenomination.BTC -> coinValue
            else -> coinValue.divide(BTC_DEC.toBigDecimal(), 8, RoundingMode.HALF_UP)
        }
        return exchangeRateDataManager.getFiatFromBch(sanitizedDenomination, fiatUnit)
    }

    private fun getFiatValueFromEth(
        coinValue: BigDecimal,
        convertEthDenomination: ETHDenomination?
    ): BigDecimal {
        val fiatUnit = fiatCountryCode

        val sanitizedDenomination = when (convertEthDenomination) {
            ETHDenomination.ETH -> coinValue
            else -> coinValue.divide(ETH_DEC.toBigDecimal(), 18, RoundingMode.HALF_UP)
        }
        return exchangeRateDataManager.getFiatFromEth(sanitizedDenomination, fiatUnit)
    }

    // Uses the global currencyState.cryptoCurrency internally, which breaks when working
    // with multiple currencies - ie pax and eth fees.
    fun getFormattedFiatValueFromSelectedCoinValue(
        coinValue: BigDecimal,
        convertEthDenomination: ETHDenomination? = null,
        convertBtcDenomination: BTCDenomination? = null
    ): String {
        val fiatUnit = fiatCountryCode
        val fiatBalance = getFiatValueFromSelectedCoin(
            coinValue,
            convertEthDenomination,
            convertBtcDenomination
        )
        return currencyFormatUtil.formatFiat(FiatValue.fromMajor(fiatUnit, fiatBalance))
    }

    /**
     * Accepts a [Double] value in fiat currency and returns a [String] formatted to the region
     * with the correct currency symbol. For example, 1.2345 with country code "USD" and locale
     * [Locale.UK] would return "US$1.23".
     *
     * @param amount The amount of fiat currency to be formatted as a [Double]
     * @param currencyCode The 3-letter currency code, eg. "GBP"
     * @param locale The current device [Locale]
     * @return The formatted currency [String]
     */
    fun getFormattedFiatValueWithSymbol(
        amount: Double,
        currencyCode: String,
        locale: Locale
    ): String = currencyFormatUtil.formatFiatWithSymbol(
        FiatValue.fromMajor(
            currencyCode,
            amount.toBigDecimal()
        ), locale
    )
    // endregion

    // region Coin specific methods
    /**
     * Returns formatted string of supplied coin value.
     * (ie 1,000.00 BTC, 0.0001 BTC)
     *
     * @param coinValue Value of the coin
     * @param coinDenomination Denomination of the coinValue supplied
     * @return BTC decimal formatted amount with appended BTC unit
     */
    @Deprecated("Use getFormattedValueWithUnit")
    fun getFormattedBtcValueWithUnit(
        coinValue: BigDecimal,
        coinDenomination: BTCDenomination
    ): String {
        val value = when (coinDenomination) {
            BTCDenomination.BTC -> coinValue
            else -> coinValue.divide(BTC_DEC.toBigDecimal(), 8, RoundingMode.HALF_UP)
        }

        return currencyFormatUtil.formatBtcWithUnit(value)
    }

    /**
     * Returns formatted string of supplied coin value.
     * (ie 1,000.00 BTC, 0.0001 BCH)
     *
     * @param cryptoValue Value and currency of the coin
     * @return decimal formatted amount with appended unit
     */
    fun getFormattedValueWithUnit(cryptoValue: CryptoValue) = currencyFormatUtil.formatWithUnit(cryptoValue)

    /**
     * Returns formatted string of supplied coin value.
     * (ie 1,000.00 BCH, 0.0001 BCH)
     *
     * @param coinValue Value of the coin
     * @param coinDenomination Denomination of the coinValue supplied
     * @return BTC decimal formatted amount with appended BTC unit
     */
    @Deprecated("Use getFormattedValueWithUnit")
    fun getFormattedBchValueWithUnit(
        coinValue: BigDecimal,
        coinDenomination: BTCDenomination
    ): String {
        val value = when (coinDenomination) {
            BTCDenomination.BTC -> coinValue
            else -> coinValue.divide(BTC_DEC.toBigDecimal(), 8, RoundingMode.HALF_UP)
        }

        return currencyFormatUtil.formatBchWithUnit(value)
    }

    /**
     * Returns formatted string of supplied coin value.
     * (ie 1,000.00, 0.0001)
     *
     * @param coinValue Value of the coin
     * @param coinDenomination Denomination of the coinValue supplied
     * @return BCH decimal formatted amount
     */
    fun getFormattedBchValue(coinValue: BigDecimal, coinDenomination: BTCDenomination): String {
        val value = when (coinDenomination) {
            BTCDenomination.BTC -> coinValue
            else -> coinValue.divide(BTC_DEC.toBigDecimal(), 8, RoundingMode.HALF_UP)
        }

        return CryptoValue.bitcoinCashFromMajor(value).format()
    }

    /**
     * Returns formatted string of supplied coin value.
     * (ie 1,000.00, 0.0001)
     *
     * @param coinValue Value of the coin
     * @param coinDenomination Denomination of the coinValue supplied
     * @return ETH decimal formatted amount
     */
    @Deprecated("Use getFormattedValueWithUnit")
    fun getFormattedEthValue(coinValue: BigDecimal, coinDenomination: ETHDenomination): String {
        val value = when (coinDenomination) {
            ETHDenomination.ETH -> coinValue
            else -> coinValue.divide(ETH_DEC.toBigDecimal(), 18, RoundingMode.HALF_UP)
        }

        return CryptoValue.etherFromMajor(value).format(precision = FormatPrecision.Full)
    }
    // endregion

    // region Convert methods
    /**
     * Returns btc amount from satoshis.
     *
     * @return btc, mbtc or bits relative to what is set in monetaryUtil
     */
    fun getTextFromSatoshis(satoshis: BigInteger, decimalSeparator: String): String {
        var displayAmount = getFormattedSelectedCoinValue(satoshis)
        displayAmount = displayAmount.replace(".", decimalSeparator)
        return displayAmount
    }

    /**
     * Returns amount of satoshis from btc amount. This could be btc, mbtc or bits.
     *
     * @return satoshis
     */
    fun getSatoshisFromText(text: String?, decimalSeparator: String): BigInteger {
        if (text == null || text.isEmpty()) return BigInteger.ZERO

        val amountToSend = stripSeparator(text, decimalSeparator)

        val amount = try {
            java.lang.Double.parseDouble(amountToSend)
        } catch (e: NumberFormatException) {
            0.0
        }

        return BigDecimal.valueOf(amount)
            .multiply(BigDecimal.valueOf(100000000))
            .toBigInteger()
    }

    /**
     * Returns amount of wei from ether amount.
     *
     * @return satoshis
     */
    fun getWeiFromText(text: String?, decimalSeparator: String): BigInteger {
        if (text == null || text.isEmpty()) return BigInteger.ZERO

        val amountToSend = stripSeparator(text, decimalSeparator)
        return Convert.toWei(amountToSend, Convert.Unit.ETHER).toBigInteger()
    }

    private fun stripSeparator(text: String, decimalSeparator: String): String = text.trim { it <= ' ' }
        .replace(" ", "")
        .replace(decimalSeparator, ".")
    // endregion

    companion object {
        private const val BTC_DEC = 1e8
        private const val ETH_DEC = 1e18
    }
}

fun String.toSafeDouble(locale: Locale): Double = try {
    var amount = this
    if (amount.isEmpty()) amount = "0"
    NumberFormat.getInstance(locale).parse(amount).toDouble()
} catch (e: ParseException) {
    0.0
}

fun String.toSafeLong(locale: Locale): Long = try {
    var amount = this
    if (amount.isEmpty()) amount = "0"
    Math.round(NumberFormat.getInstance(locale).parse(amount).toDouble() * 1e8)
} catch (e: ParseException) {
    0L
}