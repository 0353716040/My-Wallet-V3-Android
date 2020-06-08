package piuk.blockchain.androidcore.data.ethereum

import com.blockchain.logging.LastTxUpdater
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.api.Environment
import info.blockchain.wallet.ethereum.Erc20TokenData
import info.blockchain.wallet.ethereum.EthAccountApi
import info.blockchain.wallet.ethereum.EthereumWallet
import info.blockchain.wallet.ethereum.data.Erc20AddressResponse
import info.blockchain.wallet.ethereum.data.EthAddressResponseMap
import info.blockchain.wallet.ethereum.data.EthLatestBlockNumber
import info.blockchain.wallet.ethereum.data.EthTransaction
import info.blockchain.wallet.ethereum.data.TransactionState
import info.blockchain.wallet.exceptions.HDWalletException
import info.blockchain.wallet.exceptions.InvalidCredentialsException
import info.blockchain.wallet.payload.PayloadManager
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.bitcoinj.core.ECKey
import org.spongycastle.util.encoders.Hex
import org.web3j.crypto.RawTransaction
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.ethereum.datastores.EthDataStore
import piuk.blockchain.androidcore.data.ethereum.models.CombinedEthModel
import piuk.blockchain.androidcore.data.metadata.MetadataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.data.rxjava.RxPinning
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager
import piuk.blockchain.androidcore.utils.extensions.applySchedulers
import timber.log.Timber
import java.math.BigInteger
import java.util.HashMap

class EthDataManager(
    private val payloadManager: PayloadManager,
    private val ethAccountApi: EthAccountApi,
    private val ethDataStore: EthDataStore,
    private val walletOptionsDataManager: WalletOptionsDataManager,
    private val metadataManager: MetadataManager,
    private val environmentSettings: EnvironmentConfig,
    private val lastTxUpdater: LastTxUpdater,
    rxBus: RxBus
) {

    private val rxPinning = RxPinning(rxBus)

    /**
     * Clears the currently stored ETH account from memory.
     */
    fun clearEthAccountDetails() = ethDataStore.clearData()

    /**
     * Returns an [CombinedEthModel] object for a given ETH address as an [Observable]. An
     * [CombinedEthModel] contains a list of transactions associated with the account, as well
     * as a final balance. Calling this function also caches the [CombinedEthModel].
     *
     * @return An [Observable] wrapping an [CombinedEthModel]
     */
    fun fetchEthAddress(): Observable<CombinedEthModel> =
        if (environmentSettings.environment == Environment.TESTNET) {
            // TODO(eth testnet explorer coming soon)
            Observable.just(CombinedEthModel(EthAddressResponseMap()))
                .doOnNext { ethDataStore.ethAddressResponse = null }
        } else {
            rxPinning.call<CombinedEthModel> {
                ethAccountApi.getEthAddress(listOf(ethDataStore.ethWallet!!.account.address))
                    .map(::CombinedEthModel)
                    .doOnNext { ethDataStore.ethAddressResponse = it }
                    .subscribeOn(Schedulers.io())
            }
        }

    fun getBalance(account: String): Single<BigInteger> =
        ethAccountApi.getEthAddress(listOf(account))
            .map(::CombinedEthModel)
            .map { it.getTotalBalance() }
            .singleOrError()
            .doOnError(Timber::e)
            .onErrorReturn { BigInteger.ZERO }
            .subscribeOn(Schedulers.io())

    fun getErc20Address(currency: CryptoCurrency): Observable<Erc20AddressResponse> {
        // If the metadata is not yet loaded, ethDataStore.ethWallet will be null.
        // So defer() this call, so that the exception occurs after-subscription, rather than
        // when constructing the Rx chain, so it will can be handled by onError() etc
        return Observable.defer {
            ethAccountApi.getErc20Address(
                ethDataStore.ethWallet!!.account.address,
                getErc20TokenData(currency).contractAddress
            )
        }.subscribeOn(Schedulers.io())
    }

    /**
     * Returns the user's ETH account object if previously fetched.
     *
     * @return A nullable [CombinedEthModel] object
     */
    fun getEthResponseModel(): CombinedEthModel? = ethDataStore.ethAddressResponse

    /**
     * Returns the user's [EthereumWallet] object if previously fetched.
     *
     * @return A nullable [EthereumWallet] object
     */
    fun getEthWallet(): EthereumWallet? = ethDataStore.ethWallet

    fun getDefaultEthAddress(): Single<String?> =
        Single.just(getEthWallet()?.account?.address)

    /**
     * Returns a stream of [EthTransaction] objects associated with a user's ETH address specifically
     * for displaying in the transaction list. These are cached and may be empty if the account
     * hasn't previously been fetched.
     *
     * @return An [Observable] stream of [EthTransaction] objects
     */
    fun getEthTransactions(): Single<List<EthTransaction>> =
        ethDataStore.ethWallet?.account?.address?.let {
            ethAccountApi.getEthTransactions(listOf(it)).applySchedulers()
        } ?: Single.just(emptyList())

    /**
     * Returns whether or not the user's ETH account currently has unconfirmed transactions, and
     * therefore shouldn't be allowed to send funds until confirmation.
     * We compare the last submitted tx hash with the newly created tx hash - if they match it means
     * that the previous tx has not yet been processed.
     *
     * @return An [Observable] wrapping a [Boolean]
     */
    fun isLastTxPending(): Single<Boolean> =
        ethDataStore.ethWallet?.account?.address?.let {
            ethAccountApi.getLastEthTransaction(listOf(it)).map {
                it.state.toLocalState() == TransactionState.PENDING
            }.defaultIfEmpty(false).toSingle()
        } ?: Single.just(false)

    /*
    If x time passed and transaction was not successfully mined, the last transaction will be
    deemed dropped and the account will be allowed to create a new transaction.
     */
    private fun isTransactionDropped(lastTxTimestamp: Long) =
        walletOptionsDataManager.getLastEthTransactionFuse()
            .map { System.currentTimeMillis() > lastTxTimestamp + (it * 1000) }

    private fun hasLastTxBeenProcessed(lastTxHash: String) =
        fetchEthAddress().flatMapIterable { it.getTransactions() }
            .filter { list -> list.hash == lastTxHash }
            .toList()
            .flatMapObservable { Observable.just(it.size > 0) }

    /**
     * Returns a [Number] representing the most recently
     * mined block.
     *
     * @return An [Observable] wrapping a [Number]
     */
    fun getLatestBlockNumber(): Single<EthLatestBlockNumber> =
        if (environmentSettings.environment == Environment.TESTNET) {
            // TODO(eth testnet explorer coming soon)
            Single.just(EthLatestBlockNumber())
        } else {
            ethAccountApi.latestBlockNumber.applySchedulers()
        }

    /**
     * Returns true if a given ETH address is associated with an Ethereum contract.
     * This should be used to validate any proposed destination address for
     * funds.
     *
     * @param address The ETH address to be queried
     * @return An [Observable] returning true or false based on the address's contract status
     */
    fun getIfContract(address: String): Observable<Boolean> =
        if (environmentSettings.environment == Environment.TESTNET) {
            // TODO(eth testnet explorer coming soon)
            Observable.just(false)
        } else {
            rxPinning.call<Boolean> {
                ethAccountApi.getIfContract(address)
                    .applySchedulers()
            }
        }

    private fun String.toLocalState() =
        when (this) {
            "PENDING" -> TransactionState.PENDING
            "CONFIRMED" -> TransactionState.CONFIRMED
            "REPLACED" -> TransactionState.REPLACED
            else -> TransactionState.UNKNOWN
        }

    /**
     * Returns the transaction notes for a given transaction hash, or null if not found.
     */
    fun getTransactionNotes(hash: String): String? = ethDataStore.ethWallet?.txNotes?.get(hash)

    /**
     * Puts a given note in the [HashMap] of transaction notes keyed to a transaction hash. This
     * information is then saved in the metadata service.
     *
     * @return A [Completable] object
     */
    fun updateTransactionNotes(hash: String, note: String): Completable =
        ethDataStore.ethWallet?.let {
            it.txNotes[hash] = note
            return@let save()
        } ?: Completable.error { IllegalStateException("ETH Wallet is null") }
            .applySchedulers()

    fun updateErc20TransactionNotes(hash: String, note: String): Completable = rxPinning.call {
        getErc20TokenData(CryptoCurrency.PAX).putTxNote(hash, note)
        return@call save()
    }.applySchedulers()

    /**
     * Fetches EthereumWallet stored in metadata. If metadata entry doesn't exists it will be created.
     *
     * @param defaultLabel The ETH address default label to be used if metadata entry doesn't exist
     * @param defaultPaxLabel The default label for PAX
     * @return An [Completable]
     */
    fun initEthereumWallet(defaultLabel: String, defaultPaxLabel: String, defaultUsdtLabel: String): Completable =
        fetchOrCreateEthereumWallet(defaultLabel, defaultPaxLabel, defaultUsdtLabel)
            .flatMapCompletable { (wallet, needsSave) ->
                ethDataStore.ethWallet = wallet
                if (needsSave) {
                    save()
                } else {
                    Completable.complete()
                }
            }

    /**
     * @param gasPriceWei Represents the fee the sender is willing to pay for gas. One unit of gas
     *                 corresponds to the execution of one atomic instruction, i.e. a computational step
     * @param gasLimitGwei Represents the maximum number of computational steps the transaction
     *                 execution is allowed to take
     * @param weiValue The amount of wei to transfer from the sender to the recipient
     */
    fun createEthTransaction(
        nonce: BigInteger,
        to: String,
        gasPriceWei: BigInteger,
        gasLimitGwei: BigInteger,
        weiValue: BigInteger
    ): RawTransaction? = RawTransaction.createEtherTransaction(
        nonce,
        gasPriceWei,
        gasLimitGwei,
        to,
        weiValue
    )

    fun getTransaction(hash: String): Observable<EthTransaction> =
        rxPinning.call<EthTransaction> {
            ethAccountApi.getTransaction(hash)
                .applySchedulers()
        }

    fun signEthTransaction(rawTransaction: RawTransaction, ecKey: ECKey): Observable<ByteArray> =
        Observable.fromCallable {
            ethDataStore.ethWallet!!.account!!.signTransaction(rawTransaction, ecKey)
        }

    fun pushEthTx(signedTxBytes: ByteArray): Observable<String> =
        if (environmentSettings.environment == Environment.TESTNET) {
            // TODO(eth testnet explorer coming soon)
            Observable.empty()
        } else {
            rxPinning.call<String> {
                ethAccountApi.pushTx("0x" + String(Hex.encode(signedTxBytes)))
                    .flatMap {
                        lastTxUpdater.updateLastTxTime()
                            .onErrorComplete()
                            .andThen(Observable.just(it))
                    }
                    .applySchedulers()
            }
        }

    fun setLastTxHashObservable(txHash: String, timestamp: Long): Observable<String> =
        rxPinning.call<String> {
            setLastTxHash(txHash, timestamp)
                .applySchedulers()
        }

    @Throws(Exception::class)
    private fun setLastTxHash(txHash: String, timestamp: Long): Observable<String> {
        ethDataStore.ethWallet!!.lastTransactionHash = txHash
        ethDataStore.ethWallet!!.lastTransactionTimestamp = timestamp

        return save().andThen(Observable.just(txHash))
    }

    @Throws(Exception::class)
    private fun fetchOrCreateEthereumWallet(defaultLabel: String, defaultPaxLabel: String, defaultUsdtLabel: String):
        Single<Pair<EthereumWallet, Boolean>> =
        metadataManager.fetchMetadata(EthereumWallet.METADATA_TYPE_EXTERNAL).defaultIfEmpty("")
            .map { metadata ->
                val walletJson = if (metadata != "") metadata else null

                var ethWallet = EthereumWallet.load(walletJson)
                var needsSave = false

                if (ethWallet?.account == null || !ethWallet.account.isCorrect) {
                    try {
                        val masterKey = payloadManager.payload?.hdWallets?.get(0)?.masterKey
                        ethWallet = EthereumWallet(masterKey, defaultLabel, defaultPaxLabel, defaultUsdtLabel)
                        needsSave = true
                    } catch (e: HDWalletException) {
                        // Wallet private key unavailable. First decrypt with second password.
                        throw InvalidCredentialsException(e.message)
                    }
                }
                // AND-2011: Add erc20 token data if not present
                if (ethWallet.updateErc20Tokens(defaultPaxLabel, defaultUsdtLabel)) {
                    needsSave = true
                }

                if (!ethWallet.account.isAddressChecksummed()) {
                    ethWallet.account.apply {
                        address = withChecksummedAddress()
                    }
                    needsSave = true
                }
                ethWallet to needsSave
            }.toSingle()

    fun save(): Completable =
        metadataManager.saveToMetadata(
            ethDataStore.ethWallet!!.toJson(),
            EthereumWallet.METADATA_TYPE_EXTERNAL
        )

    fun getErc20TokenData(currency: CryptoCurrency): Erc20TokenData {
        when (currency) {
            CryptoCurrency.PAX -> return getEthWallet()!!.getErc20TokenData(
                Erc20TokenData.PAX_CONTRACT_NAME)
            CryptoCurrency.USDT -> return getEthWallet()!!.getErc20TokenData(
                Erc20TokenData.USDT_CONTRACT_NAME)
            else -> throw IllegalArgumentException("Not an ERC20 token")
        }
    }
}