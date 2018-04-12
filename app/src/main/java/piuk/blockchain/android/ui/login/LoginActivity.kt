package piuk.blockchain.android.ui.login

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.content.ContextCompat
import kotlinx.android.synthetic.main.activity_login.*
import kotlinx.android.synthetic.main.toolbar_general.*
import piuk.blockchain.android.R
import piuk.blockchain.android.data.api.EnvironmentSettings
import piuk.blockchain.android.injection.Injector
import piuk.blockchain.android.ui.auth.PinEntryActivity
import piuk.blockchain.android.ui.base.BaseMvpActivity
import piuk.blockchain.androidcoreui.ui.customviews.MaterialProgressDialog
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.android.ui.zxing.CaptureActivity
import piuk.blockchain.androidcoreui.utils.AppUtil
import piuk.blockchain.android.util.PermissionUtil
import piuk.blockchain.androidcoreui.utils.extensions.toast
import piuk.blockchain.androidcore.utils.helperfunctions.consume
import javax.inject.Inject

@Suppress("MemberVisibilityCanBePrivate")
class LoginActivity : BaseMvpActivity<LoginView, LoginPresenter>(), LoginView {

    @Inject lateinit var loginPresenter: LoginPresenter
    @Inject lateinit var appUtil: AppUtil

    private var progressDialog: MaterialProgressDialog? = null

    init {
        Injector.getInstance().presenterComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        setupToolbar(toolbar_general, R.string.pair_your_wallet)

        pairingFirstStep.text = getString(R.string.pair_wallet_step_1, EnvironmentSettings().explorerUrl + "wallet")

        button_manual_pair.setOnClickListener { onClickManualPair() }
        button_scan.setOnClickListener { onClickQRPair() }
    }

    override fun createPresenter() = loginPresenter

    override fun getView() = this

    override fun startLogoutTimer() {
        // No-op
    }

    override fun onSupportNavigateUp() =
            consume { onBackPressed() }

    override fun showToast(message: Int, toastType: String) = toast(message, toastType)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == PAIRING_QR) {
            if (data?.getStringExtra(CaptureActivity.SCAN_RESULT) != null) {
                presenter.pairWithQR(data.getStringExtra(CaptureActivity.SCAN_RESULT))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PermissionUtil.PERMISSION_REQUEST_CAMERA) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanActivity()
            } else {
                // Permission request was denied.
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun showProgressDialog(message: Int) {
        dismissProgressDialog()
        progressDialog = MaterialProgressDialog(
                this
        ).apply {
            setCancelable(false)
            setMessage(getString(message))
            if (!isFinishing) show()
        }
    }

    override fun dismissProgressDialog() {
        progressDialog?.apply {
            dismiss()
            progressDialog = null
        }
    }

    override fun startPinEntryActivity() {
        val intent = Intent(this, PinEntryActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun onClickQRPair() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            PermissionUtil.requestCameraPermissionFromActivity(mainLayout, this)
        } else {
            startScanActivity()
        }
    }

    private fun onClickManualPair() {
        startActivity(Intent(this, ManualPairingActivity::class.java))
    }

    private fun startScanActivity() {
        if (!appUtil.isCameraOpen) {
            val intent = Intent(this, CaptureActivity::class.java)
            intent.putExtra("SCAN_FORMATS", "QR_CODE")
            startActivityForResult(intent, PAIRING_QR)
        } else {
            showToast(R.string.camera_unavailable, ToastCustom.TYPE_ERROR)
        }
    }

    companion object {
        const val PAIRING_QR = 2005
    }
}