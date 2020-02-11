package piuk.blockchain.android.ui.dashboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.UiThread
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.extensions.exhaustive
import com.blockchain.notifications.analytics.AnalyticsEvents
import info.blockchain.balance.CryptoCurrency
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.fragment_dashboard.*
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.campaign.blockstackCampaignName
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.ui.airdrops.AirdropStatusSheet
import piuk.blockchain.android.ui.home.HomeScreenMviFragment
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.utils.extensions.inflate
import piuk.blockchain.android.ui.dashboard.adapter.DashboardDelegateAdapter
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementCard
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementHost
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementList
import piuk.blockchain.android.ui.dashboard.assetdetails.AssetDetailSheet
import piuk.blockchain.android.ui.dashboard.transfer.BasicTransferToWallet
import piuk.blockchain.android.ui.dashboard.sheets.CustodyWalletIntroSheet
import piuk.blockchain.android.ui.dashboard.sheets.BankDetailsBottomSheet
import piuk.blockchain.android.ui.dashboard.sheets.ForceBackupForSendSheet
import piuk.blockchain.android.ui.home.MainActivity
import piuk.blockchain.android.ui.home.models.MetadataEvent
import piuk.blockchain.androidcore.data.events.ActionEvent
import piuk.blockchain.androidcore.data.rxjava.RxBus
import java.lang.IllegalStateException

class EmptyDashboardItem : DashboardItem

private typealias RefreshFn = () -> Unit

class DashboardFragment : HomeScreenMviFragment<DashboardModel, DashboardIntent, DashboardState>(),
    AssetDetailSheet.Host,
    ForceBackupForSendSheet.Host,
    BasicTransferToWallet.Host {

    override val model: DashboardModel by inject()

    private val announcements: AnnouncementList by inject()

    private val theAdapter: DashboardDelegateAdapter by lazy {
        DashboardDelegateAdapter(
            prefs = get(),
            onCardClicked = { onAssetClicked(it) },
            analytics = get()
        )
    }

    private lateinit var theLayoutManager: RecyclerView.LayoutManager

    private val displayList = mutableListOf<DashboardItem>()

    private val compositeDisposable = CompositeDisposable()
    private val rxBus: RxBus by inject()

    private val actionEvent by unsafeLazy {
        rxBus.register(ActionEvent::class.java)
    }

    private val metadataEvent by unsafeLazy {
        rxBus.register(MetadataEvent::class.java)
    }

    private var state: DashboardState? = null // Hold the 'current' display state, to enable optimising of state updates

    @UiThread
    override fun render(newState: DashboardState) {

        swipe.isRefreshing = false

        if (displayList.isEmpty()) {
            createDisplayList(newState)
        } else {
            updateDisplayList(newState)
        }

        // Update/show bottom sheet
        if (this.state?.showAssetSheetFor != newState.showAssetSheetFor) {
            showAssetSheet(newState.showAssetSheetFor)
        } else {
            if (this.state?.showDashboardSheet != newState.showDashboardSheet) {
                showPromoSheet(newState)
            }
        }

        // Update/show announcement
        if (this.state?.announcement != newState.announcement) {
            showAnnouncement(newState.announcement)
        }

        this.state = newState
    }

    private fun createDisplayList(newState: DashboardState) {
        with(displayList) {
            add(IDX_CARD_ANNOUNCE, EmptyDashboardItem()) // Placeholder for announcements
            add(IDX_CARD_BALANCE, newState)
            add(IDX_CARD_BTC, newState.assets[CryptoCurrency.BTC])
            add(IDX_CARD_ETH, newState.assets[CryptoCurrency.ETHER])
            add(IDX_CARD_BCH, newState.assets[CryptoCurrency.BCH])
            add(IDX_CARD_XLM, newState.assets[CryptoCurrency.XLM])
            add(IDX_CARD_PAX, newState.assets[CryptoCurrency.PAX])
        }
        theAdapter.notifyDataSetChanged()
    }

    private fun updateDisplayList(newState: DashboardState) {
        with(displayList) {

            val modList = mutableListOf<RefreshFn?>()

            modList.add(handleUpdatedAssetState(IDX_CARD_BTC, newState.assets[CryptoCurrency.BTC]))
            modList.add(handleUpdatedAssetState(IDX_CARD_ETH, newState.assets[CryptoCurrency.ETHER]))
            modList.add(handleUpdatedAssetState(IDX_CARD_BCH, newState.assets[CryptoCurrency.BCH]))
            modList.add(handleUpdatedAssetState(IDX_CARD_XLM, newState.assets[CryptoCurrency.XLM]))
            modList.add(handleUpdatedAssetState(IDX_CARD_PAX, newState.assets[CryptoCurrency.PAX]))

            modList.removeAll { it == null }

            if (modList.isNotEmpty()) {
                set(IDX_CARD_BALANCE, newState)
                modList.add { theAdapter.notifyItemChanged(IDX_CARD_BALANCE) }
            }

            modList.forEach { it?.invoke() }
        }
    }

    private fun handleUpdatedAssetState(index: Int, newState: AssetState): RefreshFn? {
        if (displayList[index] != newState) {
            displayList[index] = newState
            return { theAdapter.notifyItemChanged(index) }
        } else {
            return null
        }
    }

    private fun showAssetSheet(sheetFor: CryptoCurrency?) {
        if (sheetFor != null) {
            showBottomSheet(AssetDetailSheet.newInstance(sheetFor))
        } else {
            // Nothing, unless we need to remove the sheet? TODO
        }
    }

    private fun showPromoSheet(state: DashboardState) {
        showBottomSheet(
            when (state.showDashboardSheet) {
                DashboardSheet.STX_AIRDROP_COMPLETE -> AirdropStatusSheet.newInstance(blockstackCampaignName)
                DashboardSheet.CUSTODY_INTRO -> CustodyWalletIntroSheet.newInstance()
                DashboardSheet.SIMPLE_BUY_PAYMENT -> BankDetailsBottomSheet.newInstance()
                DashboardSheet.BACKUP_BEFORE_SEND -> ForceBackupForSendSheet.newInstance()
                DashboardSheet.BASIC_WALLET_TRANSFER -> BasicTransferToWallet.newInstance(state.transferFundsCurrency!!)
                null -> null
            }
        )
    }

    private fun showAnnouncement(card: AnnouncementCard?) {
        displayList[IDX_CARD_ANNOUNCE] = card ?: EmptyDashboardItem()
        theAdapter.notifyItemChanged(IDX_CARD_ANNOUNCE)
    }

    override fun onBackPressed(): Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_dashboard)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        analytics.logEvent(AnalyticsEvents.Dashboard)

        setupSwipeRefresh()
        setupRecycler()
    }

    private fun setupRecycler() {
        theLayoutManager = SafeLayoutManager(requireContext())

        recycler_view.apply {
            layoutManager = theLayoutManager
            adapter = theAdapter
        }
        theAdapter.items = displayList
    }

    private fun setupToolbar() {
        activity.supportActionBar?.let {
            activity.setupToolbar(it, R.string.dashboard_title)
        }
    }

    private fun setupSwipeRefresh() {

        swipe.setOnRefreshListener { model.process(RefreshAllIntent) }

        // Configure the refreshing colors
        swipe.setColorSchemeResources(
            R.color.blue_800,
            R.color.blue_600,
            R.color.blue_400,
            R.color.blue_200
        )
    }

    override fun onResume() {
        super.onResume()
        setupToolbar()

        compositeDisposable += metadataEvent.subscribe {
            model.process(RefreshAllIntent)
            if(announcements.enable()) {
                announcements.checkLatest(announcementHost, compositeDisposable)
            }
        }

        compositeDisposable += actionEvent.subscribe {
            model.process(RefreshAllIntent)
        }

        announcements.checkLatest(announcementHost, compositeDisposable)

        model.process(RefreshAllIntent)
    }

    override fun onPause() {
        compositeDisposable.clear()
        rxBus.unregister(ActionEvent::class.java, actionEvent)
        rxBus.unregister(MetadataEvent::class.java, actionEvent)

        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            MainActivity.SETTINGS_EDIT,
            MainActivity.ACCOUNT_EDIT -> model.process(RefreshAllIntent)
            BACKUP_FUNDS_REQUEST_CODE -> startTransferFunds()
        }
    }

    private fun onAssetClicked(cryptoCurrency: CryptoCurrency) {
        model.process(ShowAssetDetails(cryptoCurrency))
    }

    private val announcementHost = object : AnnouncementHost {

        override val disposables: CompositeDisposable
            get() = compositeDisposable

        override fun showAnnouncementCard(card: AnnouncementCard) {
            model.process(ShowAnnouncement(card))
        }

        override fun dismissAnnouncementCard() {
            model.process(ClearAnnouncement)
        }

        override fun startKyc(campaignType: CampaignType) = navigator().launchKyc(campaignType)

        override fun startSwap(swapTarget: CryptoCurrency) = navigator().launchSwapOrKyc(targetCurrency = swapTarget)

        override fun startBuySell() = navigator().launchBuySell()

        override fun startPitLinking() = navigator().launchThePitLinking()

        override fun startFundsBackup() = navigator().launchBackupFunds()

        override fun startSetup2Fa() = navigator().launchSetup2Fa()

        override fun startVerifyEmail() = navigator().launchVerifyEmail()

        override fun startEnableFingerprintLogin() = navigator().launchSetupFingerprintLogin()

        override fun startIntroTourGuide() = navigator().launchIntroTour()

        override fun startTransferCrypto() = navigator().launchTransfer()

        override fun startStxReceivedDetail() =
            model.process(ShowDashboardSheet(DashboardSheet.STX_AIRDROP_COMPLETE))

        override fun startSimpleBuyPaymentDetail() =
            model.process((ShowDashboardSheet(DashboardSheet.SIMPLE_BUY_PAYMENT)))

        override fun finishSimpleBuySignup() {
            navigator().resumeSimpleBuyKyc()
        }
    }

    // AssetDetailSheet.Host
    override fun onSheetClosed() {
        model.process(ClearBottomSheet)
    }

    override fun gotoSendFor(cryptoCurrency: CryptoCurrency, filter: AssetFilter) {
        when (filter) {
            AssetFilter.Total -> throw IllegalStateException("The Send.Total action is invalid")
            AssetFilter.Wallet -> navigator().gotoSendFor(cryptoCurrency)
            AssetFilter.Custodial -> model.process(StartCustodialTransfer(cryptoCurrency))
        }.exhaustive
    }

    override fun goToReceiveFor(cryptoCurrency: CryptoCurrency, filter: AssetFilter) =
        when (filter) {
            AssetFilter.Total -> throw IllegalStateException("The Receive.Total action is invalid")
            AssetFilter.Wallet -> navigator().gotoReceiveFor(cryptoCurrency)
            AssetFilter.Custodial -> throw IllegalStateException("The Receive.Custodial action is invalid")
        }.exhaustive

    override fun gotoActivityFor(cryptoCurrency: CryptoCurrency, filter: AssetFilter) {
        when (filter) {
            AssetFilter.Total -> { /* TODO: Hook up the everything activity view, when we have designs */ }
            AssetFilter.Wallet -> navigator().gotoTransactionsFor(cryptoCurrency)
            AssetFilter.Custodial -> { /* TODO: Hook up the custodial activity, when we have designs */ }
        }.exhaustive
    }

    override fun gotoSwap(fromCryptoCurrency: CryptoCurrency, filter: AssetFilter) =
        when (filter) {
            AssetFilter.Total -> throw IllegalStateException("The Swap.Total action is invalid")
            AssetFilter.Wallet -> navigator().launchSwapOrKyc(
                fromCryptoCurrency = fromCryptoCurrency,
                targetCurrency = fromCryptoCurrency.defaultSwapTo
            )
            AssetFilter.Custodial -> throw IllegalStateException("The Swap.Custodial action is invalid")
        }.exhaustive

    override fun startBackupForTransfer() {
        navigator().launchBackupFunds(this, BACKUP_FUNDS_REQUEST_CODE)
    }

    override fun startTransferFunds() {
        model.process(TransferFunds)
    }

    override fun abortTransferFunds() {
        model.process(AbortFundsTransfer)
    }

    companion object {
        fun newInstance() = DashboardFragment()

        private const val IDX_CARD_ANNOUNCE = 0
        private const val IDX_CARD_BALANCE = 1
        private const val IDX_CARD_BTC = 2
        private const val IDX_CARD_ETH = 3
        private const val IDX_CARD_BCH = 4
        private const val IDX_CARD_XLM = 5
        private const val IDX_CARD_PAX = 6

        private const val BACKUP_FUNDS_REQUEST_CODE = 8265
    }
}

/**
 * supportsPredictiveItemAnimations = false to avoid crashes when computing changes.
 */
private class SafeLayoutManager(context: Context) : LinearLayoutManager(context) {
    override fun supportsPredictiveItemAnimations() = false
}
