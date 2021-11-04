package piuk.blockchain.android.campaign

import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.models.responses.nabu.CampaignData
import com.blockchain.nabu.models.responses.nabu.KycState
import com.blockchain.nabu.models.responses.nabu.RegisterCampaignRequest
import com.blockchain.nabu.models.responses.nabu.UserState
import com.blockchain.nabu.models.responses.tokenresponse.NabuOfflineTokenResponse
import com.blockchain.sunriver.XlmAccountReference
import com.blockchain.sunriver.XlmDataManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.Singles
import io.reactivex.rxjava3.schedulers.Schedulers
import piuk.blockchain.android.ui.kyc.settings.KycStatusHelper

class SunriverCampaignRegistration(
    private val nabuDataManager: NabuDataManager,
    private val nabuToken: NabuToken,
    private val kycStatusHelper: KycStatusHelper,
    private val xlmDataManager: XlmDataManager
) : CampaignRegistration {

    private fun defaultAccount(): Single<XlmAccountReference> = xlmDataManager.defaultAccount()

    fun getCampaignCardType(): Single<SunriverCardType> =
        getCardsForUserState()

    private fun getCardsForUserState(): Single<SunriverCardType> =
        Singles.zip(
            kycStatusHelper.getUserState(),
            kycStatusHelper.getKycStatus(),
            userIsInCampaign()
        ).map { (userState, kycState, inSunRiverCampaign) ->
            if (kycState == KycState.Verified && inSunRiverCampaign) {
                SunriverCardType.Complete
            } else if (kycState != KycState.Verified &&
                userState == UserState.Created &&
                inSunRiverCampaign
            ) {
                SunriverCardType.FinishSignUp
            } else {
                SunriverCardType.JoinWaitList
            }
        }

    override fun registerCampaign(): Completable =
        registerCampaign(CampaignData(sunriverCampaignName, false))

    override fun registerCampaign(campaignData: CampaignData): Completable =
        defaultAccount().flatMapCompletable { xlmAccount ->
            nabuToken.fetchNabuToken()
                .flatMapCompletable {
                    doRegisterCampaign(it, xlmAccount, campaignData)
                }
        }

    private fun doRegisterCampaign(
        token: NabuOfflineTokenResponse,
        xlmAccount: XlmAccountReference,
        campaignData: CampaignData
    ): Completable =
        nabuDataManager.registerCampaign(
            token,
            RegisterCampaignRequest.registerSunriver(
                xlmAccount.accountId,
                campaignData.newUser
            ),
            campaignData.campaignName
        ).subscribeOn(Schedulers.io())

    override fun userIsInCampaign(): Single<Boolean> =
        getCampaignList().map { it.contains(sunriverCampaignName) }

    private fun getCampaignList(): Single<List<String>> =
        nabuToken.fetchNabuToken().flatMap {
            nabuDataManager.getCampaignList(it)
        }.onErrorReturn { emptyList() }
}

sealed class SunriverCardType {
    object None : SunriverCardType()
    object JoinWaitList : SunriverCardType()
    object FinishSignUp : SunriverCardType()
    object Complete : SunriverCardType()
}
