package piuk.blockchain.android.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.blockchain.extensions.exhaustive
import com.blockchain.koin.mwaFeatureFlag
import com.blockchain.koin.scopedInject
import com.blockchain.notifications.NotificationsUtil
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.notifications.analytics.LaunchOrigin
import com.blockchain.notifications.analytics.NotificationAppOpened
import com.blockchain.notifications.analytics.RequestAnalyticsEvents
import com.blockchain.notifications.analytics.SendAnalytics
import com.blockchain.notifications.analytics.TransactionsAnalyticsEvents
import com.blockchain.notifications.analytics.activityShown
import com.blockchain.remoteconfig.FeatureFlag
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.FiatValue
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CryptoTarget
import piuk.blockchain.android.coincore.NullCryptoAccount
import piuk.blockchain.android.databinding.ActivityMainBinding
import piuk.blockchain.android.scan.QrScanError
import piuk.blockchain.android.scan.QrScanResultProcessor
import piuk.blockchain.android.simplebuy.BuySellClicked
import piuk.blockchain.android.simplebuy.SimpleBuyActivity
import piuk.blockchain.android.simplebuy.SimpleBuyState
import piuk.blockchain.android.simplebuy.SmallSimpleBuyNavigator
import piuk.blockchain.android.ui.activity.ActivitiesFragment
import piuk.blockchain.android.ui.addresses.AccountActivity
import piuk.blockchain.android.ui.airdrops.AirdropCentreActivity
import piuk.blockchain.android.ui.auth.newlogin.AuthNewLoginSheet
import piuk.blockchain.android.ui.backup.BackupWalletActivity
import piuk.blockchain.android.ui.base.MvpActivity
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.ui.customviews.ToastCustom
import piuk.blockchain.android.ui.dashboard.DashboardFragment
import piuk.blockchain.android.ui.home.analytics.SideNavEvent
import piuk.blockchain.android.ui.interest.InterestDashboardActivity
import piuk.blockchain.android.ui.kyc.navhost.KycNavHostActivity
import piuk.blockchain.android.ui.kyc.status.KycStatusActivity
import piuk.blockchain.android.ui.launcher.LauncherActivity
import piuk.blockchain.android.ui.linkbank.BankAuthActivity
import piuk.blockchain.android.ui.linkbank.BankAuthActivity.Companion.LINKED_BANK_CURRENCY
import piuk.blockchain.android.ui.linkbank.BankAuthActivity.Companion.LINKED_BANK_ID_KEY
import piuk.blockchain.android.ui.linkbank.BankAuthSource
import piuk.blockchain.android.ui.linkbank.BankLinkingInfo
import piuk.blockchain.android.ui.linkbank.FiatTransactionState
import piuk.blockchain.android.ui.linkbank.yapily.FiatTransactionBottomSheet
import piuk.blockchain.android.ui.onboarding.OnboardingActivity
import piuk.blockchain.android.ui.pairingcode.PairingBottomSheet
import piuk.blockchain.android.ui.scan.QrExpected
import piuk.blockchain.android.ui.scan.QrScanActivity
import piuk.blockchain.android.ui.scan.QrScanActivity.Companion.getRawScanData
import piuk.blockchain.android.ui.sell.BuySellFragment
import piuk.blockchain.android.ui.settings.SettingsActivity
import piuk.blockchain.android.ui.swap.SwapFragment
import piuk.blockchain.android.ui.thepit.PitLaunchBottomDialog
import piuk.blockchain.android.ui.thepit.PitPermissionsActivity
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import piuk.blockchain.android.ui.transactionflow.TransactionLauncher
import piuk.blockchain.android.ui.transactionflow.analytics.InterestAnalytics
import piuk.blockchain.android.ui.transactionflow.analytics.SwapAnalyticsEvents
import piuk.blockchain.android.ui.transfer.TransferFragment
import piuk.blockchain.android.ui.transfer.analytics.TransferAnalyticsEvent
import piuk.blockchain.android.ui.transfer.receive.ReceiveSheet
import piuk.blockchain.android.ui.upsell.KycUpgradePromptManager
import piuk.blockchain.android.ui.upsell.UpsellHost
import piuk.blockchain.android.urllinks.URL_BLOCKCHAIN_SUPPORT_PORTAL
import piuk.blockchain.android.util.AndroidUtils
import piuk.blockchain.android.util.calloutToExternalSupportLinkDlg
import piuk.blockchain.android.util.getAccount
import piuk.blockchain.android.util.getResolvedDrawable
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import timber.log.Timber
import java.net.URLDecoder

class MainActivity : MvpActivity<MainView, MainPresenter>(),
    HomeNavigator,
    MainView,
    DialogFlow.FlowHost,
    SlidingModalBottomDialog.Host,
    UpsellHost,
    AuthNewLoginSheet.Host,
    SmallSimpleBuyNavigator {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override val presenter: MainPresenter by scopedInject()
    private val qrProcessor: QrScanResultProcessor by scopedInject()
    private val mwaFF: FeatureFlag by inject(mwaFeatureFlag)
    private val txLauncher: TransactionLauncher by inject()

    private val compositeDisposable = CompositeDisposable()

    private var isMWAEnabled: Boolean = false

    override val view: MainView = this

    var drawerOpen = false
        internal set

    private var handlingResult = false

    private val _refreshAnnouncements = PublishSubject.create<Unit>()
    val refreshAnnouncements: Observable<Unit>
        get() = _refreshAnnouncements

    private val toolbar: Toolbar
        get() = binding.toolbarGeneral.toolbarGeneral

    private var activityResultAction: () -> Unit = {}

    private val tabSelectedListener =
        BottomNavigationView.OnNavigationItemSelectedListener { menuItem ->
            if (binding.bottomNavigation.selectedItemId != menuItem.itemId) {
                when (menuItem.itemId) {
                    R.id.nav_home -> {
                        startDashboardFragment()
                    }
                    R.id.nav_activity -> {
                        startActivitiesFragment()
                        analytics.logEvent(TransactionsAnalyticsEvents.TabItemClick)
                    }
                    R.id.nav_swap -> {
                        tryTolaunchSwap()
                        analytics.logEvent(SwapAnalyticsEvents.SwapTabItemClick)
                        analytics.logEvent(SwapAnalyticsEvents.SwapClickedEvent(LaunchOrigin.NAVIGATION))
                    }
                    R.id.nav_buy_and_sell -> {
                        launchSimpleBuySell()
                        analytics.logEvent(RequestAnalyticsEvents.TabItemClicked)
                        analytics.logEvent(BuySellClicked(origin = LaunchOrigin.NAVIGATION))
                    }
                    R.id.nav_transfer -> {
                        analytics.logEvent(
                            TransferAnalyticsEvent.TransferClicked(
                                origin = LaunchOrigin.NAVIGATION,
                                type = TransferAnalyticsEvent.AnalyticsTransferType.RECEIVE
                            )
                        )
                        startTransferFragment()
                    }
                }
            }

            true
        }

    private val currentFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.content_frame)

    internal val activity: Context
        get() = this

    private val menu: Menu
        get() = binding.navigationView.menu

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (intent.hasExtra(NotificationsUtil.INTENT_FROM_NOTIFICATION) &&
            intent.getBooleanExtra(NotificationsUtil.INTENT_FROM_NOTIFICATION, false)
        ) {
            analytics.logEvent(NotificationAppOpened)
        }

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // No-op
            }

            override fun onDrawerOpened(drawerView: View) {
                drawerOpen = true
                analytics.logEvent(SideNavEvent.SideMenuOpenEvent)
            }

            override fun onDrawerClosed(drawerView: View) {
                drawerOpen = false
            }

            override fun onDrawerStateChanged(newState: Int) {
                // No-op
            }
        })

        // Set up toolbar_constraint
        with(toolbar) {
            navigationIcon = this@MainActivity.getResolvedDrawable(R.drawable.vector_menu)
            title = ""
            setSupportActionBar(this)
        }
        // Styling
        binding.bottomNavigation.apply {
            setOnNavigationItemSelectedListener(tabSelectedListener)
            if (savedInstanceState == null) {
                val currentItem = if (intent.getBooleanExtra(START_BUY_SELL_INTRO_KEY, false)) {
                    R.id.nav_buy_and_sell
                } else R.id.nav_home
                selectedItemId = currentItem
            }
        }

        if (intent.hasExtra(SHOW_SWAP) && intent.getBooleanExtra(SHOW_SWAP, false)) {
            startSwapFlow()
        } else if (intent.hasExtra(LAUNCH_AUTH_FLOW) && intent.getBooleanExtra(LAUNCH_AUTH_FLOW, false)) {
            intent.extras?.let {
                showBottomSheet(
                    AuthNewLoginSheet.newInstance(
                        pubKeyHash = it.getString(AuthNewLoginSheet.PUB_KEY_HASH),
                        messageInJson = it.getString(AuthNewLoginSheet.MESSAGE),
                        forcePin = it.getBoolean(AuthNewLoginSheet.FORCE_PIN),
                        originIP = it.getString(AuthNewLoginSheet.ORIGIN_IP),
                        originLocation = it.getString(AuthNewLoginSheet.ORIGIN_LOCATION),
                        originBrowser = it.getString(AuthNewLoginSheet.ORIGIN_BROWSER)
                    )
                )
            }
        }

        compositeDisposable.add(mwaFF.enabled.observeOn(Schedulers.io()).subscribe(
            { result ->
                isMWAEnabled = result
            },
            {
                isMWAEnabled = false
            }
        ))
    }

    override fun onResume() {
        super.onResume()
        activityResultAction().also {
            activityResultAction = {}
        }
        // This can null out in low memory situations, so reset here
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            selectDrawerItem(menuItem)
            true
        }
        presenter.updateTicker()

        if (!handlingResult) {
            resetUi()
        }

        handlingResult = false
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main_activity, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                binding.drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            R.id.action_qr_main -> {
                QrScanActivity.start(this, QrExpected.MAIN_ACTIVITY_QR)
                analytics.logEvent(SendAnalytics.QRButtonClicked)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        handlingResult = true
        // We create a lambda so we handle the result after the view is attached to the presenter (onResume)
        activityResultAction = {
            when (requestCode) {
                QrScanActivity.SCAN_URI_RESULT -> {
                    data.getRawScanData()?.let {
                        val decodeData = URLDecoder.decode(it, "UTF-8")
                        if (resultCode == RESULT_OK) presenter.processScanResult(decodeData)
                    }
                }
                SETTINGS_EDIT,
                ACCOUNT_EDIT,
                KYC_STARTED -> {
                    // Reset state in case of changing currency etc
                    removeFragmentByTag(DashboardFragment::class.java.simpleName)
                    startDashboardFragment()

                    // Pass this result to balance fragment
                    for (fragment in supportFragmentManager.fragments) {
                        fragment.onActivityResult(requestCode, resultCode, data)
                    }
                }
                INTEREST_DASHBOARD -> {
                    if (resultCode == RESULT_FIRST_USER) {
                        data?.let { intent ->
                            val account = intent.extras?.getAccount(InterestDashboardActivity.ACTIVITY_ACCOUNT)
                            removeFragmentByTag(ActivitiesFragment::class.java.simpleName)
                            startActivitiesFragment(account)
                        }
                    }
                }
                BANK_DEEP_LINK_SIMPLE_BUY -> {
                    if (resultCode == RESULT_OK) {
                        launchBuy(data?.getStringExtra(LINKED_BANK_ID_KEY))
                    }
                }
                BANK_DEEP_LINK_SETTINGS -> {
                    if (resultCode == RESULT_OK) {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                }
                BANK_DEEP_LINK_DEPOSIT -> {
                    if (resultCode == RESULT_OK) {
                        launchDashboardFlow(AssetAction.FiatDeposit, data?.getStringExtra(LINKED_BANK_CURRENCY))
                    }
                }
                BANK_DEEP_LINK_WITHDRAW -> {
                    if (resultCode == RESULT_OK) {
                        launchDashboardFlow(AssetAction.Withdraw, data?.getStringExtra(LINKED_BANK_CURRENCY))
                    }
                }
                else -> super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    private fun launchDashboardFlow(action: AssetAction, currency: String?) {
        currency?.let {
            gotoDashboard()
            removeFragmentByTag(DashboardFragment::class.java.simpleName)
            val fragment = DashboardFragment.newInstance(action, it)
            showFragment(fragment)
        }
    }

    override fun onBackPressed() {
        val f = currentFragment
        val backHandled = when {
            drawerOpen -> {
                binding.drawerLayout.closeDrawers()
                true
            }

            f is DashboardFragment -> f.onBackPressed()

            else -> {
                // Switch to dashboard fragment
                startDashboardFragment()
                true
            }
        }

        if (!backHandled) {
            presenter.clearLoginState()
        }
    }

    private fun selectDrawerItem(menuItem: MenuItem) {
        analytics.logEvent(SideNavEvent(menuItem.itemId))
        when (menuItem.itemId) {
            R.id.nav_the_exchange -> presenter.onThePitMenuClicked()
            R.id.nav_airdrops -> AirdropCentreActivity.start(this)
            R.id.nav_addresses -> startActivityForResult(
                Intent(this, AccountActivity::class.java),
                ACCOUNT_EDIT
            )
            R.id.login_web_wallet -> {
                if (isMWAEnabled) {
                    QrScanActivity.start(this, QrExpected.MAIN_ACTIVITY_QR)
                } else {
                    showBottomSheet(PairingBottomSheet())
                }
            }
            R.id.nav_settings -> startActivityForResult(
                Intent(this, SettingsActivity::class.java),
                SETTINGS_EDIT
            )
            R.id.nav_support -> onSupportClicked()
            R.id.nav_logout -> showLogoutDialog()
            R.id.nav_interest -> launchInterestDashboard()
        }
        binding.drawerLayout.closeDrawers()
    }

    override fun showLoading() {
        binding.progress.visible()
    }

    override fun hideLoading() {
        binding.progress.gone()
    }

    override fun launchThePitLinking(linkId: String) {
        PitPermissionsActivity.start(this, linkId)
    }

    override fun launchThePit() {
        PitLaunchBottomDialog.launch(this)
    }

    override fun launchBackupFunds(fragment: Fragment?, requestCode: Int) {
        fragment?.let {
            BackupWalletActivity.startForResult(it, requestCode)
        } ?: BackupWalletActivity.start(this)
    }

    override fun launchSetup2Fa() {
        SettingsActivity.startFor2Fa(this)
    }

    override fun launchVerifyEmail() {
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_EMAIL)
            startActivity(Intent.createChooser(this, getString(R.string.security_centre_email_check)))
        }
    }

    override fun launchSetupFingerprintLogin() {
        OnboardingActivity.launchForFingerprints(this)
    }

    override fun launchReceive() {
        startTransferFragment(TransferFragment.TransferViewType.TYPE_RECEIVE)
    }

    override fun launchSend() {
        startTransferFragment(TransferFragment.TransferViewType.TYPE_SEND)
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this, R.style.AlertDialogStyle)
            .setTitle(R.string.logout_wallet)
            .setMessage(R.string.ask_you_sure_logout)
            .setPositiveButton(R.string.btn_logout) { _, _ ->
                analytics.logEvent(AnalyticsEvents.Logout)
                presenter.unPair()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onSupportClicked() {
        analytics.logEvent(AnalyticsEvents.Support)
        calloutToExternalSupportLinkDlg(this, URL_BLOCKCHAIN_SUPPORT_PORTAL)
    }

    private fun resetUi() {
        // Set selected appropriately.
        with(binding.bottomNavigation) {
            val currentItem = when (currentFragment) {
                is DashboardFragment -> R.id.nav_home
                is ActivitiesFragment -> R.id.nav_activity
                is TransferFragment -> R.id.nav_transfer
                is BuySellFragment -> R.id.nav_buy_and_sell
                is SwapFragment -> R.id.nav_swap
                else -> R.id.nav_home
            }
            selectedItemId = currentItem
        }
    }

    private fun startSingleActivity(clazz: Class<*>) {
        val intent = Intent(this@MainActivity, clazz)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun kickToLauncherPage() {
        startSingleActivity(LauncherActivity::class.java)
    }

    override fun showProgressDialog(message: Int) {
        super.showProgressDialog(message, null)
    }

    override fun hideProgressDialog() {
        super.dismissProgressDialog()
    }

    override fun launchKyc(campaignType: CampaignType) {
        KycNavHostActivity.startForResult(this, campaignType, KYC_STARTED)
    }

    override fun tryTolaunchSwap(
        sourceAccount: CryptoAccount?,
        targetAccount: CryptoAccount?
    ) {
        startSwapFlow(sourceAccount, targetAccount)
    }

    override fun getStartIntent(): Intent {
        return intent
    }

    override fun clearAllDynamicShortcuts() {
        if (AndroidUtils.is25orHigher()) {
            getSystemService(ShortcutManager::class.java)!!.removeAllDynamicShortcuts()
        }
    }

    override fun enableSwapButton(isEnabled: Boolean) {
        with(binding.bottomNavigation) {
            menu.findItem(R.id.nav_swap).isEnabled = isEnabled
        }
    }

    override fun displayDialog(title: Int, message: Int) {
        AlertDialog.Builder(this, R.style.AlertDialogStyle)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    @SuppressLint("CheckResult")
    override fun startTransactionFlowWithTarget(targets: Collection<CryptoTarget>) {
        val currentFragment = this.currentFragment ?: return
        if (targets.size > 1) {
            disambiguateSendScan(targets)
        } else {
            val targetAddress = targets.first()
            qrProcessor.selectSourceAccount(this, targetAddress)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { sourceAccount ->
                        txLauncher.startFlow(
                            activity = this,
                            fragmentManager = currentFragment.childFragmentManager,
                            action = AssetAction.Send,
                            flowHost = this@MainActivity,
                            sourceAccount = sourceAccount,
                            target = targetAddress
                        )
                    },
                    onError = { Timber.e("Unable to select source account for scan") }
                )
        }
    }

    override fun showScanTargetError(error: QrScanError) {
        ToastCustom.makeText(
            this,
            getString(
                when (error.errorCode) {
                    QrScanError.ErrorCode.ScanFailed -> R.string.error_scan_failed_general
                    QrScanError.ErrorCode.BitPayScanFailed -> R.string.error_scan_failed_bitpay
                }
            ),
            ToastCustom.LENGTH_LONG,
            ToastCustom.TYPE_ERROR
        )
    }

    private fun launchBuy(linkedBankId: String?) {
        startActivity(
            SimpleBuyActivity.newInstance(
                context = activity,
                preselectedPaymentMethodId = linkedBankId
            )
        )
    }

    @SuppressLint("CheckResult")
    private fun disambiguateSendScan(targets: Collection<CryptoTarget>) {
        qrProcessor.disambiguateScan(this, targets)
            .subscribeBy(
                onSuccess = {
                    startTransactionFlowWithTarget(listOf(it))
                }
            )
    }

    override fun showHomebrewDebugMenu() {
        menu.findItem(R.id.nav_debug_swap).isVisible = true
    }

    override fun goToTransfer() {
        startTransferFragment()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(LAUNCH_AUTH_FLOW, false)) {
            intent.extras?.let {
                showBottomSheet(
                    AuthNewLoginSheet.newInstance(
                        pubKeyHash = it.getString(AuthNewLoginSheet.PUB_KEY_HASH),
                        messageInJson = it.getString(AuthNewLoginSheet.MESSAGE),
                        forcePin = it.getBoolean(AuthNewLoginSheet.FORCE_PIN),
                        originIP = it.getString(AuthNewLoginSheet.ORIGIN_IP),
                        originLocation = it.getString(AuthNewLoginSheet.ORIGIN_LOCATION),
                        originBrowser = it.getString(AuthNewLoginSheet.ORIGIN_BROWSER)
                    )
                )
            }
        }
    }

    override fun navigateToBottomSheet(bottomSheet: BottomSheetDialogFragment) {
        clearBottomSheet()
        showBottomSheet(bottomSheet)
    }

    private fun startTransferFragment(
        viewToShow: TransferFragment.TransferViewType = TransferFragment.TransferViewType.TYPE_SEND
    ) {
        setCurrentTabItem(R.id.nav_transfer)
        toolbar.title = getString(R.string.transfer)

        val transferFragment = TransferFragment.newInstance(viewToShow)
        showFragment(transferFragment)
    }

    private fun startSwapFlow(sourceAccount: CryptoAccount? = null, destinationAccount: CryptoAccount? = null) {
        if (sourceAccount == null && destinationAccount == null) {
            setCurrentTabItem(R.id.nav_swap)
            toolbar.title = getString(R.string.common_swap)
            val swapFragment = SwapFragment.newInstance()
            showFragment(swapFragment)
        } else if (sourceAccount != null) {
            txLauncher.startFlow(
                activity = this,
                sourceAccount = sourceAccount,
                target = destinationAccount ?: NullCryptoAccount(),
                action = AssetAction.Swap,
                fragmentManager = supportFragmentManager,
                flowHost = this@MainActivity
            )
        }
    }

    override fun gotoDashboard() {
        setCurrentTabItem(R.id.nav_home)
    }

    private fun startDashboardFragment() {
        runOnUiThread {
            val fragment = DashboardFragment.newInstance()
            showFragment(fragment)
            setCurrentTabItem(R.id.nav_home)
            toolbar.title = getString(R.string.dashboard_title)
        }
    }

    override fun resumeSimpleBuyKyc() {
        startActivity(
            SimpleBuyActivity.newInstance(
                context = this,
                launchKycResume = true
            )
        )
    }

    override fun startSimpleBuy(asset: AssetInfo) {
        startActivity(
            SimpleBuyActivity.newInstance(
                context = this,
                launchFromNavigationBar = true,
                asset = asset
            )
        )
    }

    override fun startInterestDashboard() {
        launchInterestDashboard()
    }

    private fun launchInterestDashboard() {
        startActivityForResult(
            InterestDashboardActivity.newInstance(this), INTEREST_DASHBOARD
        )
        analytics.logEvent(InterestAnalytics.InterestClicked)
    }

    private fun startActivitiesFragment(account: BlockchainAccount? = null) {
        setCurrentTabItem(R.id.nav_activity)
        val fragment = ActivitiesFragment.newInstance(account)
        showFragment(fragment)
        toolbar.title = ""
        analytics.logEvent(activityShown(account?.label ?: "All Wallets"))
    }

    override fun refreshAnnouncements() {
        _refreshAnnouncements.onNext(Unit)
    }

    override fun launchPendingVerificationScreen(campaignType: CampaignType) {
        KycStatusActivity.start(this, campaignType)
    }

    override fun shouldIgnoreDeepLinking() =
        (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0

    private fun removeFragmentByTag(tag: String) {
        supportFragmentManager.findFragmentByTag(tag)?.let { fragment ->
            supportFragmentManager.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
        }
    }

    private fun showFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        val primaryFragment = supportFragmentManager.primaryNavigationFragment
        primaryFragment?.let {
            transaction.hide(it)
        }

        val tag = fragment.javaClass.simpleName
        var tempFragment = supportFragmentManager.findFragmentByTag(tag)
        
        if (tempFragment == null) {
            tempFragment = fragment
            transaction.add(R.id.content_frame, tempFragment, tag)
        } else {
            transaction.show(tempFragment)
        }

        transaction.setPrimaryNavigationFragment(tempFragment)
        transaction.setReorderingAllowed(true)
        transaction.commitNowAllowingStateLoss()
    }

    /*** Silently switch the current tab in the tab_bar */
    private fun setCurrentTabItem(@IdRes item: Int) {
        binding.bottomNavigation.apply {
            setOnNavigationItemSelectedListener(null)
            selectedItemId = item
            setOnNavigationItemSelectedListener(tabSelectedListener)
        }
    }

    companion object {

        val TAG: String = MainActivity::class.java.simpleName
        const val START_BUY_SELL_INTRO_KEY = "START_BUY_SELL_INTRO_KEY"
        const val SHOW_SWAP = "SHOW_SWAP"
        const val LAUNCH_AUTH_FLOW = "LAUNCH_AUTH_FLOW"
        const val ACCOUNT_EDIT = 2008
        const val SETTINGS_EDIT = 2009
        const val KYC_STARTED = 2011
        const val INTEREST_DASHBOARD = 2012
        const val BANK_DEEP_LINK_SIMPLE_BUY = 2013
        const val BANK_DEEP_LINK_SETTINGS = 2014
        const val BANK_DEEP_LINK_DEPOSIT = 2015
        const val BANK_DEEP_LINK_WITHDRAW = 2021

        fun start(context: Context, bundle: Bundle) {
            Intent(context, MainActivity::class.java).apply {
                putExtras(bundle)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)

                context.startActivity(this)
            }
        }
    }

    override fun launchSimpleBuySell(viewType: BuySellFragment.BuySellViewType, asset: AssetInfo?) {
        setCurrentTabItem(R.id.nav_buy_and_sell)
        toolbar.title = getString(R.string.buy_and_sell)
        val buySellFragment = BuySellFragment.newInstance(asset, viewType)
        showFragment(buySellFragment)
    }

    override fun performAssetActionFor(action: AssetAction, account: BlockchainAccount) =
        presenter.validateAccountAction(action, account)

    override fun launchUpsellAssetAction(
        upsell: KycUpgradePromptManager.Type,
        action: AssetAction,
        account: BlockchainAccount
    ) = replaceBottomSheet(KycUpgradePromptManager.getUpsellSheet(upsell))

    override fun launchAssetAction(
        action: AssetAction,
        account: BlockchainAccount
    ) = when (action) {
        AssetAction.Receive -> replaceBottomSheet(ReceiveSheet.newInstance(account as CryptoAccount))
        AssetAction.Swap -> tryTolaunchSwap(sourceAccount = account as CryptoAccount)
        AssetAction.ViewActivity -> startActivitiesFragment(account)
        else -> {
        }
    }

    override fun launchOpenBankingLinking(bankLinkingInfo: BankLinkingInfo) {
        startActivityForResult(
            BankAuthActivity.newInstance(bankLinkingInfo.linkingId, bankLinkingInfo.bankAuthSource, this),
            when (bankLinkingInfo.bankAuthSource) {
                BankAuthSource.SIMPLE_BUY -> BANK_DEEP_LINK_SIMPLE_BUY
                BankAuthSource.SETTINGS -> BANK_DEEP_LINK_SETTINGS
                BankAuthSource.DEPOSIT -> BANK_DEEP_LINK_DEPOSIT
                BankAuthSource.WITHDRAW -> BANK_DEEP_LINK_WITHDRAW
            }.exhaustive
        )
    }

    override fun launchSimpleBuyFromDeepLinkApproval() {
        startActivity(SimpleBuyActivity.newInstance(this, launchFromApprovalDeepLink = true))
    }

    override fun handlePaymentForCancelledOrder(state: SimpleBuyState) =
        replaceBottomSheet(
            FiatTransactionBottomSheet.newInstance(
                state.fiatCurrency, getString(R.string.yapily_payment_to_fiat_wallet_title, state.fiatCurrency),
                getString(
                    R.string.yapily_payment_to_fiat_wallet_subtitle,
                    state.selectedCryptoAsset?.ticker ?: getString(
                        R.string.yapily_payment_to_fiat_wallet_default
                    ),
                    state.fiatCurrency
                ),
                FiatTransactionState.SUCCESS
            )
        )

    override fun handleApprovalDepositComplete(
        orderValue: FiatValue,
        estimatedTransactionCompletionTime: String
    ) =
        replaceBottomSheet(
            FiatTransactionBottomSheet.newInstance(
                orderValue.currencyCode,
                getString(R.string.deposit_confirmation_success_title, orderValue.toStringWithSymbol()),
                getString(
                    R.string.yapily_fiat_deposit_success_subtitle, orderValue.toStringWithSymbol(),
                    orderValue.currencyCode,
                    estimatedTransactionCompletionTime
                ),
                FiatTransactionState.SUCCESS
            )
        )

    override fun handleApprovalDepositError(currency: String) =
        replaceBottomSheet(
            FiatTransactionBottomSheet.newInstance(
                currency,
                getString(R.string.deposit_confirmation_error_title),
                getString(
                    R.string.deposit_confirmation_error_subtitle
                ),
                FiatTransactionState.ERROR
            )
        )

    override fun handleBuyApprovalError() {
        ToastCustom.makeText(
            this, getString(R.string.simple_buy_confirmation_error), Toast.LENGTH_LONG, ToastCustom.TYPE_ERROR
        )
    }

    override fun handleApprovalDepositInProgress(amount: FiatValue) =
        replaceBottomSheet(
            FiatTransactionBottomSheet.newInstance(
                amount.currencyCode,
                getString(R.string.deposit_confirmation_pending_title),
                getString(
                    R.string.deposit_confirmation_pending_subtitle
                ),
                FiatTransactionState.PENDING
            )
        )

    override fun handleApprovalDepositTimeout(currencyCode: String) =
        replaceBottomSheet(
            FiatTransactionBottomSheet.newInstance(
                currencyCode,
                getString(R.string.deposit_confirmation_pending_title),
                getString(
                    R.string.deposit_confirmation_pending_subtitle
                ),
                FiatTransactionState.ERROR
            )
        )

    override fun showOpenBankingDeepLinkError() {
        ToastCustom.makeText(
            this, getString(R.string.open_banking_deeplink_error), Toast.LENGTH_LONG, ToastCustom.TYPE_ERROR
        )
    }

    override fun launchFiatDeposit(currency: String) {
        runOnUiThread {
            launchDashboardFlow(AssetAction.FiatDeposit, currency)
        }
    }

    override fun onFlowFinished() {
        Timber.d("On finished")
    }

    override fun onSheetClosed() {
        Timber.d("On closed")
    }

    override fun startUpsellKyc() {
        launchKyc(CampaignType.None)
    }

    override fun exitSimpleBuyFlow() {
        launchSimpleBuySell()
    }
}
