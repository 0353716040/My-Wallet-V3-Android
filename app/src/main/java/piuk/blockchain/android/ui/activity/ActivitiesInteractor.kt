package piuk.blockchain.android.ui.activity

import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.SimpleBuyAnalytics
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.swap.nabu.datamanagers.CustodialWalletManager
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.CryptoAccount
import timber.log.Timber

class ActivitiesInteractor(
    private val coincore: Coincore,
    private val custodialWalletManager: CustodialWalletManager,
    private val simpleBuyPrefs: SimpleBuyPrefs,
    private val analytics: Analytics
) {
    fun getActivityForAccount(account: CryptoAccount): Single<ActivitySummaryList> =
        account.activity

    fun getDefaultAccount(): Single<CryptoAccount> =
        Single.just(coincore.allWallets)

    fun cancelSimpleBuyOrder(orderId: String): Disposable? {
        return custodialWalletManager.deleteBuyOrder(orderId)
                .subscribeBy(
                    onComplete = { simpleBuyPrefs.clearState() },
                    onError = { error ->
                        analytics.logEvent(SimpleBuyAnalytics.BANK_DETAILS_CANCEL_ERROR)
                        Timber.e(error)
                    }
                )

    }
}
