package piuk.blockchain.android.ui.transfer.send

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.Money
import io.reactivex.Completable
import io.reactivex.Single
import piuk.blockchain.android.coincore.AddressFactory
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.PendingSendTx
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.SendProcessor
import piuk.blockchain.android.coincore.SendTarget
import piuk.blockchain.android.coincore.SendValidationError
import piuk.blockchain.androidcore.utils.extensions.then

class SendInteractor(
    private val coincore: Coincore,
    private val addressFactory: AddressFactory
) {
    private var sendProcessor: SendProcessor? = null

    fun validatePassword(password: String): Single<Boolean> =
        Single.just(coincore.validateSecondPassword(password))

    fun validateTargetAddress(address: String, asset: CryptoCurrency): Single<ReceiveAddress> =
        Single.fromCallable {
            addressFactory.parse(address, asset) ?: throw SendValidationError(SendValidationError.INVALID_ADDRESS)
        }

    fun initialiseTransaction(
        sourceAccount: CryptoAccount,
        targetAddress: SendTarget
    ): Completable =
        sourceAccount.createSendProcessor(targetAddress)
            .doOnSuccess { sendProcessor = it }
            .ignoreElement()

    fun getAvailableBalance(tx: PendingSendTx): Single<Money> = sendProcessor!!.availableBalance(tx)

    fun verifyAndExecute(tx: PendingSendTx): Completable =
        sendProcessor!!.validate(tx)
            .then {
                sendProcessor!!.execute(tx)
            }

    fun getFeeForTransaction(tx: PendingSendTx) = sendProcessor!!.absoluteFee(tx)

    fun checkIfNoteSupported(): Single<Boolean> = Single.just(sendProcessor!!.isNoteSupported)
}
