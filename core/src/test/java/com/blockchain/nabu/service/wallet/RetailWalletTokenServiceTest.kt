package com.blockchain.nabu.service.wallet

import com.blockchain.nabu.api.wallet.RETAIL_JWT_TOKEN_PATH
import com.blockchain.nabu.api.wallet.RetailWallet
import com.blockchain.nabu.models.responses.wallet.RetailJwtResponse
import com.blockchain.nabu.service.RetailWalletTokenService
import com.blockchain.testutils.waitForCompletionWithoutErrors
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.rxjava3.core.Single
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class RetailWalletTokenServiceTest {

    private lateinit var subject: RetailWalletTokenService

    private val retailWallet: RetailWallet = mock()
    private val retrofit: Retrofit = mock()

    private val apiKey = "API_KEY"
    private val explorerPath = "explorerPath"

    @Before
    fun setUp() {
        whenever(retrofit.create(RetailWallet::class.java)).thenReturn(retailWallet)
        subject = RetailWalletTokenService(explorerPath, apiKey, retrofit)
    }

    @Test
    fun `requestJwt`() {
        val guid = "GUID"
        val sharedKey = "SHARED_KEY"

        val expectedResponse = RetailJwtResponse(true, "token", null)

        whenever(
            retailWallet.requestJwt(RETAIL_JWT_TOKEN_PATH, guid, sharedKey, apiKey)
        ).thenReturn(
            Single.just(expectedResponse)
        )

        subject.requestJwt(
            path = RETAIL_JWT_TOKEN_PATH,
            guid = guid,
            sharedKey = sharedKey
        ).test().waitForCompletionWithoutErrors().assertValue {
            it == expectedResponse
        }
    }
}