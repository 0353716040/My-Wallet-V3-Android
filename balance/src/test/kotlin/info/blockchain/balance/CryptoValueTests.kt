package info.blockchain.balance

import org.amshove.kluent.`should be`
import org.amshove.kluent.`should equal`
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class CryptoValueTests {

    @Test
    fun `zero btc`() {
        CryptoValue.ZeroBtc `should equal` CryptoValue(CryptoCurrency.BTC, BigInteger.ZERO)
    }

    @Test
    fun `zero btc function`() {
        CryptoValue.zero(CryptoCurrency.BTC) `should be` CryptoValue.ZeroBtc
    }

    @Test
    fun `zero bch`() {
        CryptoValue.ZeroBch `should equal` CryptoValue(CryptoCurrency.BCH, BigInteger.ZERO)
    }

    @Test
    fun `zero bch function`() {
        CryptoValue.zero(CryptoCurrency.BCH) `should be` CryptoValue.ZeroBch
    }

    @Test
    fun `zero eth`() {
        CryptoValue.ZeroEth `should equal` CryptoValue(CryptoCurrency.ETHER, BigInteger.ZERO)
    }

    @Test
    fun `zero eth function`() {
        CryptoValue.zero(CryptoCurrency.ETHER) `should be` CryptoValue.ZeroEth
    }

    @Test
    fun `zero pax function`() {
        CryptoValue.zero(CryptoCurrency.PAX) `should be` CryptoValue.ZeroPax
    }

    @Test
    fun `toBigDecimal BTC`() {
        CryptoValue.bitcoinFromSatoshis(12345678901L).toBigDecimal() `should equal` BigDecimal("123.45678901")
    }

    @Test
    fun `toBigDecimal BCH`() {
        CryptoValue.bitcoinCashFromSatoshis(234L).toBigDecimal() `should equal` BigDecimal("0.00000234")
    }

    @Test
    fun `toBigDecimal ETH`() {
        CryptoValue(
            CryptoCurrency.ETHER,
            234L.toBigInteger()
        ).toBigDecimal() `should equal` BigDecimal("0.000000000000000234")
    }

    @Test
    fun `toBigDecimal keeps all trailing 0s`() {
        CryptoValue(
            CryptoCurrency.BTC,
            10000000000L.toBigInteger()
        ).toBigDecimal() `should equal` BigDecimal("100.00000000")
    }

    @Test
    fun `toMajorUnit Double`() {
        CryptoValue(CryptoCurrency.BTC, 12300001234L.toBigInteger()).toMajorUnitDouble() `should equal` 123.00001234
    }

    @Test
    fun `zero is not positive`() {
        CryptoValue.ZeroBtc.isPositive `should be` false
    }

    @Test
    fun `1 Satoshi is positive`() {
        CryptoValue.bitcoinFromSatoshis(1).isPositive `should be` true
    }

    @Test
    fun `2 Satoshis is positive`() {
        CryptoValue.bitcoinFromSatoshis(2).isPositive `should be` true
    }

    @Test
    fun `-1 Satoshi is not positive`() {
        CryptoValue.bitcoinFromSatoshis(-1).isPositive `should be` false
    }

    @Test
    fun `zero isZero`() {
        CryptoValue.ZeroBtc.isZero `should be` true
    }

    @Test
    fun `1 satoshi is not isZero`() {
        CryptoValue.bitcoinFromSatoshis(1).isZero `should be` false
    }

    @Test
    fun `1 wei is not isZero`() {
        CryptoValue.fromMinor(CryptoCurrency.ETHER, BigInteger.ONE).isZero `should be` false
    }

    @Test
    fun `0 wei is isZero`() {
        CryptoValue.fromMinor(CryptoCurrency.ETHER, BigInteger.ZERO).isZero `should be` true
    }

    @Test
    fun `amount is the minor part of the currency`() {
        CryptoValue(CryptoCurrency.BTC, 1234.toBigInteger()).toBigInteger() `should equal` 1234L.toBigInteger()
    }

    @Test
    fun `amount is the total minor part of the currency`() {
        2L.ether().toBigInteger() `should equal` 2e18.toBigDecimal().toBigIntegerExact()
    }

    @Test
    fun `amount when created from satoshis`() {
        CryptoValue.bitcoinFromSatoshis(4567L).apply {
            currency `should equal` CryptoCurrency.BTC
            toBigInteger() `should equal` 4567.toBigInteger()
        }
    }

    @Test
    fun `amount when created from satoshis big integer`() {
        CryptoValue.fromMinor(CryptoCurrency.BTC, 4567.toBigInteger()).apply {
            currency `should equal` CryptoCurrency.BTC
            toBigInteger() `should equal` 4567.toBigInteger()
        }
    }

    @Test
    fun `amount of Cash when created from satoshis`() {
        CryptoValue.bitcoinCashFromSatoshis(45678L).apply {
            currency `should equal` CryptoCurrency.BCH
            toBigInteger() `should equal` 45678.toBigInteger()
        }
    }

    @Test
    fun `amount of Cash when created from satoshis big integer`() {
        CryptoValue.fromMinor(CryptoCurrency.BCH, 1234L.toBigInteger()).apply {
            currency `should equal` CryptoCurrency.BCH
            toBigInteger() `should equal` 1234.toBigInteger()
        }
    }
}
