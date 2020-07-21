package piuk.blockchain.android.ui.transfer.send.flow

import android.app.Activity
import android.content.Intent
import android.text.Editable
import android.view.View
import android.widget.TextView
import com.blockchain.koin.scopedInject
import info.blockchain.balance.CryptoCurrency
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.dialog_send_address.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.AddressFactory
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.ui.transfer.send.FlowInputSheet
import piuk.blockchain.android.ui.transfer.send.SendIntent
import piuk.blockchain.android.ui.transfer.send.SendState
import piuk.blockchain.android.ui.zxing.CaptureActivity
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.androidcoreui.utils.extensions.gone
import piuk.blockchain.androidcoreui.utils.extensions.invisible
import piuk.blockchain.androidcoreui.utils.extensions.visible
import piuk.blockchain.androidcoreui.utils.helperfunctions.AfterTextChangedWatcher
import timber.log.Timber

class EnterTargetAddressSheet(
    host: SlidingModalBottomDialog.Host
) : FlowInputSheet(host) {
    override val layoutResource: Int = R.layout.dialog_send_address

    private val appUtil: AppUtil by inject()
    private val coincore: Coincore by scopedInject()
    private val addressFactory: AddressFactory by scopedInject()

    private val disposables = CompositeDisposable()
    private var state: SendState = SendState()

    override fun render(newState: SendState) {
        Timber.d("!SEND!> Rendering! EnterTargetAddressSheet")

        with(dialogView) {
            if (state.sendingAccount != newState.sendingAccount) {
                from_details.updateAccount(newState.sendingAccount, disposables)
                setupTransferList(newState.sendingAccount)
            }
            cta_button.isEnabled = newState.nextEnabled

            (newState.sendTarget as? CryptoAddress)?.let {
                address_entry.setText(it.address, TextView.BufferType.EDITABLE)
            }
        }
        state = newState
    }

    // TODO: This address processing should occur via the interactor
    private val addressTextWatcher = object : AfterTextChangedWatcher() {
        override fun afterTextChanged(s: Editable?) {
            val address = addressFactory.parse(s.toString(), state.sendingAccount.asset)
            if (address != null) {
                addressEntered(address)
                dialogView.error_msg.invisible()
            } else {
                dialogView.error_msg.visible()
            }
        }
    }

    override fun initControls(view: View) {
        with(dialogView) {
            address_entry.addTextChangedListener(addressTextWatcher)
            btn_scan.setOnClickListener { onLaunchAddressScan() }
            cta_button.setOnClickListener { onCtaClick() }

            wallet_select.apply {
                onLoadError = { showErrorToast("Failed getting transfer wallets") }
                onAccountSelected = { accountSelected(it) }
                onEmptyList = { hideTransferList() }
            }
        }
    }

    private fun setupTransferList(account: CryptoAccount) {
        dialogView.wallet_select.initialise(
            coincore[account.asset].canTransferTo(account).map { it.map { it as BlockchainAccount } }
        )
    }

    private fun hideTransferList() {
        dialogView.title_pick.gone()
        dialogView.wallet_select.gone()
    }

    private fun accountSelected(account: BlockchainAccount) {
        require(account is CryptoAccount)
        model.process(SendIntent.TargetSelected(account))
        model.process(SendIntent.TargetSelectionConfirmed)
    }

    private fun onLaunchAddressScan() {
        if (!appUtil.isCameraOpen) {
            startActivityForResult(
                Intent(activity, CaptureActivity::class.java),
                SCAN_QR_ADDRESS
            )
        } else {
            showErrorToast(R.string.camera_unavailable)
        }
    }

    private fun addressEntered(address: ReceiveAddress) {
        model.process(SendIntent.TargetSelected(address))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
        when (requestCode) {
            SCAN_QR_ADDRESS -> handleScanResult(resultCode, data)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }

    private fun handleScanResult(resultCode: Int, data: Intent?) {
        Timber.d("Got QR scan result!")
        if (resultCode == Activity.RESULT_OK && data != null) {
            data.getStringExtra(CaptureActivity.SCAN_RESULT)?.let {
                // Just supporting ETH in this pass,
                // TODO: Replace this with a full scanning parser?
                val address = addressFactory.parse(it, CryptoCurrency.ETHER)
                if (address == null) {
                    showErrorToast("Invalid ETH address!!")
                } else {
                    addressEntered(address)
                }
            }
        }
    }

    private fun onCtaClick() =
        model.process(SendIntent.TargetSelectionConfirmed)

    companion object {
        const val SCAN_QR_ADDRESS = 2985
    }
}
