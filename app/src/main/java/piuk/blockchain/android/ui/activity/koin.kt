package piuk.blockchain.android.ui.activity

import io.reactivex.android.schedulers.AndroidSchedulers
import org.koin.dsl.module.applicationContext
import piuk.blockchain.android.ui.activity.detail.ActivityDetailState
import piuk.blockchain.android.ui.activity.detail.ActivityDetailsInteractor
import piuk.blockchain.android.ui.activity.detail.ActivityDetailsModel
import piuk.blockchain.android.ui.activity.detail.TransactionHelper
import piuk.blockchain.android.ui.activity.detail.TransactionInOutMapper

val activitiesModule = applicationContext {

    context("Payload") {

        factory {
            ActivitiesModel(
                initialState = ActivitiesState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                interactor = get()
            )
        }

        factory {
            ActivitiesInteractor(
                coincore = get(),
                activityRepo = get(),
                custodialWalletManager = get(),
                simpleBuyPrefs = get(),
                analytics = get()
            )
        }

        factory {
            ActivityDetailsModel(
                initialState = ActivityDetailState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                interactor = get()
            )
        }

        factory {
            ActivityDetailsInteractor(
                coincore = get(),
                currencyPrefs = get(),
                transactionInputOutputMapper = get(),
                assetActivityRepo = get()
            )
        }

        factory {
            TransactionInOutMapper(
                transactionHelper = get(),
                payloadDataManager = get(),
                stringUtils = get(),
                ethDataManager = get(),
                bchDataManager = get(),
                xlmDataManager = get(),
                environmentSettings = get()
            )
        }

        factory {
            TransactionHelper(
                payloadDataManager = get(),
                bchDataManager = get()
            )
        }
    }
}
