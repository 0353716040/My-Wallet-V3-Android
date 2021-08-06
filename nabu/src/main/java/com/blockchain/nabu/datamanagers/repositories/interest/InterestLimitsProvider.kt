package com.blockchain.nabu.datamanagers.repositories.interest

import com.blockchain.nabu.Authenticator
import com.blockchain.nabu.service.NabuService
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import io.reactivex.rxjava3.core.Single
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.exchangerate.toCrypto
import java.util.Calendar
import java.util.Date

interface InterestLimitsProvider {
    fun getLimitsForAllAssets(): Single<InterestLimitsList>
}

class InterestLimitsProviderImpl(
    private val assetCatalogue: AssetCatalogue,
    private val nabuService: NabuService,
    private val authenticator: Authenticator,
    private val currencyPrefs: CurrencyPrefs,
    private val exchangeRates: ExchangeRateDataManager
) : InterestLimitsProvider {
    override fun getLimitsForAllAssets(): Single<InterestLimitsList> =
        authenticator.authenticate {
            nabuService.getInterestLimits(it, currencyPrefs.selectedFiatCurrency)
                .map { responseBody ->
                    InterestLimitsList(responseBody.limits.assetMap.entries.map { entry ->
                        val crypto = assetCatalogue.fromNetworkTicker(entry.key)!!
                        val minDepositFiatValue = FiatValue.fromMinor(currencyPrefs.selectedFiatCurrency,
                            entry.value.minDepositAmount.toLong())
                        val minDepositCryptoValue = minDepositFiatValue.toCrypto(exchangeRates, crypto)
                        val maxWithdrawalFiatValue = FiatValue.fromMinor(currencyPrefs.selectedFiatCurrency,
                            entry.value.maxWithdrawalAmount.toLong())
                        val maxWithdrawalCryptoValue = maxWithdrawalFiatValue.toCrypto(exchangeRates, crypto)

                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        calendar.add(Calendar.MONTH, 1)

                        InterestLimits(
                            interestLockUpDuration = entry.value.lockUpDuration,
                            minDepositAmount = minDepositCryptoValue,
                            cryptoCurrency = crypto,
                            currency = entry.value.currency,
                            nextInterestPayment = calendar.time,
                            maxWithdrawalAmount = maxWithdrawalCryptoValue
                        )
                    })
                }
        }
}

data class InterestLimits(
    val interestLockUpDuration: Int,
    val minDepositAmount: CryptoValue,
    val cryptoCurrency: AssetInfo,
    val currency: String,
    val nextInterestPayment: Date,
    val maxWithdrawalAmount: CryptoValue
)

data class InterestLimitsList(
    val list: List<InterestLimits>
)