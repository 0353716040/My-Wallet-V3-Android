package piuk.blockchain.android.ui.swap.homebrew.exchange.host

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import androidx.navigation.fragment.NavHostFragment.findNavController
import com.blockchain.accounts.AsyncAllAccountList
import com.blockchain.swap.common.exchange.mvi.ChangeCryptoFromAccount
import com.blockchain.swap.common.exchange.mvi.ChangeCryptoToAccount
import com.blockchain.swap.common.exchange.mvi.SimpleFieldUpdateIntent
import com.blockchain.swap.common.exchange.service.QuoteService
import piuk.blockchain.android.ui.swap.homebrew.exchange.ExchangeFragment
import piuk.blockchain.android.ui.swap.homebrew.exchange.ExchangeLimitState
import piuk.blockchain.android.ui.swap.homebrew.exchange.ExchangeMenuState
import piuk.blockchain.android.ui.swap.homebrew.exchange.ExchangeModel
import piuk.blockchain.android.ui.swap.homebrew.exchange.ExchangeViewModelProvider
import piuk.blockchain.android.ui.swap.homebrew.exchange.REQUEST_CODE_CHOOSE_RECEIVING_ACCOUNT
import piuk.blockchain.android.ui.swap.homebrew.exchange.REQUEST_CODE_CHOOSE_SENDING_ACCOUNT
import piuk.blockchain.android.ui.swap.homebrew.exchange.confirmation.ExchangeConfirmationFragment
import piuk.blockchain.android.ui.swap.logging.WebsocketConnectionFailureEvent
import piuk.blockchain.android.ui.swap.showErrorDialog
import com.blockchain.swap.nabu.StartKyc
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.notifications.analytics.logEvent
import com.blockchain.ui.chooserdialog.AccountChooserBottomDialog
import info.blockchain.balance.AccountReference
import info.blockchain.balance.CryptoCurrency
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.koin.android.architecture.ext.viewModel
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.swap.homebrew.exchange.SwapInfoBottomDialog
import piuk.blockchain.androidcore.utils.helperfunctions.consume
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.base.BaseAuthActivity
import piuk.blockchain.androidcoreui.utils.logging.Logging

class HomebrewNavHostActivity : BaseAuthActivity(),
    HomebrewHostActivityListener,
    ExchangeViewModelProvider,
    ExchangeLimitState,
    ExchangeMenuState,
    AccountChooserBottomDialog.Callback {

    private val toolbar by unsafeLazy { findViewById<Toolbar>(R.id.toolbar_general) }
    private val navHostFragment by unsafeLazy { supportFragmentManager.findFragmentById(R.id.nav_host) }
    private val navController by unsafeLazy { findNavController(navHostFragment!!) }
    private val currentFragment: Fragment?
        get() = navHostFragment?.childFragmentManager?.findFragmentById(R.id.nav_host)

    private val defaultCurrency by unsafeLazy { intent.getStringExtra(EXTRA_DEFAULT_CURRENCY) }

    private val preselectedToCurrency by lazy {
        (intent.getSerializableExtra(EXTRA_PRESELECTED_TO_CURRENCY)as?CryptoCurrency) ?: CryptoCurrency.ETHER
    }

    override val exchangeViewModel: ExchangeModel by viewModel()

    private val startKyc: StartKyc by inject()
    private val allAccountList: AsyncAllAccountList by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_homebrew_host)

        val args = ExchangeFragment.bundleArgs(defaultCurrency)
        compositeDisposable += allAccountList.allAccounts()
            .map { accounts ->
                accounts.first { it.cryptoCurrency == preselectedToCurrency }
            }.subscribeBy { exchangeViewModel.initWithPreselectedCurrency(it.cryptoCurrency) }

        navController.navigate(R.id.exchangeFragment, args)
    }

    override fun onSupportNavigateUp(): Boolean =
        if (currentFragment is ExchangeConfirmationFragment) {
            consume { navController.popBackStack() }
        } else {
            consume { finish() }
        }

    override fun onBackPressed() {
        onSupportNavigateUp()
    }

    override fun setToolbarTitle(title: Int) {
        setupToolbar(toolbar, title)
    }

    override fun onResume() {
        super.onResume()
        newQuoteWebSocket()
    }

    override fun onPause() {
        compositeDisposable.clear()
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean =
        consume { menuInflater.inflate(R.menu.menu_tool_bar, menu) }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_show_kyc -> {
                logEvent(AnalyticsEvents.SwapTiers)
                startKyc.startKycActivity(this@HomebrewNavHostActivity)
                return true
            }
            R.id.action_help -> {
                showHelpDialog()
                return true
            }
            R.id.action_error -> {
                menuState?.let {
                    when (it) {
                        is ExchangeMenuState.ExchangeMenu.Error -> {
                            showErrorDialog(supportFragmentManager, it.error)
                        }
                        is ExchangeMenuState.ExchangeMenu.Help -> {
                            // Invalid menu state
                        }
                    }
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showHelpDialog() {
        SwapInfoBottomDialog.newInstance().show(supportFragmentManager, "SwapInfo")
    }

    private var showKycItem: MenuItem? = null
    private var errorItem: MenuItem? = null
    private var helpItem: MenuItem? = null
    private var menuState: ExchangeMenuState.ExchangeMenu? = null

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        showKycItem = menu?.findItem(R.id.action_show_kyc)
        errorItem = menu?.findItem(R.id.action_error)
        helpItem = menu?.findItem(R.id.action_help)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun setMenuState(state: ExchangeMenuState.ExchangeMenu) {
        menuState = state
        errorItem?.isVisible = (state is ExchangeMenuState.ExchangeMenu.Error)
        helpItem?.isVisible = (state is ExchangeMenuState.ExchangeMenu.Help)
    }

    override fun setOverTierLimit(overLimit: Boolean) {
        showKycItem?.setIcon(
            if (overLimit) {
                R.drawable.ic_over_tier_limit
            } else {
                R.drawable.ic_under_tier_limit
            }
        )
    }

    override fun launchConfirmation() {
        navController.navigate(R.id.exchangeConfirmationFragment)
    }

    private val compositeDisposable = CompositeDisposable()

    private fun newQuoteWebSocket(): QuoteService {
        val quotesService = exchangeViewModel.quoteService

        compositeDisposable += listenForConnectionErrors(quotesService)
        compositeDisposable += quotesService.openAsDisposable()

        return quotesService
    }

    private var snackbar: Snackbar? = null

    private fun listenForConnectionErrors(quotesSocket: QuoteService) =
        quotesSocket.connectionStatus
            .map {
                it != QuoteService.Status.Error
            }
            .distinctUntilChanged()
            .subscribe {
                if (it) {
                    snackbar?.dismiss()
                } else {
                    snackbar = Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.connection_error,
                        Snackbar.LENGTH_INDEFINITE
                    ).apply {
                        show()
                    }

                    Logging.logCustom(WebsocketConnectionFailureEvent())
                }
            }

    override fun onAccountSelected(requestCode: Int, accountReference: AccountReference) {
        when (requestCode) {
            REQUEST_CODE_CHOOSE_SENDING_ACCOUNT -> {
                exchangeViewModel.inputEventSink.onNext(
                    ChangeCryptoFromAccount(accountReference)
                )
                exchangeViewModel.inputEventSink.onNext(
                    SimpleFieldUpdateIntent(0.toBigDecimal())
                )
            }
            REQUEST_CODE_CHOOSE_RECEIVING_ACCOUNT -> {
                exchangeViewModel.inputEventSink.onNext(
                    ChangeCryptoToAccount(accountReference)
                )
                exchangeViewModel.inputEventSink.onNext(
                    SimpleFieldUpdateIntent(0.toBigDecimal())
                )
            }
            else -> throw IllegalArgumentException("Unknown request code $requestCode")
        }
    }

    companion object {
        private const val EXTRA_DEFAULT_CURRENCY = "EXTRA_DEFAULT_CURRENCY"
        private const val EXTRA_PRESELECTED_TO_CURRENCY = "EXTRA_PRESELECTED_TO_CURRENCY"

        @JvmStatic
        fun start(context: Context, defaultCurrency: String, toCryptoCurrency: CryptoCurrency? = null) {
            Intent(context, HomebrewNavHostActivity::class.java).apply {
                putExtra(EXTRA_DEFAULT_CURRENCY, defaultCurrency)
                putExtra(EXTRA_PRESELECTED_TO_CURRENCY, toCryptoCurrency ?: CryptoCurrency.ETHER)
            }.run { context.startActivity(this) }
        }
    }
}

internal interface HomebrewHostActivityListener {
    fun setToolbarTitle(@StringRes title: Int)
    fun launchConfirmation()
}