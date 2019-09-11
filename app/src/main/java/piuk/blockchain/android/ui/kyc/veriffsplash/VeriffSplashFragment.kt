package piuk.blockchain.android.ui.kyc.veriffsplash

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment.findNavController
import com.blockchain.activities.StartSwap
import com.blockchain.kyc.models.nabu.SupportedDocuments
import piuk.blockchain.android.ui.kyc.navhost.KycProgressListener
import piuk.blockchain.android.ui.kyc.navhost.models.KycStep
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.blockchain.notifications.analytics.logEvent
import com.blockchain.ui.extensions.throttledClicks
import com.blockchain.ui.urllinks.URL_BLOCKCHAIN_GOLD_UNAVAILABLE_SUPPORT
import com.blockchain.ui.urllinks.URL_BLOCKCHAIN_KYC_SUPPORTED_COUNTRIES_LIST
import com.blockchain.veriff.VeriffApplicantAndToken
import com.blockchain.veriff.VeriffLauncher
import io.reactivex.Observable
import org.koin.android.ext.android.inject
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.base.BaseFragment
import piuk.blockchain.androidcoreui.ui.base.UiState
import piuk.blockchain.androidcoreui.ui.base.UiState.CONTENT
import piuk.blockchain.androidcoreui.ui.base.UiState.EMPTY
import piuk.blockchain.androidcoreui.ui.base.UiState.FAILURE
import piuk.blockchain.androidcoreui.ui.base.UiState.LOADING
import com.blockchain.ui.dialog.MaterialProgressDialog
import piuk.blockchain.androidcoreui.utils.ParentActivityDelegate
import piuk.blockchain.androidcoreui.utils.extensions.goneIf
import piuk.blockchain.androidcoreui.utils.extensions.inflate
import piuk.blockchain.androidcoreui.utils.extensions.visible
import timber.log.Timber
import kotlinx.android.synthetic.main.fragment_kyc_veriff_splash.*
import piuk.blockchain.android.R
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.utils.extensions.gone
import piuk.blockchain.androidcoreui.utils.extensions.toast
import java.lang.IllegalStateException

class VeriffSplashFragment : BaseFragment<VeriffSplashView, VeriffSplashPresenter>(),
    VeriffSplashView {

    private val presenter: VeriffSplashPresenter by inject()
    private val swapStarter: StartSwap by inject()
    private val stringUtils: StringUtils by inject()
    private val progressListener: KycProgressListener by ParentActivityDelegate(this)
    override val countryCode by unsafeLazy { VeriffSplashFragmentArgs.fromBundle(arguments).countryCode }
    private var progressDialog: MaterialProgressDialog? = null

    override val nextClick: Observable<Unit>
        get() = btn_next.throttledClicks()

    override val swapClick: Observable<Unit>
        get() = btn_goto_swap.throttledClicks()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = container?.inflate(R.layout.fragment_kyc_veriff_splash)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logEvent(AnalyticsEvents.KycVerifyIdentity)

        checkCameraPermissions()
        setupTextLinks()
        onViewReady()
    }

    private fun setupTextLinks() {

        val linksMap = mapOf<String, Uri>(
            "gold_error" to Uri.parse(URL_BLOCKCHAIN_GOLD_UNAVAILABLE_SUPPORT),
            "country_list" to Uri.parse(URL_BLOCKCHAIN_KYC_SUPPORTED_COUNTRIES_LIST)
        )

        // On the content view:
        val countriesText = stringUtils.getStringWithMappedLinks(
            R.string.kyc_veriff_splash_country_supported_subheader,
            linksMap,
            requireActivity()
        )

        text_supported_countries.text = countriesText
        text_supported_countries.movementMethod = LinkMovementMethod.getInstance()

        // On the error view:
        val supportText = stringUtils.getStringWithMappedLinks(
            R.string.kyc_gold_unavailable_text_support,
            linksMap,
            requireActivity()
        )
        text_action.text = supportText
        text_action.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun checkCameraPermissions() {
        val granted = ContextCompat.checkSelfPermission(
            requireActivity(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        view?.findViewById<View>(R.id.text_view_veriff_splash_enable_camera_title)?.goneIf(granted)
        view?.findViewById<View>(R.id.text_view_veriff_splash_enable_camera_body)?.goneIf(granted)
    }

    override fun showProgressDialog(cancelable: Boolean) {
        progressDialog = MaterialProgressDialog(requireContext()).apply {
            setOnCancelListener { presenter.onProgressCancelled() }
            setMessage(R.string.kyc_country_selection_please_wait)
            setCancelable(cancelable)
            show()
        }
    }

    override fun dismissProgressDialog() {
        progressDialog?.apply { dismiss() }
        progressDialog = null
    }

    @SuppressLint("InflateParams")
    override fun continueToVeriff(applicant: VeriffApplicantAndToken) {
        launchVeriff(applicant)
    }

    private fun launchVeriff(applicant: VeriffApplicantAndToken) {
        VeriffLauncher().launchVeriff(requireActivity(), applicant, REQUEST_CODE_VERIFF)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_VERIFF) {
            Timber.d("Veriff result code $resultCode")
            if (resultCode == RESULT_OK) {
                presenter.submitVerification()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun showErrorToast(message: Int) {
        toast(message, ToastCustom.TYPE_ERROR)
    }

    override fun continueToCompletion() {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.kyc_nav_xml, true)
            .build()
        findNavController(this).navigate(R.id.applicationCompleteFragment, null, navOptions)
    }

    override fun continueToSwap() {
        val activity = requireActivity()
        swapStarter.startSwapActivity(activity)
        activity.finish()
    }

    override fun createPresenter(): VeriffSplashPresenter = presenter

    override fun getMvpView(): VeriffSplashView = this

    override fun supportedDocuments(documents: List<SupportedDocuments>) {
        val makeVisible = { id: Int -> view?.findViewById<View>(id)?.visible() }
        documents.forEach {
            when (it) {
                SupportedDocuments.PASSPORT -> makeVisible(R.id.text_view_document_passport)
                SupportedDocuments.DRIVING_LICENCE -> makeVisible(R.id.text_view_document_drivers_license)
                SupportedDocuments.NATIONAL_IDENTITY_CARD -> makeVisible(R.id.text_view_document_id_card)
                SupportedDocuments.RESIDENCE_PERMIT -> makeVisible(R.id.text_view_document_residence_permit)
            }
        }
    }

    override fun setUiState(@UiState.UiStateDef state: Int) {
        when (state) {
            LOADING -> showLoadingState()
            CONTENT -> showContentState()
            FAILURE -> showErrorState()
            EMPTY -> showEmptyState()
        }
    }

    private fun showLoadingState() {
        showProgressDialog(cancelable = false)
        error_layout.gone()
        content_view.gone()
        loading_view.visible()
    }

    private fun showContentState() {
        dismissProgressDialog()
        progressListener.setHostTitle(R.string.kyc_veriff_splash_title)
        progressListener.incrementProgress(KycStep.VeriffSplashPage)
        error_layout.gone()
        content_view.visible()
        loading_view.gone()
    }

    private fun showErrorState() {
        dismissProgressDialog()
        progressListener.setHostTitle(R.string.kyc_veriff_splash_error_silver)
        error_layout.visible()
        content_view.gone()
        loading_view.gone()
    }

    private fun showEmptyState() {
        throw IllegalStateException("UiState == EMPTY. This should never happen")
    }

    companion object {
        private const val REQUEST_CODE_VERIFF = 1440
    }
}