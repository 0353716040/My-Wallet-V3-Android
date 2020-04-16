package piuk.blockchain.android.simplebuy

import androidx.annotation.VisibleForTesting
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.swap.nabu.datamanagers.BuyOrder
import com.blockchain.swap.nabu.datamanagers.CustodialWalletManager
import com.blockchain.swap.nabu.datamanagers.OrderState
import com.google.gson.Gson
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import piuk.blockchain.androidcore.utils.extensions.flatMapBy
import timber.log.Timber
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

// Ensure that the local and remote SimpleBuy state is the same.
// Resolution strategy is:
//  - check simple buy is enabled
//  - inflate the local state, if any
//  - fetch the remote state, if any
//      - if the remote state is the same as the local state, then do nothing
//      - if the remote state exists and the local state is in an earlier stage, use the remote state
//      - if the remote state and the local state refer to the same order (id) and the remote state
//        is completed/error/cancel, then wipe the local state
//

interface SimpleBuyPrefsStateAdapter {
    fun fetch(): SimpleBuyState?
    fun update(newState: SimpleBuyState)
    fun clear()
}

enum class SimpleBuySyncEvent {
    SYNC_COMPLETE
}

internal class SimpleBuyInflateAdapter(
    private val prefs: SimpleBuyPrefs,
    private val gson: Gson
) : SimpleBuyPrefsStateAdapter {
    override fun fetch(): SimpleBuyState? =
        prefs.simpleBuyState()?.let {
            gson.fromJson(it, SimpleBuyState::class.java)
        }

    override fun update(newState: SimpleBuyState) =
        prefs.updateSimpleBuyState(gson.toJson(newState))

    override fun clear() {
        prefs.clearState()
    }
}

class SimpleBuySyncFactory(
    private val custodialWallet: CustodialWalletManager,
    private val availabilityChecker: SimpleBuyAvailability,
    private val localStateAdapter: SimpleBuyPrefsStateAdapter
) {
    private val isEnabled = AtomicBoolean(false)

    fun performSync(): Completable =
        checkEnabled()
            .doOnSuccess { isEnabled.set(it) }
            .flatMapCompletable {
                Timber.d("SB Sync: Enabled == $it")
                if (!it) {
                    // TODO: Handle this better.
                    //  We want to clear local state, but it's currently blocking testing that
                    //  we do because we have intermittent failures in isAvailable checks.
                    //  So comment the clear out for now to facilitate happy path testing
                    // localStateAdapter.clear()
                    Completable.complete()
                } else {
                    syncStates()
                        .doOnSuccess { v ->
                            Timber.d("SB Sync: Success")
                            localStateAdapter.update(v)
                        }
                        .doOnComplete {
                            Timber.d("SB Sync: Complete")
                            localStateAdapter.clear()
                        }
                        .ignoreElement()
                }
            }
            .observeOn(Schedulers.computation())
            .doOnError {
                Timber.d("SB Sync: FAILED because $it")
            }

    fun currentState(): SimpleBuyState? =
        localStateAdapter.fetch().apply {
            Timber.d("SB Sync: state == $this")
        }

    fun isEnabled(): Boolean {
        return isEnabled.get()
    }

    fun lightweightSync(): Completable =
    // If we have a local state in awaiting funds, check the server and clear it if the backend has transitioned
        // to any completed state (pending, cancelled, finished, failed)
        checkEnabled()
            .doOnSuccess { isEnabled.set(it) }
            .flatMapCompletable {
                Timber.d("SB Sync: Enabled == $it")
                if (!it) {
                    Completable.complete()
                } else {
                    maybeInflateLocalState()
                        .flatMapBy(
                            onSuccess = { localState ->
                                if (localState.orderState == OrderState.AWAITING_FUNDS) {
                                    updateWithRemote(localState)
                                        .doOnComplete { localStateAdapter.clear() }
                                        .doOnSuccess { state -> localStateAdapter.update(state) }
                                } else {
                                    Maybe.empty<SimpleBuyState>()
                                }
                            },
                            onError = { Maybe.empty<SimpleBuyState>() }, // Do nothing
                            onComplete = {
                                localStateAdapter.clear()
                                Maybe.empty<SimpleBuyState>()
                            } // No local state. Do nothing
                        )
                        .ignoreElement()
                }
            }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun checkEnabled(): Single<Boolean> =
        availabilityChecker.isAvailable()
            .doOnSuccess { isEnabled.set(it) }

    private fun syncStates(): Maybe<SimpleBuyState> {
        return maybeInflateLocalState()
            .flatMap { updateWithRemote(it) }
            .flatMapBy(
                onSuccess = { checkForRemoteOverride(it) },
                onError = {
                    Maybe.error<SimpleBuyState>(it)
                },
                onComplete = { Maybe.defer { getRemotePendingBuy() } }
            )
    }

    private fun getRemotePendingBuy(): Maybe<SimpleBuyState> {
        return custodialWallet.getAllOutstandingBuyOrders()
            .flatMapMaybe { list ->
                list.sortedByDescending { it.expires }
                    .firstOrNull {
                        it.state == OrderState.AWAITING_FUNDS || it.state == OrderState.PENDING_EXECUTION
                    }?.let {
                        Maybe.just(it.toSimpleBuyState())
                    } ?: Maybe.empty()
            }
    }

    private fun checkForRemoteOverride(localState: SimpleBuyState): Maybe<SimpleBuyState> {
        return if (localState.orderState < OrderState.AWAITING_FUNDS) {
            custodialWallet.getAllOutstandingBuyOrders()
                .flatMapMaybe { list ->
                    list.sortedByDescending { it.expires }
                        .firstOrNull {
                            it.state == OrderState.AWAITING_FUNDS || it.state == OrderState.PENDING_EXECUTION
                        }?.let {
                            Maybe.just(it.toSimpleBuyState())
                        } ?: Maybe.just(localState)
                }
        } else {
            Maybe.just(localState)
        }
    }

    private fun updateWithRemote(localState: SimpleBuyState): Maybe<SimpleBuyState> =
        getRemoteForLocal(localState.id)
            .defaultIfEmpty(localState)
            .map { remoteState ->
                Timber.d("SB Sync: local.state == ${localState.orderState}, remote.state == ${remoteState.orderState}")
                if (localState.orderState < remoteState.orderState) {
                    Timber.d("SB Sync: Take remote")
                    remoteState
                } else {
                    Timber.d("SB Sync: Take local")
                    localState
                }
            }
            .flatMap { state ->
                when (state.orderState) {
                    OrderState.UNINITIALISED,
                    OrderState.INITIALISED,
                    OrderState.PENDING_EXECUTION,
                    OrderState.AWAITING_FUNDS -> Maybe.just(state)
                    OrderState.FINISHED,
                    OrderState.CANCELED,
                    OrderState.FAILED,
                    OrderState.UNKNOWN -> Maybe.empty()
                }
            }

    private fun getRemoteForLocal(id: String?): Maybe<SimpleBuyState> =
        id?.let {
            custodialWallet.getBuyOrder(it)
                .map { order -> order.toSimpleBuyState() }
                .toMaybe()
                .onErrorResumeNext(Maybe.empty<SimpleBuyState>())
        } ?: Maybe.empty()

    private fun maybeInflateLocalState(): Maybe<SimpleBuyState> =
        localStateAdapter.fetch()?.let {
            Maybe.just(it)
        } ?: Maybe.empty()
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun BuyOrder.toSimpleBuyState(): SimpleBuyState =
    SimpleBuyState(
        id = id,
        enteredAmount = fiat.toStringWithoutSymbol()
            .replace(DecimalFormatSymbols(Locale.getDefault()).groupingSeparator.toString(), ""),
        fiatCurrency = fiat.currencyCode,
        selectedCryptoCurrency = crypto.currency,
        orderState = state,
        expirationDate = expires,
        kycVerificationState = KycState.VERIFIED_AND_ELIGIBLE, // This MUST be so if we have an order in process.
        currentScreen = FlowScreen.BANK_DETAILS
    )
