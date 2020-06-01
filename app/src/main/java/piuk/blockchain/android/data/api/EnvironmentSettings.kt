package piuk.blockchain.android.data.api

import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.api.Environment
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.params.BitcoinCashMainNetParams
import org.bitcoinj.params.BitcoinCashTestNet3Params
import org.bitcoinj.params.BitcoinMainNetParams
import org.bitcoinj.params.BitcoinTestNet3Params
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.data.api.bitpay.BITPAY_LIVE_BASE
import piuk.blockchain.androidcore.data.api.EnvironmentConfig

class EnvironmentSettings : EnvironmentConfig {

    override fun websocketUrl(currency: CryptoCurrency) =
        when (currency) {
            CryptoCurrency.BTC -> BuildConfig.BITCOIN_WEBSOCKET_URL
            CryptoCurrency.ETHER -> BuildConfig.ETHEREUM_WEBSOCKET_URL
            CryptoCurrency.BCH -> BuildConfig.BITCOIN_CASH_WEBSOCKET_URL
            CryptoCurrency.XLM -> BuildConfig.BITCOIN_CASH_WEBSOCKET_URL
            CryptoCurrency.PAX -> TODO("PAX is not yet supported - AND-2003")
            CryptoCurrency.STX -> TODO("STUB: STX NOT IMPLEMENTED")
            CryptoCurrency.ALG -> TODO("STUB: ALG NOT IMPLEMENTED")
        }

    override fun shouldShowDebugMenu(): Boolean = BuildConfig.FLAVOR != "envProd"

    override val environment: Environment = Environment.fromString(BuildConfig.ENVIRONMENT)

    override val explorerUrl: String = BuildConfig.EXPLORER_URL

    override val apiUrl: String = BuildConfig.API_URL
    override val everypayHostUrl: String = BuildConfig.EVERYPAY_HOST_URL

    override val bitpayUrl: String = BITPAY_LIVE_BASE

    override val bitcoinNetworkParameters: NetworkParameters
        get() = when (environment) {
            Environment.TESTNET -> BitcoinTestNet3Params.get()
            else -> BitcoinMainNetParams.get()
        }

    override val bitcoinCashNetworkParameters: NetworkParameters
        get() = when (environment) {
            Environment.TESTNET -> BitcoinCashTestNet3Params.get()
            else -> BitcoinCashMainNetParams.get()
        }
}
