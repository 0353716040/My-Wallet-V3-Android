package info.blockchain.balance

import org.amshove.kluent.`should be`
import org.amshove.kluent.`should throw the Exception`
import org.amshove.kluent.`with message`
import org.junit.Test

class CryptoCurrencyTests {

    @Test
    fun `lowercase btc`() {
        CryptoCurrency.fromSymbol("btc") `should be` CryptoCurrency.BTC
    }

    @Test
    fun `uppercase BTC`() {
        CryptoCurrency.fromSymbol("BTC") `should be` CryptoCurrency.BTC
    }

    @Test
    fun `lowercase bch`() {
        CryptoCurrency.fromSymbol("btc") `should be` CryptoCurrency.BTC
    }

    @Test
    fun `uppercase BCH`() {
        CryptoCurrency.fromSymbol("BCH") `should be` CryptoCurrency.BCH
    }

    @Test
    fun `lowercase eth`() {
        CryptoCurrency.fromSymbol("eth") `should be` CryptoCurrency.ETHER
    }

    @Test
    fun `uppercase ETH`() {
        CryptoCurrency.fromSymbol("ETH") `should be` CryptoCurrency.ETHER
    }

    @Test
    fun `uppercase XLM`() {
        CryptoCurrency.fromSymbol("XLM") `should be` CryptoCurrency.XLM
    }

    @Test
    fun `lowercase xlm`() {
        CryptoCurrency.fromSymbol("xlm") `should be` CryptoCurrency.XLM
    }

    @Test
    fun `uppercase PAX`() {
        CryptoCurrency.fromSymbol("PAX") `should be` CryptoCurrency.PAX
    }

    @Test
    fun `lowercase pax`() {
        CryptoCurrency.fromSymbol("pax") `should be` CryptoCurrency.PAX
    }

    @Test
    fun `mixed case pax`() {
        CryptoCurrency.fromSymbol("Pax") `should be` CryptoCurrency.PAX
    }

    @Test
    fun `null should return null`() {
        CryptoCurrency.fromSymbol(null) `should be` null
    }

    @Test
    fun `empty should return null`() {
        CryptoCurrency.fromSymbol("") `should be` null
    }

    @Test
    fun `not recognised should return null`() {
        CryptoCurrency.fromSymbol("NONE") `should be` null
    }

    @Test
    fun `fromSymbolOrThrow, Ether`() {
        CryptoCurrency.fromSymbolOrThrow("ETH") `should be` CryptoCurrency.ETHER
    }

    @Test
    fun `fromSymbolOrThrow, not recognised should throw`() {
        {
            CryptoCurrency.fromSymbolOrThrow("NONE")
        } `should throw the Exception`
            IllegalArgumentException::class `with message` "Bad currency symbol \"NONE\""
    }

    @Test
    fun `fromSymbolOrThrow, null recognised should throw`() {
        {
            CryptoCurrency.fromSymbolOrThrow(null)
        } `should throw the Exception`
            IllegalArgumentException::class `with message` "Bad currency symbol \"null\""
    }

    @Test
    fun `btc dp is 8`() {
        CryptoCurrency.BTC.dp `should be` 8
        CryptoCurrency.BTC.userDp `should be` 8
    }

    @Test
    fun `bch dp is 8`() {
        CryptoCurrency.BCH.dp `should be` 8
        CryptoCurrency.BCH.userDp `should be` 8
    }

    @Test
    fun `ether dp is 18 and 8 for user`() {
        CryptoCurrency.ETHER.dp `should be` 18
        CryptoCurrency.ETHER.userDp `should be` 8
    }

    @Test
    fun `XLM dp is 7`() {
        CryptoCurrency.XLM.dp `should be` 7
        CryptoCurrency.XLM.userDp `should be` 7
    }

    @Test
    fun `btc required confirmations is 3`() {
        CryptoCurrency.BTC.requiredConfirmations `should be` 3
    }

    @Test
    fun `bch required confirmations is 3`() {
        CryptoCurrency.BCH.requiredConfirmations `should be` 3
    }

    @Test
    fun `ether required confirmations is 12`() {
        CryptoCurrency.ETHER.requiredConfirmations `should be` 12
    }

    @Test
    fun `unit name XLM`() {
        CryptoCurrency.XLM.unit `should be` "Stellar"
    }

    @Test
    fun `unit name PAX`() {
        CryptoCurrency.PAX.unit `should be` "USD PAX"
    }
}