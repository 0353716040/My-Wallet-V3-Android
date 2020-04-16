package piuk.blockchain.android.ui.accounts

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.CryptoCurrency
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.dialog_account_selector_sheet.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CryptoSingleAccount
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

class AccountSelectSheet : SlidingModalBottomDialog() {

    interface Host : SlidingModalBottomDialog.Host {
        fun onAccountSelected(account: CryptoAccount)
    }

    override val host: Host by lazy {
        super.host as? Host
            ?: throw IllegalStateException("Host fragment is not a AccountSelectSheet.Host")
    }

    override val layoutResource: Int
        get() = R.layout.dialog_account_selector_sheet

    private val coincore: Coincore by inject()
    private val exchangeRates: ExchangeRateDataManager by inject()
    private val currencyPrefs: CurrencyPrefs by inject()
    private val disposables = CompositeDisposable()
    private val uiScheduler = AndroidSchedulers.mainThread()

    private val theAdapter: AccountsDelegateAdapter by lazy {
        AccountsDelegateAdapter(
            disposables = disposables,
            exchangeRates = exchangeRates,
            currencyPrefs = currencyPrefs,
            onAccountClicked = { onAccountSelected(it) }
        )
    }

    private val cryptoCurrency: CryptoCurrency? by lazy {
        arguments?.getSerializable(ARG_CRYPTO_CURRENCY) as? CryptoCurrency
    }

    private fun onAccountSelected(account: CryptoAccount) {
        host.onAccountSelected(account)
        dismiss()
    }

    override fun initControls(view: View) {
        with(view.account_list) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = theAdapter

            val itemList = mutableListOf<CryptoAccount>()
            theAdapter.items = itemList

            itemList.add(coincore.allWallets)

            CryptoCurrency.activeCurrencies().forEach { cc ->
                disposables += coincore[cc].accounts()
                    .observeOn(uiScheduler)
                    .subscribeBy(
                        onSuccess = {
                            itemList.addAll(
                                it.accounts
                                    .filterIsInstance<CryptoSingleAccount>()
                                    .filter { a -> a.hasTransactions }
                            )
                            theAdapter.notifyDataSetChanged()
                        },
                        onError = {
                            onCannotLoadAccounts()
                        }
                    )
            }
        }
    }

    private fun onCannotLoadAccounts() {
        dismiss()
    }

    override fun onSheetHidden() {
        super.onSheetHidden()
        disposables.dispose()
    }

    companion object {
        private const val ARG_CRYPTO_CURRENCY = "crypto"

        fun newInstance(
            cryptoCurrency: CryptoCurrency,
            assetFilter: AssetFilter
        ): AccountSelectSheet =
            AccountSelectSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_CRYPTO_CURRENCY, cryptoCurrency)
                }
            }

        // Create sheet for all accounts
        fun newInstance(): AccountSelectSheet =
            AccountSelectSheet()
    }
}
