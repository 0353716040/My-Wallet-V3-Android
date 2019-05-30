package piuk.blockchain.android.ui.swap.homebrew.exchange.confirmation

import android.support.annotation.StringRes
import com.blockchain.morph.exchange.mvi.ExchangeViewState
import info.blockchain.balance.CryptoValue
import io.reactivex.Observable
import piuk.blockchain.androidcoreui.ui.base.View
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import java.util.Locale

interface ExchangeConfirmationView : View {

    val locale: Locale

    val exchangeViewState: Observable<ExchangeViewState>

    fun showExchangeCompleteDialog(firstGoldPaxTrade: Boolean)

    fun showSecondPasswordDialog()

    fun showProgressDialog()

    fun dismissProgressDialog()

    fun displayErrorDialog(@StringRes message: Int)

    fun updateFee(cryptoValue: CryptoValue)

    fun showToast(@StringRes message: Int, @ToastCustom.ToastType type: String)
}
