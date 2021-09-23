package piuk.blockchain.android.ui.interest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import info.blockchain.balance.AssetInfo
import com.blockchain.notifications.analytics.LaunchOrigin
import io.reactivex.rxjava3.core.Single
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.CryptoAccount
import com.blockchain.coincore.InterestAccount
import com.blockchain.coincore.SingleAccount
import io.reactivex.rxjava3.disposables.CompositeDisposable
import piuk.blockchain.android.databinding.ActivityInterestDashboardBinding
import piuk.blockchain.android.databinding.ToolbarGeneralBinding
import piuk.blockchain.android.ui.base.BlockchainActivity
import piuk.blockchain.android.ui.customviews.account.AccountSelectSheet
import piuk.blockchain.android.ui.kyc.navhost.KycNavHostActivity
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import piuk.blockchain.android.ui.transactionflow.analytics.InterestAnalytics
import piuk.blockchain.android.ui.transactionflow.TransactionLauncher
import piuk.blockchain.android.util.putAccount
import piuk.blockchain.androidcore.utils.helperfunctions.consume

class InterestDashboardActivity : BlockchainActivity(),
    InterestSummarySheet.Host,
    InterestDashboardFragment.InterestDashboardHost,
    DialogFlow.FlowHost {

    private val binding: ActivityInterestDashboardBinding by lazy {
        ActivityInterestDashboardBinding.inflate(layoutInflater)
    }

    private val txLauncher: TransactionLauncher by inject()
    private val compositeDisposable = CompositeDisposable()

    override val alwaysDisableScreenshots: Boolean
        get() = false

    private val fragment: InterestDashboardFragment by lazy { InterestDashboardFragment.newInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(ToolbarGeneralBinding.bind(binding.root).toolbarGeneral)
        setTitle(R.string.rewards_dashboard_title)
        analytics.logEvent(InterestAnalytics.InterestViewed)

        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment, InterestDashboardFragment::class.simpleName)
            .commitAllowingStateLoss()
    }

    override fun onSupportNavigateUp(): Boolean = consume {
        onBackPressed()
    }

    override fun goToActivityFor(account: BlockchainAccount) {
        val b = Bundle()
        b.putAccount(ACTIVITY_ACCOUNT, account)
        setResult(RESULT_FIRST_USER, Intent().apply {
            putExtras(b)
        })
        finish()
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    override fun goToInterestDeposit(toAccount: InterestAccount) {
        clearBottomSheet()
        require(toAccount is CryptoAccount)
        txLauncher.startFlow(
            activity = this,
            target = toAccount,
            action = AssetAction.InterestDeposit,
            fragmentManager = supportFragmentManager,
            flowHost = this,
            compositeDisposable = compositeDisposable
        )
    }

    override fun goToInterestWithdraw(fromAccount: InterestAccount) {
        clearBottomSheet()
        require(fromAccount is CryptoAccount)
        txLauncher.startFlow(
            activity = this,
            sourceAccount = fromAccount,
            action = AssetAction.InterestWithdraw,
            fragmentManager = supportFragmentManager,
            flowHost = this,
            compositeDisposable = compositeDisposable
        )
    }

    override fun onSheetClosed() {
        // do nothing
    }

    override fun startKyc() {
        analytics.logEvent(InterestAnalytics.InterestDashboardKyc)
        KycNavHostActivity.start(this, CampaignType.Interest)
    }

    override fun showInterestSummarySheet(account: SingleAccount, asset: AssetInfo) {
        showBottomSheet(InterestSummarySheet.newInstance(account, asset))
    }

    override fun startAccountSelection(
        filter: Single<List<BlockchainAccount>>,
        toAccount: SingleAccount
    ) {
        showBottomSheet(
            AccountSelectSheet.newInstance(object : AccountSelectSheet.SelectionHost {
                override fun onAccountSelected(account: BlockchainAccount) {
                    startDeposit(account as SingleAccount, toAccount)
                    analytics.logEvent(
                        InterestAnalytics.InterestDepositClicked(
                            currency = (toAccount as CryptoAccount).asset.networkTicker,
                            origin = LaunchOrigin.SAVINGS_PAGE
                        )
                    )
                }

                override fun onSheetClosed() {
                    // do nothing
                }
            }, filter, R.string.select_deposit_source_title)
        )
    }

    private fun startDeposit(
        fromAccount: SingleAccount,
        toAccount: SingleAccount
    ) {
        txLauncher.startFlow(
            activity = this,
            sourceAccount = fromAccount as CryptoAccount,
            target = toAccount,
            action = AssetAction.InterestDeposit,
            fragmentManager = supportFragmentManager,
            flowHost = this,
            compositeDisposable = compositeDisposable
        )
    }

    companion object {
        const val ACTIVITY_ACCOUNT = "ACTIVITY_ACCOUNT"

        fun newInstance(context: Context) =
            Intent(context, InterestDashboardActivity::class.java)
    }

    override fun onFlowFinished() {
        fragment.refreshBalances()
    }
}