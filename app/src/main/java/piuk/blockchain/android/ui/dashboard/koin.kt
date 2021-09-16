package piuk.blockchain.android.ui.dashboard

import com.blockchain.koin.interestAccountFeatureFlag
import com.blockchain.koin.payloadScopeQualifier
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.koin.dsl.bind
import org.koin.dsl.module
import piuk.blockchain.android.ui.dashboard.assetdetails.AssetDetailsInteractor
import piuk.blockchain.android.ui.dashboard.assetdetails.AssetDetailsModel
import piuk.blockchain.android.ui.dashboard.assetdetails.AssetDetailsState
import piuk.blockchain.android.ui.dashboard.model.PortfolioActionAdapter
import piuk.blockchain.android.ui.dashboard.model.PortfolioModel
import piuk.blockchain.android.ui.dashboard.model.PortfolioState
import piuk.blockchain.android.ui.dashboard.model.PricesActionAdapter
import piuk.blockchain.android.ui.dashboard.model.PricesModel
import piuk.blockchain.android.ui.dashboard.model.PricesState
import piuk.blockchain.android.ui.transfer.AccountsSorting
import piuk.blockchain.android.ui.transfer.DashboardAccountsSorting

val dashboardModule = module {

    scope(payloadScopeQualifier) {

        factory {
            PortfolioModel(
                initialState = PortfolioState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                actionAdapter = get(),
                environmentConfig = get(),
                crashLogger = get()
            )
        }

        factory {
            PortfolioActionAdapter(
                coincore = get(),
                payloadManager = get(),
                custodialWalletManager = get(),
                simpleBuyPrefs = get(),
                analytics = get(),
                crashLogger = get(),
                linkedBanksFactory = get(),
                gatedFeatures = get()
            )
        }

        factory {
            PricesModel(
                initialState = PricesState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                actionAdapter = get(),
                environmentConfig = get(),
                crashLogger = get()
            )
        }

        factory {
            PricesActionAdapter(
                coincore = get(),
                exchangeRates = get()
            )
        }

        scoped {
            AssetDetailsModel(
                initialState = AssetDetailsState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                interactor = get(),
                environmentConfig = get(),
                crashLogger = get()
            )
        }

        factory {
            AssetDetailsInteractor(
                interestFeatureFlag = get(interestAccountFeatureFlag),
                dashboardPrefs = get(),
                coincore = get(),
                custodialWalletManager = get()
            )
        }

        factory {
            BalanceAnalyticsReporter(
                analytics = get(),
                coincore = get()
            )
        }

        factory {
            DashboardAccountsSorting(
                dashboardPrefs = get(),
                assetCatalogue = get()
            )
        }.bind(AccountsSorting::class)
    }
}
