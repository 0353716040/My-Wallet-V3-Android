package piuk.blockchain.android.ui.kyc.invalidcountry

import com.blockchain.android.testutils.rxInit
import com.blockchain.swap.nabu.datamanagers.NabuDataManager
import com.blockchain.swap.nabu.models.tokenresponse.NabuOfflineTokenResponse
import com.blockchain.swap.nabu.models.tokenresponse.mapToMetadata
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Single
import org.amshove.kluent.any
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.ui.kyc.countryselection.util.CountryDisplayModel
import piuk.blockchain.androidcore.data.metadata.MetadataManager

class KycInvalidCountryPresenterTest {

    private lateinit var subject: KycInvalidCountryPresenter
    private val nabuDataManager: NabuDataManager = mock()
    private val metadataManager: MetadataManager = mock()
    private val view: KycInvalidCountryView = mock()

    @get:Rule
    val rxSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
    }

    @Before
    fun setUp() {
        subject = KycInvalidCountryPresenter(nabuDataManager, metadataManager)
        subject.initView(view)
    }

    @Test
    fun `on no thanks clicked request successful`() {
        // Arrange
        givenSuccessfulUserCreation()
        givenSuccessfulRecordCountryRequest()
        givenViewReturnsDisplayModel()
        // Act
        subject.onNoThanks()
        // Assert
        verify(nabuDataManager).recordCountrySelection(any(), any(), any(), eq(null), eq(false))
        verify(view).showProgressDialog()
        verify(view).dismissProgressDialog()
        verify(view).finishPage()
    }

    @Test
    fun `on notify me clicked request successful`() {
        // Arrange
        givenSuccessfulUserCreation()
        givenSuccessfulRecordCountryRequest()
        givenViewReturnsDisplayModel()
        // Act
        subject.onNotifyMe()
        // Assert
        verify(nabuDataManager).recordCountrySelection(any(), any(), any(), eq(null), eq(true))
        verify(view).showProgressDialog()
        verify(view).dismissProgressDialog()
        verify(view).finishPage()
    }

    @Test
    fun `on no thanks clicked request fails but exception swallowed`() {
        // Arrange
        givenSuccessfulUserCreation()
        whenever(nabuDataManager.recordCountrySelection(any(), any(), any(), eq(null), any()))
            .thenReturn(Completable.error { Throwable() })
        givenSuccessfulRecordCountryRequest()
        givenViewReturnsDisplayModel()
        // Act
        subject.onNoThanks()
        // Assert
        verify(view).showProgressDialog()
        verify(view).dismissProgressDialog()
        verify(view).finishPage()
    }

    private fun givenSuccessfulUserCreation() {
        val jwt = "JWT"
        whenever(nabuDataManager.requestJwt()).thenReturn(Single.just(jwt))
        val offlineToken = NabuOfflineTokenResponse("", "")
        whenever(nabuDataManager.getAuthToken(jwt))
            .thenReturn(Single.just(offlineToken))
        whenever(metadataManager.saveToMetadata(offlineToken.mapToMetadata()))
            .thenReturn(Completable.complete())
    }

    private fun givenSuccessfulRecordCountryRequest() {
        whenever(nabuDataManager.recordCountrySelection(any(), any(), any(), eq(null), any()))
            .thenReturn(Completable.complete())
    }

    private fun givenViewReturnsDisplayModel() {
        whenever(view.displayModel).thenReturn(
            CountryDisplayModel(
                name = "Great Britain",
                countryCode = "GB",
                state = null,
                flag = null,
                isState = false
            )
        )
    }
}