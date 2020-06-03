package piuk.blockchain.android.coincore

import org.koin.dsl.module.applicationContext
import piuk.blockchain.android.coincore.alg.AlgoTokens
import piuk.blockchain.android.coincore.bch.BchTokens
import piuk.blockchain.android.coincore.btc.BtcTokens
import piuk.blockchain.android.coincore.eth.EthTokens
import piuk.blockchain.android.coincore.impl.AssetActivityRepo
import piuk.blockchain.android.coincore.pax.PaxTokens
import piuk.blockchain.android.coincore.stx.StxTokens
import piuk.blockchain.android.coincore.xlm.XlmTokens

val coincoreModule = applicationContext {

    context("Payload") {

        bean {
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

        bean {
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

        bean {
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

        bean {
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

        bean {
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

        bean {
            PaxTokens(
                rxBus = get(),
                paxAccount = get(),
                exchangeRates = get(),
                historicRates = get(),
                currencyPrefs = get(),
                custodialManager = get(),
                stringUtils = get(),
                crashLogger = get(),
                labels = get()
            )
        }

        bean {
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

        bean {
            Coincore(
                btcTokens = get(),
                bchTokens = get(),
                ethTokens = get(),
                xlmTokens = get(),
                paxTokens = get(),
                stxTokens = get(),
                algoTokens = get(),
                defaultLabels = get()
            )
        }

        bean {
            AssetActivityRepo(
                coincore = get(),
                rxBus = get()
            )
        }
    }
}
