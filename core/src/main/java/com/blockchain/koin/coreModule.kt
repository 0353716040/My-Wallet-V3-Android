@file:Suppress("USELESS_CAST")

package com.blockchain.koin

import android.preference.PreferenceManager
import com.blockchain.accounts.AccountList
import com.blockchain.accounts.AllAccountList
import com.blockchain.accounts.AllAccountsImplementation
import com.blockchain.accounts.AsyncAccountList
import com.blockchain.accounts.AsyncAllAccountList
import com.blockchain.accounts.AsyncAllAccountListImplementation
import com.blockchain.accounts.BchAccountListAdapter
import com.blockchain.accounts.BchAsyncAccountListAdapter
import com.blockchain.accounts.BtcAccountListAdapter
import com.blockchain.accounts.BtcAsyncAccountListAdapter
import com.blockchain.accounts.EthAccountListAdapter
import com.blockchain.accounts.EthAsyncAccountListAdapter
import com.blockchain.accounts.PaxAccountListAdapter
import com.blockchain.accounts.PaxAsyncAccountList
import com.blockchain.balance.AsyncAccountBalanceReporter
import com.blockchain.balance.AsyncAddressBalanceReporter
import com.blockchain.balance.BchBalanceAdapter
import com.blockchain.balance.BtcBalanceAdapter
import com.blockchain.balance.EthBalanceAdapter
import com.blockchain.balance.plus
import com.blockchain.datamanagers.AccountLookup
import com.blockchain.datamanagers.AddressResolver
import com.blockchain.datamanagers.DataManagerPayloadDecrypt
import com.blockchain.datamanagers.MaximumSpendableCalculator
import com.blockchain.datamanagers.SelfFeeCalculatingTransactionExecutor
import com.blockchain.datamanagers.TransactionExecutor
import com.blockchain.datamanagers.TransactionExecutorViaDataManagers
import com.blockchain.datamanagers.TransactionExecutorWithoutFees
import com.blockchain.fees.FeeType
import com.blockchain.logging.LastTxUpdateDateOnSettingsService
import com.blockchain.logging.LastTxUpdater
import com.blockchain.logging.NullLogger
import com.blockchain.logging.TimberLogger
import com.blockchain.metadata.MetadataRepository
import com.blockchain.payload.PayloadDecrypt
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.ThePitLinkingPrefs
import com.blockchain.sunriver.XlmHorizonUrlFetcher
import com.blockchain.sunriver.XlmTransactionTimeoutFetcher
import com.blockchain.wallet.DefaultLabels
import com.blockchain.wallet.ResourceDefaultLabels
import com.blockchain.wallet.SeedAccess
import com.blockchain.wallet.SeedAccessWithoutPrompt
import info.blockchain.api.blockexplorer.BlockExplorer
import info.blockchain.wallet.util.PrivateKeyFactory
import org.koin.dsl.module.applicationContext
import piuk.blockchain.androidcore.BuildConfig
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.data.access.AccessStateImpl
import piuk.blockchain.androidcore.data.access.LogoutTimer
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.auth.AuthService
import piuk.blockchain.androidcore.data.bitcoincash.BchDataStore
import piuk.blockchain.androidcore.data.currency.CurrencyFormatManager
import piuk.blockchain.androidcore.data.currency.CurrencyFormatUtil
import piuk.blockchain.androidcore.data.currency.CurrencyState
import piuk.blockchain.androidcore.data.erc20.datastores.Erc20DataStore
import piuk.blockchain.androidcore.data.ethereum.EthereumAccountWrapper
import piuk.blockchain.androidcore.data.ethereum.datastores.EthDataStore
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateService
import piuk.blockchain.androidcore.data.exchangerate.FiatExchangeRates
import piuk.blockchain.androidcore.data.exchangerate.datastore.ExchangeRateDataStore
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.metadata.MetadataManager
import piuk.blockchain.androidcore.data.metadata.MoshiMetadataRepositoryAdapter
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManagerSeedAccessAdapter
import piuk.blockchain.androidcore.data.payload.PayloadService
import piuk.blockchain.androidcore.data.payload.PromptingSeedAccessAdapter
import piuk.blockchain.androidcore.data.payments.PaymentService
import piuk.blockchain.androidcore.data.payments.SendDataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.data.settings.EmailSyncUpdater
import piuk.blockchain.androidcore.data.settings.PhoneNumberUpdater
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import piuk.blockchain.androidcore.data.settings.SettingsEmailAndSyncUpdater
import piuk.blockchain.androidcore.data.settings.SettingsPhoneNumberUpdater
import piuk.blockchain.androidcore.data.settings.SettingsPhoneVerificationQuery
import piuk.blockchain.androidcore.data.settings.SettingsService
import piuk.blockchain.androidcore.data.settings.applyFlag
import piuk.blockchain.androidcore.data.settings.datastore.SettingsDataStore
import piuk.blockchain.androidcore.data.settings.datastore.SettingsMemoryStore
import piuk.blockchain.androidcore.data.transactions.TransactionListStore
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsState
import piuk.blockchain.androidcore.utils.AESUtilWrapper
import piuk.blockchain.androidcore.utils.DeviceIdGenerator
import piuk.blockchain.androidcore.utils.DeviceIdGeneratorImpl
import piuk.blockchain.androidcore.utils.MetadataUtils
import piuk.blockchain.androidcore.utils.PrefsUtil
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.UUIDGenerator
import java.util.UUID

val coreModule = applicationContext {

    bean { RxBus() }

    factory { AuthService(get(), get()) }

    factory { MetadataUtils() }

    factory { PrivateKeyFactory() }

    context("Payload") {

        factory { PayloadService(get()) }

        factory { PayloadDataManager(get(), get(), get(), get(), get()) }

        factory { DataManagerPayloadDecrypt(get(), get()) as PayloadDecrypt }

        factory { PromptingSeedAccessAdapter(PayloadDataManagerSeedAccessAdapter(get()), get()) }
            .bind(SeedAccessWithoutPrompt::class)
            .bind(SeedAccess::class)

        bean { MetadataManager(get(), get(), get()) }

        bean { MoshiMetadataRepositoryAdapter(get(), get()) as MetadataRepository }

        factory { AddressResolver(get(), get(), get()) }

        factory { AccountLookup(get(), get(), get()) }

        factory {
            TransactionExecutorViaDataManagers(
                payloadDataManager = get(),
                ethDataManager = get(),
                erc20Account = get(),
                sendDataManager = get(),
                addressResolver = get(),
                accountLookup = get(),
                defaultAccountDataManager = get(),
                ethereumAccountWrapper = get(),
                xlmSender = get(),
                coinSelectionRemoteConfig = get(),
                analytics = get()
            ) as TransactionExecutor
        }

        factory("Regular") {
            SelfFeeCalculatingTransactionExecutor(
                get(),
                get(),
                get(),
                FeeType.Regular
            ) as TransactionExecutorWithoutFees
        }

        factory("Priority") {
            SelfFeeCalculatingTransactionExecutor(
                get(),
                get(),
                get(),
                FeeType.Priority
            ) as TransactionExecutorWithoutFees
        }

        factory("Regular") {
            get<TransactionExecutorWithoutFees>("Regular") as MaximumSpendableCalculator
        }

        factory("Priority") {
            get<TransactionExecutorWithoutFees>("Priority") as MaximumSpendableCalculator
        }

        factory("BTC") { BtcAccountListAdapter(get()) as AccountList }
        factory("BCH") { BchAccountListAdapter(get()) as AccountList }
        factory("ETH") { EthAccountListAdapter(get()) as AccountList }
        factory("PAX") { PaxAccountListAdapter(get(), get()) as AccountList }

        factory("BTC") { BtcAsyncAccountListAdapter(get()) as AsyncAccountList }
        factory("BCH") { BchAsyncAccountListAdapter(get()) as AsyncAccountList }
        factory("ETH") { EthAsyncAccountListAdapter(EthAccountListAdapter(get())) as AsyncAccountList }
        factory("PAX") { PaxAsyncAccountList(ethDataManager = get(), stringUtils = get()) as AsyncAccountList }

        factory("BTC") { BtcBalanceAdapter(get()) }
            .bind(AsyncAddressBalanceReporter::class)
            .bind(AsyncAccountBalanceReporter::class)
        factory("BCH") { BchBalanceAdapter(get()) }
            .bind(AsyncAddressBalanceReporter::class)
            .bind(AsyncAccountBalanceReporter::class)
        factory("ETH") { EthBalanceAdapter(get()) }
            .bind(AsyncAddressBalanceReporter::class)
            .bind(AsyncAccountBalanceReporter::class)

        factory("all") {
            get<AsyncAccountBalanceReporter>("BTC") +
                    get("BCH") + get("ETH") + get("XLM")
        }

        factory {
            AllAccountsImplementation(
                btcAccountList = get("BTC"),
                bchAccountList = get("BCH"),
                etherAccountList = get("ETH"),
                paxAccountList = get("PAX")
            ) as AllAccountList
        }

        factory {
            AsyncAllAccountListImplementation(
                listOf(
                    get("BTC"),
                    get("ETH"),
                    get("BCH"),
                    get("XLM"),
                    get("PAX")
                )
            ) as AsyncAllAccountList
        }

        bean { EthDataStore() }

        bean { Erc20DataStore() }

        bean { BchDataStore() }

        bean { WalletOptionsState() }

        bean { SettingsDataManager(get(), get(), get()) }

        bean { SettingsService(get()) }

        bean {
            SettingsDataStore(SettingsMemoryStore(), get<SettingsService>().getSettingsObservable())
        }

        factory { WalletOptionsDataManager(get(), get(), get(), get("explorer-url")) }
            .bind(XlmTransactionTimeoutFetcher::class).bind(XlmHorizonUrlFetcher::class)

        factory { ExchangeRateDataManager(get(), get()) }

        bean { ExchangeRateDataStore(get(), get()) }

        factory {
            FiatExchangeRates(
                exchangeRates = get(),
                currencyPrefs = get()
            )
        }

        factory { FeeDataManager(get(), get(), get()) }

        bean { TransactionListStore() }

        factory { CurrencyFormatManager(get(), get(), get(), get(), get()) }

        factory {
            AuthDataManager(
                prefs = get(),
                authService = get(),
                accessState = get(),
                aesUtilWrapper = get(),
                prngHelper = get(),
                crashLogger = get()
            )
        }

        factory { LastTxUpdateDateOnSettingsService(get()) as LastTxUpdater }

        factory { SendDataManager(get(), get(), get()) }

        factory { SettingsPhoneVerificationQuery(get()).applyFlag(get("ff_sms_verification")) }

        factory { SettingsPhoneNumberUpdater(get()) as PhoneNumberUpdater }

        factory { SettingsEmailAndSyncUpdater(get(), get()) as EmailSyncUpdater }
    }

    bean { BlockExplorer(get("explorer"), get("api"), getProperty("api-code")) }

    factory { ExchangeRateService(get()) }

    factory {
        DeviceIdGeneratorImpl(
            ctx = get(),
            analytics = get()
        )
    }.bind(DeviceIdGenerator::class)

    factory {
        object : UUIDGenerator {
            override fun generateUUID(): String = UUID.randomUUID().toString()
        }
    }.bind(UUIDGenerator::class)

    bean {
        PrefsUtil(
            store = PreferenceManager.getDefaultSharedPreferences(/* context = */ get()),
            idGenerator = get(),
            uuidGenerator = get()
        )
    }.bind(PersistentPrefs::class).bind(CurrencyPrefs::class).bind(ThePitLinkingPrefs::class)

    factory { CurrencyFormatUtil() }

    bean { CurrencyState(get()) }

    factory { PaymentService(get(), get(), get()) }

    bean {
        if (BuildConfig.DEBUG)
            TimberLogger()
        else
            NullLogger
    }

    factory { EthereumAccountWrapper() }

    bean {
        AccessStateImpl(
            context = get(),
            prefs = get(),
            rxBus = get(),
            crashLogger = get()
        )
    }.bind(AccessState::class)

    factory {
        val accessState = get<AccessState>()
        object : LogoutTimer {
            override fun start() {
                accessState.startLogoutTimer()
            }

            override fun stop() {
                accessState.stopLogoutTimer()
            }
        } as LogoutTimer
    }

    factory { AESUtilWrapper() }

    factory { ResourceDefaultLabels(get()) as DefaultLabels }
}
