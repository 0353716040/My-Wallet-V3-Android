package piuk.blockchain.android.ui.login

import android.net.Uri
import androidx.annotation.VisibleForTesting
import piuk.blockchain.android.ui.base.mvi.MviIntent

sealed class LoginIntents : MviIntent<LoginState> {

    data class UpdateEmail(private val email: String) : LoginIntents() {
        override fun reduce(oldState: LoginState): LoginState =
            oldState.copy(
                email = email,
                currentStep = if (email.isBlank()) {
                    LoginStep.SELECT_METHOD
                } else {
                    LoginStep.ENTER_EMAIL
                }
            )
    }

    data class ObtainSessionIdForEmail(
        val selectedEmail: String,
        val captcha: String
    ) : LoginIntents() {
        override fun reduce(oldState: LoginState): LoginState =
            oldState.copy(
                email = selectedEmail,
                captcha = captcha,
                currentStep = LoginStep.GET_SESSION_ID
            )
    }

    data class SendEmail(
        val sessionId: String,
        val selectedEmail: String,
        val captcha: String
    ) : LoginIntents() {
        override fun reduce(oldState: LoginState): LoginState =
            oldState.copy(
                email = selectedEmail,
                sessionId = sessionId,
                currentStep = LoginStep.SEND_EMAIL
            )
    }

    object ShowEmailSent : LoginIntents() {
        override fun reduce(oldState: LoginState): LoginState =
            oldState.copy(
                currentStep = LoginStep.VERIFY_DEVICE
            )
    }

    object GetSessionIdFailed : LoginIntents() {
        override fun reduce(oldState: LoginState): LoginState =
            oldState.copy(
                currentStep = LoginStep.SHOW_SESSION_ERROR
            )
    }

    object ShowEmailFailed : LoginIntents() {
        override fun reduce(oldState: LoginState): LoginState =
            oldState.copy(
                currentStep = LoginStep.SHOW_EMAIL_ERROR
            )
    }

    object StartPinEntry : LoginIntents() {
        override fun reduce(oldState: LoginState): LoginState =
            oldState.copy(
                currentStep = LoginStep.ENTER_PIN
            )
    }

    data class LoginWithQr(val qrString: String) : LoginIntents() {
        override fun reduce(oldState: LoginState): LoginState =
            oldState.copy(
                currentStep = LoginStep.LOG_IN
            )
    }

    data class ShowScanError(private val shouldRestartApp: Boolean) : LoginIntents() {
        override fun reduce(oldState: LoginState): LoginState =
            oldState.copy(
                currentStep = LoginStep.SHOW_SCAN_ERROR,
                shouldRestartApp = shouldRestartApp
            )
    }

    class CheckForExistingSessionOrDeepLink(val action: String, val uri: Uri) : LoginIntents() {
        override fun reduce(oldState: LoginState): LoginState = oldState
    }

    object UnknownError : LoginIntents() {
        override fun reduce(oldState: LoginState): LoginState = oldState.copy(currentStep = LoginStep.UNKNOWN_ERROR)
    }

    object UserIsLoggedIn : LoginIntents() {
        override fun reduce(oldState: LoginState): LoginState =
            oldState.copy(
                currentStep = LoginStep.ENTER_PIN
            )
    }

    class UserAuthenticationRequired(
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        val action: String?,
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        val uri: Uri
    ) : LoginIntents() {
        override fun reduce(oldState: LoginState): LoginState =
            oldState.copy(
                currentStep = LoginStep.NAVIGATE_FROM_DEEPLINK,
                intentAction = action,
                intentUri = uri
            )
    }
}