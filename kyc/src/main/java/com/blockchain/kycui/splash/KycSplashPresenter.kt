package com.blockchain.kycui.splash

import com.blockchain.BaseKycPresenter
import com.blockchain.kyc.models.nabu.Kyc2TierState
import com.blockchain.kyc.services.nabu.NabuCoinifyAccountCreator
import com.blockchain.kycui.navhost.models.CampaignType
import com.blockchain.kycui.reentry.KycNavigator
import com.blockchain.kycui.settings.KycStatusHelper
import com.blockchain.nabu.NabuToken
import info.blockchain.wallet.exceptions.ApiException
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidbuysell.models.coinify.exceptions.CoinifyApiException
import piuk.blockchain.kyc.R
import timber.log.Timber

class KycSplashPresenter(
    nabuToken: NabuToken,
    private val kycStatusHelper: KycStatusHelper,
    private val kycNavigator: KycNavigator,
    private val nabuCoinifyAccountCreator: NabuCoinifyAccountCreator,
    private val stringUtils: StringUtils
) : BaseKycPresenter<KycSplashView>(nabuToken) {

    override fun onViewReady() {}

    fun onCTATapped(campaignType: CampaignType) {
        if (campaignType != CampaignType.BuySell) {
            goToNextKycStep()
            return
        }

        compositeDisposable += kycStatusHelper.getKyc2TierStatus()
            .subscribeOn(Schedulers.io())
            .doOnSubscribe { view.displayLoading(true) }
            .doAfterTerminate { view.displayLoading(false) }
            .flatMap {
                if (
                    it == Kyc2TierState.Tier2Approved ||
                    it == Kyc2TierState.Tier1InReview ||
                    it == Kyc2TierState.Tier1Approved
                ) {
                    nabuCoinifyAccountCreator.createCoinifyAccountIfNeeded()
                        .toSingleDefault(it)
                } else {
                    Single.just(it)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onError = {
                    Timber.e(it)
                    if (it is CoinifyApiException) {
                        view.showError(it.getErrorDescription())
                    } else if (it is ApiException && it.isMailNotVerifiedException) {
                        view.onEmailNotVerified()
                    } else {
                        view.showError(stringUtils.getString(R.string.kyc_non_specific_server_error))
                    }
                },
                onSuccess = {
                    if (it == Kyc2TierState.Tier2Approved) {
                        view.goToBuySellView()
                    } else {
                        goToNextKycStep()
                    }
                }
            )
    }

    private fun goToNextKycStep() {
        compositeDisposable += kycNavigator.findNextStep()
            .subscribeBy(
                onError = { Timber.e(it) },
                onSuccess = { view.goToNextKycStep(it) }
            )
    }
}