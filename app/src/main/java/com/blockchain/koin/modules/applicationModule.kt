@file:Suppress("USELESS_CAST")

package com.blockchain.koin.modules

import android.content.Context
import com.blockchain.activities.StartOnboarding
import com.blockchain.activities.StartSwap
import com.blockchain.balance.TotalBalance
import com.blockchain.balance.plus
import com.blockchain.kycui.settings.KycStatusHelper
import com.blockchain.kycui.sunriver.SunriverCampaignHelper
import com.blockchain.remoteconfig.CoinSelectionRemoteConfig
import com.blockchain.ui.CurrentContextAccess
import com.blockchain.ui.chooser.AccountListing
import com.blockchain.ui.password.SecondPasswordHandler
import info.blockchain.wallet.util.PrivateKeyFactory
import org.koin.dsl.module.applicationContext
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.data.cache.DynamicFeeCache
import piuk.blockchain.android.data.datamanagers.QrCodeDataManager
import piuk.blockchain.android.data.datamanagers.TransactionListDataManager
import piuk.blockchain.android.deeplink.DeepLinkProcessor
import piuk.blockchain.android.kyc.KycDeepLinkHelper
import piuk.blockchain.android.sunriver.SunRiverCampaignAccountProviderAdapter
import piuk.blockchain.android.sunriver.SunriverDeepLinkHelper
import piuk.blockchain.android.ui.account.SecondPasswordHandlerDialog
import piuk.blockchain.android.ui.auth.FirebaseMobileNoticeRemoteConfig
import piuk.blockchain.android.ui.auth.MobileNoticeRemoteConfig
import piuk.blockchain.android.ui.auth.PinEntryPresenter
import piuk.blockchain.android.ui.balance.BalancePresenter
import piuk.blockchain.android.ui.buysell.createorder.BuySellBuildOrderPresenter
import piuk.blockchain.android.ui.chooser.WalletAccountHelperAccountListingAdapter
import piuk.blockchain.android.ui.confirm.ConfirmPaymentPresenter
import piuk.blockchain.android.ui.dashboard.DashboardPresenter
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementHost
import piuk.blockchain.android.ui.fingerprint.FingerprintHelper
import piuk.blockchain.android.ui.launcher.DeepLinkPersistence
import piuk.blockchain.android.ui.login.ManualPairingPresenter
import piuk.blockchain.android.ui.onboarding.OnBoardingStarter
import piuk.blockchain.android.ui.receive.ReceivePresenter
import piuk.blockchain.android.ui.receive.WalletAccountHelper
import piuk.blockchain.android.ui.send.SendView
import piuk.blockchain.android.ui.send.external.PerCurrencySendPresenter
import piuk.blockchain.android.ui.send.external.SendPresenter
import piuk.blockchain.android.ui.send.strategy.BitcoinCashSendStrategy
import piuk.blockchain.android.ui.send.strategy.BitcoinSendStrategy
import piuk.blockchain.android.ui.send.strategy.EtherSendStrategy
import piuk.blockchain.android.ui.send.strategy.SendStrategy
import piuk.blockchain.android.ui.send.strategy.XlmSendStrategy
import piuk.blockchain.android.ui.send.strategy.PaxSendStrategy
import piuk.blockchain.android.ui.settings.SettingsPresenter
import piuk.blockchain.android.ui.swap.SwapStarter
import piuk.blockchain.android.ui.swipetoreceive.SwipeToReceiveHelper
import piuk.blockchain.android.ui.swipetoreceive.SwipeToReceivePresenter
import piuk.blockchain.android.ui.thepit.PitPermissionsPresenter
import piuk.blockchain.android.ui.thepit.PitVerifyEmailPresenter
import piuk.blockchain.android.ui.transactions.TransactionDetailPresenter
import piuk.blockchain.android.ui.transactions.TransactionHelper
import piuk.blockchain.android.util.OSUtil
import piuk.blockchain.android.util.PrngHelper
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidbuysell.datamanagers.BuyDataManager
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.erc20.Erc20Account
import piuk.blockchain.androidcore.data.erc20.PaxAccount
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.utils.PrngFixer
import piuk.blockchain.androidcoreui.utils.AppUtil
import piuk.blockchain.androidcoreui.utils.DateUtil
import java.util.Locale

val applicationModule = applicationContext {

    factory { OSUtil(get()) }

    factory { StringUtils(get()) }

    bean {
        AppUtil(
            context = get(),
            payloadManager = get(),
            accessState = get(),
            prefs = get()
        )
    }

    factory { get<Context>().resources }

    factory { Locale.getDefault() }

    bean { CurrentContextAccess() }

    context("Payload") {

        factory {
            EthDataManager(get(), get(), get(), get(), get(), get(), get(), get())
        }

        factory("pax") {
            PaxAccount(
                ethDataManager = get(),
                dataStore = get(),
                environmentSettings = get()
            ) as Erc20Account
        }

        factory {
            BchDataManager(get(), get(), get(), get(), get(), get(), get())
        }

        factory {
            BuyDataManager(get(), get(), get(), get(), get())
        }

        factory {
            SwipeToReceiveHelper(
                payloadDataManager = get(),
                prefs = get(),
                ethDataManager = get(),
                bchDataManager = get(),
                stringUtils = get(),
                environmentSettings = get(),
                xlmDataManager = get()
            )
        }

        factory {
            SwipeToReceivePresenter(
                qrGenerator = get(),
                swipeToReceiveHelper = get()
            )
        }

        factory {
            WalletAccountHelper(
                payloadManager = get(),
                stringUtils = get(),
                currencyState = get(),
                ethDataManager = get(),
                bchDataManager = get(),
                xlmDataManager = get(),
                environmentSettings = get(),
                exchangeRates = get(),
                paxAccount = get("pax")
            )
        }

        factory { WalletAccountHelperAccountListingAdapter(get()) }
            .bind(AccountListing::class)

        factory {
            SecondPasswordHandlerDialog(get(), get()) as SecondPasswordHandler
        }

        factory { KycStatusHelper(get(), get(), get(), get()) }

        factory { TransactionListDataManager(get(), get(), get(), get(), get(), get(), get()) }

        factory("spendable") { get<TransactionListDataManager>() as TotalBalance }

        @Suppress("ConstantConditionIf")
        factory("all") {
            if (BuildConfig.SHOW_LOCKBOX_BALANCE) {
                get<TotalBalance>("lockbox") + get("spendable")
            } else {
                get("spendable")
            }
        }

        factory {
            FingerprintHelper(
                applicationContext = get(),
                prefs = get(),
                fingerprintAuth = get()
            )
        }

        factory {
            BuySellBuildOrderPresenter(
                coinifyDataManager = get(),
                sendDataManager = get(),
                exchangeService = get(),
                stringUtils = get(),
                currencyFormatManager = get(),
                exchangeRateDataManager = get(),
                feeDataManager = get(),
                dynamicFeeCache = get(),
                payloadDataManager = get(),
                nabuToken = get(),
                nabuDataManager = get(),
                coinSelectionRemoteConfig = get()
            )
        }

        factory {
            TransactionDetailPresenter(
                transactionHelper = get(),
                prefs = get(),
                payloadDataManager = get(),
                stringUtils = get(),
                transactionListDataManager = get(),
                exchangeRateDataManager = get(),
                bchDataManager = get(),
                ethDataManager = get(),
                environmentSettings = get(),
                xlmDataManager = get()
            )
        }

        factory<SendPresenter<SendView>> {
            PerCurrencySendPresenter(
                btcStrategy = get("BTCStrategy"),
                bchStrategy = get("BCHStrategy"),
                etherStrategy = get("EtherStrategy"),
                xlmStrategy = get("XLMStrategy"),
                paxStrategy = get("PaxStrategy"),
                prefs = get(),
                exchangeRates = get(),
                stringUtils = get(),
                envSettings = get(),
                exchangeRateFactory = get()
            )
        }

        factory<SendStrategy<SendView>>("BTCStrategy") {
            BitcoinSendStrategy(
                walletAccountHelper = get(),
                payloadDataManager = get(),
                currencyState = get(),
                prefs = get(),
                exchangeRateFactory = get(),
                stringUtils = get(),
                sendDataManager = get(),
                dynamicFeeCache = get(),
                feeDataManager = get(),
                privateKeyFactory = get(),
                environmentSettings = get(),
                currencyFormatter = get(),
                exchangeRates = get(),
                coinSelectionRemoteConfig = get()
            )
        }

        factory<SendStrategy<SendView>>("BCHStrategy") {
            BitcoinCashSendStrategy(
                walletAccountHelper = get(),
                payloadDataManager = get(),
                prefs = get(),
                stringUtils = get(),
                sendDataManager = get(),
                dynamicFeeCache = get(),
                feeDataManager = get(),
                privateKeyFactory = get(),
                environmentSettings = get(),
                bchDataManager = get(),
                currencyFormatter = get(),
                exchangeRates = get(),
                environmentConfig = get(),
                currencyState = get(),
                coinSelectionRemoteConfig = get()
            )
        }

        factory<SendStrategy<SendView>>("EtherStrategy") {
            EtherSendStrategy(
                walletAccountHelper = get(),
                payloadDataManager = get(),
                ethDataManager = get(),
                stringUtils = get(),
                dynamicFeeCache = get(),
                feeDataManager = get(),
                currencyFormatter = get(),
                exchangeRates = get(),
                environmentConfig = get(),
                currencyState = get(),
                currencyPrefs = get()
            )
        }

        factory<SendStrategy<SendView>>("XLMStrategy") {
            XlmSendStrategy(
                currencyState = get(),
                xlmDataManager = get(),
                xlmFeesFetcher = get(),
                walletOptionsDataManager = get(),
                xlmTransactionSender = get(),
                fiatExchangeRates = get(),
                sendFundsResultLocalizer = get()
            )
        }

        factory<SendStrategy<SendView>>("PaxStrategy") {
            PaxSendStrategy(
                walletAccountHelper = get(),
                payloadDataManager = get(),
                ethDataManager = get(),
                paxAccount = get("pax"),
                stringUtils = get(),
                dynamicFeeCache = get(),
                feeDataManager = get(),
                currencyFormatter = get(),
                exchangeRates = get(),
                environmentConfig = get(),
                currencyState = get(),
                currencyPrefs = get()
            )
        }

        factory { SunriverDeepLinkHelper(get()) }

        factory { KycDeepLinkHelper(get()) }

        factory { DeepLinkProcessor(get(), get(), get()) }

        factory { DeepLinkPersistence(get()) }

        factory { ConfirmPaymentPresenter() }

        factory { SunRiverCampaignAccountProviderAdapter(get()) as SunriverCampaignHelper.XlmAccountProvider }

        factory {
            DashboardPresenter(
                dashboardBalanceCalculator = get(),
                prefs = get(),
                exchangeRateFactory = get(),
                stringUtils = get(),
                accessState = get(),
                buyDataManager = get(),
                rxBus = get(),
                swipeToReceiveHelper = get(),
                currencyFormatManager = get(),
                lockboxDataManager = get(),
                currentTier = get(),
                sunriverCampaignHelper = get(),
                announcements = get()
            )
        }.bind(AnnouncementHost::class)

        factory {
            BalancePresenter(
                exchangeRateDataManager = get(),
                transactionListDataManager = get(),
                ethDataManager = get(),
                paxAccount = get("pax"),
                payloadDataManager = get(),
                buyDataManager = get(),
                stringUtils = get(),
                prefs = get(),
                rxBus = get(),
                currencyState = get(),
                shapeShiftDataManager = get(),
                bchDataManager = get(),
                walletAccountHelper = get(),
                environmentSettings = get(),
                exchangeService = get(),
                coinifyDataManager = get(),
                fiatExchangeRates = get()
            )
        }

        factory {
            QrCodeDataManager()
        }

        factory {
            PitPermissionsPresenter(nabuDataManager = get(), nabuToken = get(), settingsDataManager = get())
        }

        factory {
            ReceivePresenter(
                prefs = get(),
                qrCodeDataManager = get(),
                walletAccountHelper = get(),
                payloadDataManager = get(),
                ethDataStore = get(),
                bchDataManager = get(),
                xlmDataManager = get(),
                environmentSettings = get(),
                currencyState = get(),
                fiatExchangeRates = get()
            )
        }

        factory { TransactionHelper(get(), get()) }

        factory {
            ManualPairingPresenter(
                /* appUtil = */ get(),
                /* authDataManager = */ get(),
                /* payloadDataManager = */ get(),
                /* prefs = */ get()
            )
        }

        factory {
            SettingsPresenter(
                /* fingerprintHelper = */ get(),
                /* authDataManager = */ get(),
                /* settingsDataManager = */ get(),
                /* emailUpdater = */ get(),
                /* payloadManager = */ get(),
                /* payloadDataManager = */ get(),
                /* stringUtils = */ get(),
                /* prefs = */ get(),
                /* accessState = */ get(),
                /* swipeToReceiveHelper = */ get(),
                /* notificationTokenManager = */ get(),
                /* exchangeRateDataManager = */ get(),
                /* currencyFormatManager = */ get(),
                /* kycStatusHelper = */ get()
            )
        }

        factory {
            PinEntryPresenter(
                mAuthDataManager = get(),
                appUtil = get(),
                prefs = get(),
                mPayloadDataManager = get(),
                mStringUtils = get(),
                mFingerprintHelper = get(),
                mAccessState = get(),
                walletOptionsDataManager = get(),
                environmentSettings = get(),
                prngFixer = get(),
                mobileNoticeRemoteConfig = get()
            )
        }

        factory {
            PitVerifyEmailPresenter(
                emailSyncUpdater = get()
            )
        }
    }

    factory {
        FirebaseMobileNoticeRemoteConfig(remoteConfig = get()) as MobileNoticeRemoteConfig
    }

    factory {
        SwapStarter(prefs = get())
    }.bind(StartSwap::class)

    factory {
        OnBoardingStarter()
    }.bind(StartOnboarding::class)

    factory { DateUtil(get()) }

    bean { PrngHelper(get(), get()) as PrngFixer }

    factory { PrivateKeyFactory() }

    bean { DynamicFeeCache() }

    factory { CoinSelectionRemoteConfig(get()) }
}
