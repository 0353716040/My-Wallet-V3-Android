package piuk.blockchain.android.ui.dashboard

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.FiatValue
import java.util.Locale

sealed class PieChartsState {

    data class DataPoint(
        val fiatValue: FiatValue,
        val cryptoValueString: String
    ) {
        val isZero = fiatValue.isZero
        val fiatValueString = fiatValue.toStringWithSymbol(Locale.getDefault())
    }

    data class Coin(
        val spendable: DataPoint,
        val watchOnly: DataPoint
    ) {
        val isZero = spendable.isZero && watchOnly.isZero
    }

    data class Data(
        val bitcoin: Coin,
        val ether: Coin,
        val bitcoinCash: Coin,
        val lumen: Coin
    ) : PieChartsState() {

        operator fun get(cryptoCurrency: CryptoCurrency) =
            when (cryptoCurrency) {
                CryptoCurrency.BTC -> bitcoin
                CryptoCurrency.ETHER -> ether
                CryptoCurrency.BCH -> bitcoinCash
                CryptoCurrency.XLM -> lumen
            }

        private val totalValue =
            bitcoin.spendable.fiatValue +
                bitcoinCash.spendable.fiatValue +
                ether.spendable.fiatValue +
                lumen.spendable.fiatValue

        val totalValueString = totalValue.toStringWithSymbol(Locale.getDefault())

        val isZero = bitcoin.isZero && bitcoinCash.isZero && ether.isZero && lumen.isZero
    }

    object Loading : PieChartsState()
    object Error : PieChartsState()
}