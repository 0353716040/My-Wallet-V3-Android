package piuk.blockchain.android.ui.auth

import androidx.annotation.StringRes
import piuk.blockchain.android.ui.base.View
import piuk.blockchain.android.ui.customviews.ToastCustom
import piuk.blockchain.android.util.ViewUtils

interface PinEntryView : View {

    fun showProgressDialog(@StringRes messageId: Int)

    fun fillPinBoxAtIndex(index: Int)

    fun clearPinBoxAtIndex(index: Int)

    fun fillPinBoxes()

    fun showToast(@StringRes message: Int, @ToastCustom.ToastType toastType: String)

    fun showParameteredToast(@StringRes message: Int, @ToastCustom.ToastType toastType: String, parameter: Int)

    fun dismissProgressDialog()

    fun showMaxAttemptsDialog()

    fun showValidationDialog()

    fun showCommonPinWarning(callback: DialogButtonCallback)

    fun showWalletVersionNotSupportedDialog(walletVersion: String?)

    fun walletUpgradeRequired(passwordTriesRemaining: Int)

    fun onWalletUpgradeFailed()

    fun restartPageAndClearTop()

    fun setTitleString(@StringRes title: Int)

    fun setTitleVisibility(@ViewUtils.Visibility visibility: Int)

    fun clearPinBoxes()

    fun goToPasswordRequiredActivity()

    fun finishWithPayloadDecrypted()

    fun finishWithResultOk(pin: String)

    fun showFingerprintDialog()

    fun showKeyboard()

    fun showAccountLockedDialog()

    fun showMobileNotice(mobileNoticeDialog: MobileNoticeDialog)

    fun appNeedsUpgrade(isForced: Boolean)

    fun restartAppWithVerifiedPin()

    fun setupCommitHashView()

    fun askToUseBiometrics()

    fun showApiOutageMessage()

    val isForValidatingPinForResult: Boolean

    val isForValidatingAndLoadingPayloadResult: Boolean
}
