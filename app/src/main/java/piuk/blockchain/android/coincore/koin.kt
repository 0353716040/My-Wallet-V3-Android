package piuk.blockchain.android.coincore

import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.koin.usdtAccount
import org.koin.dsl.module
import piuk.blockchain.android.coincore.alg.AlgoTokens
import piuk.blockchain.android.coincore.bch.BchTokens
import piuk.blockchain.android.coincore.btc.BtcTokens
import piuk.blockchain.android.coincore.erc20.pax.PaxTokens
import piuk.blockchain.android.coincore.erc20.usdt.UsdtTokens
import piuk.blockchain.android.coincore.eth.EthTokens
import piuk.blockchain.android.coincore.stx.StxTokens
import piuk.blockchain.android.coincore.xlm.XlmTokens
import piuk.blockchain.android.repositories.AssetActivityRepository

val coincoreModule = module {

    scope(payloadScopeQualifier) {

        scoped {
            StxTokens(
                rxBus = get(),
                payloadManager = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                crashLogger = get(),
                labels = get()
            )
        }

        scoped {
            BtcTokens(
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                payloadDataManager = get(),
                rxBus = get(),
                custodialManager = get(),
                crashLogger = get(),
                labels = get()
            )
        }

        scoped {
            BchTokens(
                bchDataManager = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                rxBus = get(),
                crashLogger = get(),
                stringUtils = get(),
                custodialManager = get(),
                environmentSettings = get(),
                labels = get()
            )
        }

        scoped {
            XlmTokens(
                rxBus = get(),
                xlmDataManager = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                crashLogger = get(),
                labels = get()
            )
        }

        scoped {
            EthTokens(
                ethDataManager = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                rxBus = get(),
                crashLogger = get(),
                stringUtils = get(),
                custodialManager = get(),
                labels = get()
            )
        }

        scoped {
            PaxTokens(
                rxBus = get(),
                erc20Account = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                stringUtils = get(),
                crashLogger = get(),
                labels = get()
            )
        }

        scoped {
            AlgoTokens(
                rxBus = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                crashLogger = get(),
                labels = get()
            )
        }

        scoped {
            UsdtTokens(
                rxBus = get(),
                erc20Account = get(usdtAccount),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                stringUtils = get(),
                crashLogger = get(),
                labels = get()
            )
        }

        scoped {
            Coincore(
                btcTokens = get(),
                bchTokens = get(),
                ethTokens = get(),
                xlmTokens = get(),
                paxTokens = get(),
                stxTokens = get(),
                algoTokens = get(),
                usdtTokens = get(),
                defaultLabels = get()
            )
        }

        scoped {
            AssetActivityRepository(
                coincore = get(),
                rxBus = get()
            )
        }
    }
}
