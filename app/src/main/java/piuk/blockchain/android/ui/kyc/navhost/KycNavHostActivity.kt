package piuk.blockchain.android.ui.kyc.navhost

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.Tier
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.KYCAnalyticsEvents
import com.blockchain.notifications.analytics.LaunchOrigin
import org.koin.android.ext.android.inject
import piuk.blockchain.android.KycNavXmlDirections
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.databinding.ActivityKycNavHostBinding
import piuk.blockchain.android.ui.base.BaseMvpActivity
import piuk.blockchain.android.ui.customviews.ToastCustom
import piuk.blockchain.android.ui.customviews.toast
import piuk.blockchain.android.ui.kyc.complete.ApplicationCompleteFragment
import piuk.blockchain.android.ui.kyc.email.entry.EmailEntryHost
import piuk.blockchain.android.ui.kyc.email.entry.KycEmailEntryFragmentDirections
import piuk.blockchain.android.util.invisibleIf
import piuk.blockchain.androidcore.utils.helperfunctions.consume
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy

interface StartKyc {
    fun startKycActivity(context: Any, campaignType: CampaignType)
}

internal class KycStarter : StartKyc {
    override fun startKycActivity(context: Any, campaignType: CampaignType) {
        KycNavHostActivity.start(context as Context, campaignType, true)
    }
}

class KycNavHostActivity : BaseMvpActivity<KycNavHostView, KycNavHostPresenter>(),
    KycProgressListener, KycNavHostView {

    private val binding: ActivityKycNavHostBinding by lazy {
        ActivityKycNavHostBinding.inflate(layoutInflater)
    }

    private val kycNavHastPresenter: KycNavHostPresenter by scopedInject()
    private val analytics: Analytics by inject()
    private var navInitialDestination: NavDestination? = null
    private val navController: NavController by lazy {
        (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
    }
    private val currentFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.nav_host)

    override val campaignType by unsafeLazy {
        intent.getSerializableExtra(EXTRA_CAMPAIGN_TYPE) as CampaignType
    }
    override val showTiersLimitsSplash by unsafeLazy {
        intent.getBooleanExtra(EXTRA_SHOW_TIERS_LIMITS_SPLASH, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val title = R.string.identity_verification
        setupToolbar(binding.toolbarKyc, title)
        if (!showTiersLimitsSplash) {
            analytics.logEvent(
                KYCAnalyticsEvents.UpgradeKycVeriffClicked(
                    campaignType.toLaunchOrigin(),
                    Tier.GOLD
                )
            )
        }
        navController.setGraph(R.navigation.kyc_nav, intent.extras)

        onViewReady()
    }

    override fun setHostTitle(title: Int) {
        binding.toolbarKyc.title = getString(title)
    }

    override fun displayLoading(loading: Boolean) {
        binding.frameLayoutFragmentWrapper.invisibleIf(loading)
        binding.progressBarLoadingUser.invisibleIf(!loading)
    }

    override fun showErrorToastAndFinish(message: Int) {
        toast(message, ToastCustom.TYPE_ERROR)
        finish()
    }

    override fun navigate(directions: NavDirections) {
        navController.navigate(directions)
        navInitialDestination = navController.currentDestination
    }

    override fun navigateToKycSplash() {
        navController.navigate(KycNavXmlDirections.actionDisplayKycSplash())
        navInitialDestination = navController.currentDestination
    }

    override fun navigateToResubmissionSplash() {
        navController.navigate(KycNavXmlDirections.actionDisplayResubmissionSplash())
        navInitialDestination = navController.currentDestination
    }

    override fun hideBackButton() {
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    override fun onEmailEntryFragmentShown() {
        binding.toolbarKyc.title = getString(R.string.kyc_email_title)
    }

    override fun onEmailVerified() {
        navigate(
            KycEmailEntryFragmentDirections.actionAfterValidation()
        )
    }

    override fun onEmailVerificationSkipped() {
        throw IllegalStateException("Email must be verified")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        supportFragmentManager.fragments.forEach { fragment ->
            fragment.childFragmentManager.fragments.forEach {
                it.onActivityResult(
                    requestCode,
                    resultCode,
                    data
                )
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean = consume {

        if (flowShouldBeClosedAfterBackAction() || !navController.navigateUp()) {
            finish()
        }
    }

    override fun onBackPressed() {
        if (flowShouldBeClosedAfterBackAction()) {
            finish()
        } else {
            super.onBackPressed()
        }
    }

    private fun flowShouldBeClosedAfterBackAction() =
        // If on final page, close host Activity on navigate up
        currentFragment is ApplicationCompleteFragment ||
            // If not coming from settings, we want the 1st launched screen to be the 1st screen in the stack
            (navInitialDestination != null && navInitialDestination?.id == navController.currentDestination?.id)

    override fun createPresenter(): KycNavHostPresenter = kycNavHastPresenter

    override fun getView(): KycNavHostView = this

    override fun startLogoutTimer() = Unit

    companion object {

        //        const val RESULT_KYC_STX_COMPLETE = 5
        const val RESULT_KYC_FOR_SDD_COMPLETE = 35432
        const val RESULT_KYC_FOR_TIER_COMPLETE = 8954234
        private const val EXTRA_CAMPAIGN_TYPE = "piuk.blockchain.android.EXTRA_CAMPAIGN_TYPE"
        const val EXTRA_SHOW_TIERS_LIMITS_SPLASH = "piuk.blockchain.android.EXTRA_SHOW_TIERS_LIMITS_SPLASH"

        @JvmStatic
        fun start(context: Context, campaignType: CampaignType) {
            intentArgs(context, campaignType)
                .run { context.startActivity(this) }
        }

        @JvmStatic
        fun start(context: Context, campaignType: CampaignType, showLimits: Boolean) {
            intentArgs(context, campaignType, showLimits)
                .run { context.startActivity(this) }
        }

        @JvmStatic
        fun startForResult(activity: Activity, campaignType: CampaignType, requestCode: Int) {
            intentArgs(activity, campaignType)
                .run { activity.startActivityForResult(this, requestCode) }
        }

        @JvmStatic
        fun startForResult(
            fragment: Fragment,
            campaignType: CampaignType,
            requestCode: Int,
            showTiersLimitsSplash: Boolean = false
        ) {
            intentArgs(fragment.requireContext(), campaignType, showTiersLimitsSplash)
                .run { fragment.startActivityForResult(this, requestCode) }
        }

        @JvmStatic
        private fun intentArgs(
            context: Context,
            campaignType: CampaignType,
            showTiersLimitsSplash: Boolean = false
        ): Intent =
            Intent(context, KycNavHostActivity::class.java)
                .apply {
                    putExtra(EXTRA_CAMPAIGN_TYPE, campaignType)
                    putExtra(EXTRA_SHOW_TIERS_LIMITS_SPLASH, showTiersLimitsSplash)
                }

        fun kycStatusUpdated(resultCode: Int) =
            resultCode == RESULT_KYC_FOR_SDD_COMPLETE || resultCode == RESULT_KYC_FOR_TIER_COMPLETE
    }
}

private fun CampaignType.toLaunchOrigin(): LaunchOrigin =
    when (this) {
        CampaignType.Swap -> LaunchOrigin.SWAP
        CampaignType.Blockstack,
        CampaignType.Sunriver -> LaunchOrigin.AIRDROP
        CampaignType.Resubmission -> LaunchOrigin.RESUBMISSION
        CampaignType.SimpleBuy -> LaunchOrigin.SIMPLETRADE
        CampaignType.FiatFunds -> LaunchOrigin.FIAT_FUNDS
        CampaignType.Interest -> LaunchOrigin.SAVINGS
        CampaignType.None -> LaunchOrigin.SETTINGS
    }

interface KycProgressListener : EmailEntryHost {

    val campaignType: CampaignType

    fun setHostTitle(@StringRes title: Int)

    fun hideBackButton()
}