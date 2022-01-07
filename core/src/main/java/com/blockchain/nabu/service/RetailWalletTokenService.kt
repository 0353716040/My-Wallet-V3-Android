package com.blockchain.nabu.service

import com.blockchain.nabu.api.wallet.RETAIL_JWT_TOKEN_PATH
import com.blockchain.nabu.api.wallet.RetailWallet
import com.blockchain.nabu.models.responses.wallet.RetailJwtResponse
import io.reactivex.rxjava3.core.Single
import retrofit2.Retrofit

class RetailWalletTokenService(
    private val explorerPath: String,
    private val apiCode: String,
    retrofit: Retrofit
) {

    private val service: RetailWallet = retrofit.create(RetailWallet::class.java)

    internal fun requestJwt(
        path: String = explorerPath + RETAIL_JWT_TOKEN_PATH,
        guid: String,
        sharedKey: String
    ): Single<RetailJwtResponse> = service.requestJwt(
        path,
        guid,
        sharedKey,
        apiCode
    )
}
