package piuk.blockchain.android.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import com.blockchain.koin.scopedInject
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import info.blockchain.wallet.api.Environment
import org.koin.android.ext.android.inject
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.ActivityLoginBinding
import piuk.blockchain.android.databinding.ToolbarGeneralBinding
import piuk.blockchain.android.ui.auth.PinEntryActivity
import piuk.blockchain.android.ui.base.mvi.MviActivity
import piuk.blockchain.android.ui.customviews.ToastCustom
import piuk.blockchain.android.ui.customviews.toast
import piuk.blockchain.android.ui.launcher.LauncherActivity
import piuk.blockchain.android.ui.login.auth.LoginAuthActivity
import piuk.blockchain.android.ui.scan.QrExpected
import piuk.blockchain.android.ui.scan.QrScanActivity
import piuk.blockchain.android.ui.scan.QrScanActivity.Companion.getRawScanData
import piuk.blockchain.android.ui.start.ManualPairingActivity
import piuk.blockchain.android.util.AfterTextChangedWatcher
import piuk.blockchain.android.util.ViewUtils
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import timber.log.Timber

class LoginActivity : MviActivity<LoginModel, LoginIntents, LoginState, ActivityLoginBinding>() {

    override val model: LoginModel by scopedInject()

    override val alwaysDisableScreenshots: Boolean = true

    private val environmentConfig: EnvironmentConfig by inject()

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build()

    private val googleSignInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(this, gso)
    }

    private val recaptchaClient: GoogleReCaptchaClient by lazy {
        GoogleReCaptchaClient(this, environmentConfig)
    }

    private lateinit var state: LoginState

    override val toolbarBinding: ToolbarGeneralBinding
        get() = binding.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadToolbar(
            titleToolbar = getString(R.string.login_title),
            backAction = { onBackPressed() }
        )
        recaptchaClient.initReCaptcha()
        checkExistingSessionOrDeeplink(intent)
    }

    private fun checkExistingSessionOrDeeplink(intent: Intent) {
        val action = intent.action
        val data = intent.data
        if (action != null && data != null) {
            model.process(LoginIntents.CheckForExistingSessionOrDeepLink(action, data))
        }
    }

    override fun onStart() {
        super.onStart()

        analytics.logEvent(LoginAnalytics.LoginViewed)
        with(binding) {
            loginEmailText.apply {
                inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

                addTextChangedListener(object : AfterTextChangedWatcher() {
                    override fun afterTextChanged(s: Editable) {
                        model.process(LoginIntents.UpdateEmail(s.toString()))
                    }
                })

                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_GO && continueButton.isEnabled) {
                        onContinueButtonClicked()
                    }
                    true
                }
            }
            continueButton.setOnClickListener {
                onContinueButtonClicked()
            }

            continueWithGoogleButton.setOnClickListener {
                analytics.logEvent(LoginAnalytics.LoginWithGoogleMethodSelected)
                startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
            }
            if (environmentConfig.isRunningInDebugMode()) {
                scanPairingButton.setOnClickListener {
                    QrScanActivity.start(this@LoginActivity, QrExpected.MAIN_ACTIVITY_QR)
                }
                scanPairingButton.visibleIf {
                    environmentConfig.environment != Environment.PRODUCTION
                }
                manualPairingButton.apply {
                    setOnClickListener {
                        startActivity(Intent(context, ManualPairingActivity::class.java))
                    }
                    isEnabled = true
                    visible()
                }
            }
        }
    }

    private fun onContinueButtonClicked() {
        binding.loginEmailText.text?.let { emailInputText ->
            if (emailInputText.isNotBlank()) {
                if (isDemoAccount(emailInputText.toString().trim())) {
                    val intent = ManualPairingActivity.newInstance(this, BuildConfig.PLAY_STORE_DEMO_WALLET_ID)
                    startActivity(intent)
                } else {
                    ViewUtils.hideKeyboard(this@LoginActivity)
                    verifyReCaptcha(emailInputText.toString())
                }
            }
        }
    }

    private fun isDemoAccount(email: String): Boolean = email == BuildConfig.PLAY_STORE_DEMO_EMAIL

    override fun onPause() {
        model.process(LoginIntents.CancelPolling)
        super.onPause()
    }

    override fun onDestroy() {
        recaptchaClient.close()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        checkExistingSessionOrDeeplink(intent)
    }

    override fun initBinding(): ActivityLoginBinding = ActivityLoginBinding.inflate(layoutInflater)

    override fun render(newState: LoginState) {
        state = newState
        updateUI(newState)
        when (newState.currentStep) {
            LoginStep.SHOW_SCAN_ERROR -> {
                toast(R.string.pairing_failed, ToastCustom.TYPE_ERROR)
                if (newState.shouldRestartApp) {
                    restartToLauncherActivity()
                }
            }
            LoginStep.ENTER_PIN -> {
                showLoginApprovalStatePrompt(newState.loginApprovalState)
                startActivity(
                    Intent(this, PinEntryActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            LoginStep.VERIFY_DEVICE -> navigateToVerifyDevice()
            LoginStep.SHOW_SESSION_ERROR -> toast(R.string.login_failed_session_id_error, ToastCustom.TYPE_ERROR)
            LoginStep.SHOW_EMAIL_ERROR -> toast(R.string.login_send_email_error, ToastCustom.TYPE_ERROR)
            LoginStep.NAVIGATE_FROM_DEEPLINK -> {
                newState.intentUri?.let { uri ->
                    startActivity(Intent(newState.intentAction, uri, this, LoginAuthActivity::class.java))
                }
            }
            LoginStep.NAVIGATE_FROM_PAYLOAD -> {
                newState.payload?.let {
                    startActivity(LoginAuthActivity.newInstance(this, it, newState.payloadBase64String))
                }
            }
            LoginStep.UNKNOWN_ERROR -> {
                model.process(LoginIntents.CheckShouldNavigateToOtherScreen)
                toast(getString(R.string.common_error), ToastCustom.TYPE_ERROR)
            }
            LoginStep.POLLING_PAYLOAD_ERROR -> handlePollingError(newState.pollingState)
            LoginStep.ENTER_EMAIL -> returnToEmailInput()
            // TODO AND-5317 this should display a bottom sheet with info about what device we're authorising
            LoginStep.REQUEST_APPROVAL -> showLoginApprovalDialog()
            LoginStep.NAVIGATE_TO_LANDING_PAGE -> {
                showLoginApprovalStatePrompt(newState.loginApprovalState)
                model.process(LoginIntents.ResetState)
                restartToLauncherActivity()
                finish()
            }
            else -> {
                // do nothing
            }
        }
    }

    private fun showLoginApprovalStatePrompt(loginApprovalState: LoginApprovalState) =
        when (loginApprovalState) {
            LoginApprovalState.NONE -> {
                // do nothing
            }
            LoginApprovalState.APPROVED ->
                ToastCustom.makeText(
                    this, getString(R.string.login_approved_toast), ToastCustom.LENGTH_LONG, ToastCustom.TYPE_OK
                )
            LoginApprovalState.REJECTED -> ToastCustom.makeText(
                this, getString(R.string.login_denied_toast), ToastCustom.LENGTH_LONG, ToastCustom.TYPE_ERROR
            )
        }

    private fun restartToLauncherActivity() {
        startActivity(
            Intent(this, LauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun showLoginApprovalDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.login_approval_dialog_title)
            .setMessage(R.string.login_approval_dialog_message)
            .setPositiveButton(R.string.common_approve) { di, _ ->
                model.process(LoginIntents.ApproveLoginRequest)
                di.dismiss()
            }
            .setNegativeButton(R.string.common_deny) { di, _ ->
                model.process(LoginIntents.DenyLoginRequest)
                di.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun handlePollingError(state: AuthPollingState) =
        when (state) {
            AuthPollingState.TIMEOUT -> {
                ToastCustom.makeText(
                    this, getString(R.string.login_polling_timeout), ToastCustom.LENGTH_LONG,
                    ToastCustom.TYPE_ERROR
                )
                returnToEmailInput()
            }
            AuthPollingState.ERROR -> {
                // fail silently? - maybe log analytics
            }
            AuthPollingState.DENIED -> {
                ToastCustom.makeText(
                    this, getString(R.string.login_polling_denied), ToastCustom.LENGTH_LONG,
                    ToastCustom.TYPE_ERROR
                )
                returnToEmailInput()
            }
            else -> {
                // no error, do nothing
            }
        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && requestCode == QrScanActivity.SCAN_URI_RESULT) {
            data.getRawScanData()?.let { rawQrString ->
                model.process(LoginIntents.LoginWithQr(rawQrString))
            }
        } else if (resultCode == RESULT_OK && requestCode == RC_SIGN_IN) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                task.result.email?.let { email ->
                    verifyReCaptcha(email)
                } ?: toast(R.string.login_google_email_not_found, ToastCustom.TYPE_GENERAL)
            } catch (apiException: ApiException) {
                Timber.e(apiException)
                toast(R.string.login_google_sign_in_failed, ToastCustom.TYPE_ERROR)
            }
        }
    }

    private fun returnToEmailInput() {
        supportFragmentManager.run {
            this.findFragmentByTag(VerifyDeviceFragment::class.simpleName)?.let { fragment ->
                beginTransaction()
                    .remove(fragment)
                    .commitAllowingStateLoss()
                model.process(LoginIntents.RevertToEmailInput)
            }
        }
    }

    private fun updateUI(newState: LoginState) {
        with(binding) {
            progressBar.visibleIf { newState.isLoading }
            // TODO enable Google auth once ready along with the OR label
            continueButton.visibleIf {
                newState.isTypingEmail ||
                    newState.currentStep == LoginStep.SEND_EMAIL ||
                    newState.currentStep == LoginStep.VERIFY_DEVICE
            }
            continueButton.isEnabled =
                emailRegex.matches(newState.email) || (newState.isTypingEmail && emailRegex.matches(newState.email))
        }
    }

    private fun navigateToVerifyDevice() {
        supportFragmentManager.run {
            beginTransaction()
                .replace(
                    R.id.content_frame,
                    VerifyDeviceFragment.newInstance(
                        state.sessionId, state.email, state.captcha
                    ),
                    VerifyDeviceFragment::class.simpleName
                )
                .addToBackStack(VerifyDeviceFragment::class.simpleName)
                .commitAllowingStateLoss()
        }
    }

    private fun verifyReCaptcha(selectedEmail: String) {
        recaptchaClient.verifyForLogin(
            onSuccess = { response ->
                analytics.logEvent(LoginAnalytics.LoginIdentifierEntered)
                model.process(
                    LoginIntents.ObtainSessionIdForEmail(
                        selectedEmail = selectedEmail,
                        captcha = response.tokenResult
                    )
                )
            },
            onError = { toast(R.string.common_error, ToastCustom.TYPE_ERROR) }
        )
    }

    private val emailRegex = Regex(
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
            "\\@" +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
            "(" +
            "\\." +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
            ")+"
    )

    companion object {
        private const val RC_SIGN_IN = 10
    }
}
