package com.blockchain.api.bitcoin

import com.blockchain.api.bitcoin.data.BalanceResponseDto
import com.blockchain.api.bitcoin.data.MultiAddress
import com.blockchain.api.bitcoin.data.UnspentOutputsDto
import io.reactivex.rxjava3.core.Single
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// TODO: Should be internal, but required for payload integration tests.
// Once those are re-written, this can be changed back
/*internal*/ interface BitcoinApi {
    @FormUrlEncoded
    @POST("{coin}/multiaddr")
    fun getMultiAddress(
        @Path("coin") coin: String,
        @Field("active") activeLegacy: String,
        @Field("activeBech32") activeBech32: String,
        @Field("n") limit: Int?,
        @Field("offset") offset: Int?,
        @Field("filter") filter: Int?,
        @Field("onlyShow") context: String?,
        @Field("api_code") apiCode: String
    ): Call<MultiAddress>

    @GET("{coin}/unspent")
    fun getUnspent(
        @Path("coin") coin: String,
        @Query("active") activeLegacy: String,
        @Query("activeBech32") activeBech32: String,
        @Query("confirmations") confirmations: Int?,
        @Query("limit") limit: Int?,
        @Query("api_code") apiCode: String
    ): Single<UnspentOutputsDto>

    @Deprecated("Use the Rx version")
    @FormUrlEncoded
    @POST("{coin}/balance")
    fun getBalance(
        @Path("coin") coin: String,
        @Field("active") activeLegacy: String,
        @Field("activeBech32") activeBech32: String,
        @Field("filter") filter: Int,
        @Field("api_code") apiCode: String
    ): Call<BalanceResponseDto>

    @FormUrlEncoded
    @POST("{coin}/balance")
    fun getBalanceRx(
        @Path("coin") coin: String,
        @Field("active") activeLegacy: String,
        @Field("activeBech32") activeBech32: String,
        @Field("filter") filter: Int,
        @Field("api_code") apiCode: String
    ): Single<BalanceResponseDto>

    @FormUrlEncoded
    @POST("{coin}/pushtx")
    fun pushTx(
        @Path("coin") coin: String?,
        @Field("tx") hash: String?,
        @Field("api_code") apiCode: String?
    ): Call<ResponseBody>

    @FormUrlEncoded
    @POST("{coin}/pushtx")
    fun pushTxWithSecret(
        @Path("coin") coin: String,
        @Field("tx") hash: String,
        @Field("lock_secret") lockSecret: String,
        @Field("api_code") apiCode: String
    ): Call<ResponseBody>
}
