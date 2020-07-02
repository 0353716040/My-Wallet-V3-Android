package piuk.blockchain.android.coincore.erc20.usdt

import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AvailableActions
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.impl.Erc20CryptoNonCustodialAccountBase
import piuk.blockchain.androidcore.data.erc20.Erc20Account
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

internal class UsdtCryptoWalletAccount(
    label: String,
    private val address: String,
    account: Erc20Account,
    exchangeRates: ExchangeRateDataManager
) : Erc20CryptoNonCustodialAccountBase(
    CryptoCurrency.USDT,
    label,
    account,
    exchangeRates
) {

    override val receiveAddress: Single<ReceiveAddress>
        get() = Single.just(
            UsdtAddress(address, label)
        )

    override val actions: AvailableActions
        get() = availableActions

    private val availableActions = setOf(
        AssetAction.ViewActivity,
        AssetAction.Swap
    )
}
