package piuk.blockchain.androidcore.data.ethereum

import com.blockchain.android.testutils.rxInit
import com.blockchain.logging.LastTxUpdater
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.ethereum.Erc20TokenData
import info.blockchain.wallet.ethereum.EthAccountApi
import info.blockchain.wallet.ethereum.EthereumWallet
import info.blockchain.wallet.ethereum.data.Erc20AddressResponse
import info.blockchain.wallet.ethereum.data.Erc20TransferResponse
import info.blockchain.wallet.ethereum.data.EthAddressResponse
import info.blockchain.wallet.ethereum.data.EthAddressResponseMap
import info.blockchain.wallet.ethereum.data.EthLatestBlockNumber
import info.blockchain.wallet.ethereum.data.EthTransaction
import info.blockchain.wallet.keys.MasterKey
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be equal to`
import com.nhaarman.mockitokotlin2.any

import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.web3j.crypto.RawTransaction
import piuk.blockchain.androidcore.data.erc20.Erc20DataModel
import piuk.blockchain.androidcore.data.erc20.Erc20Transfer
import piuk.blockchain.androidcore.data.erc20.datastores.Erc20DataStore
import piuk.blockchain.androidcore.data.ethereum.datastores.EthDataStore
import piuk.blockchain.androidcore.data.metadata.MetadataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager
import java.math.BigInteger

@Suppress("ClassName")
private object DUMMY_ERC20 : CryptoCurrency(
    ticker = "DUMMY",
    name = "Dummies",
    categories = emptySet(),
    precisionDp = 8,
    requiredConfirmations = 5,
    l2chain = ETHER,
    l2identifier = "0xF00F00F00F00F00F00FAB",
    colour = "#123456"
)

class EthDataManagerTest {

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
    }

    private val payloadManager: PayloadDataManager = mock()
    private val assetCatalogue: AssetCatalogue = mock()
    private val ethAccountApi: EthAccountApi = mock()
    private val ethDataStore: EthDataStore = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private val erc20DataStore: Erc20DataStore = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private val walletOptionsDataManager: WalletOptionsDataManager = mock()
    private val metadataManager: MetadataManager = mock()
    private val lastTxUpdater: LastTxUpdater = mock()
    private val rxBus = RxBus()

    private val subject = EthDataManager(
        payloadDataManager = payloadManager,
        ethAccountApi = ethAccountApi,
        ethDataStore = ethDataStore,
        erc20DataStore = erc20DataStore,
        walletOptionsDataManager = walletOptionsDataManager,
        metadataManager = metadataManager,
        lastTxUpdater = lastTxUpdater,
        rxBus = rxBus
    )

    @Test
    fun clearEthAccountDetails() {
        // Arrange

        // Act
        subject.clearAccountDetails()
        // Assert

        verify(ethDataStore).clearData()
        verify(erc20DataStore).clearData()
        verifyNoMoreInteractions(ethDataStore)
        verifyNoMoreInteractions(erc20DataStore)
    }

    @Test
    fun fetchEthAddress() {
        // Arrange
        val ethAddress = "ADDRESS"
        whenever(ethDataStore.ethWallet!!.account.address).thenReturn(ethAddress)
        val ethAddressResponseMap: EthAddressResponseMap = mock()
        whenever(ethAccountApi.getEthAddress(listOf(ethAddress)))
            .thenReturn(Observable.just(ethAddressResponseMap))
        // Act
        val testObserver = subject.fetchEthAddress().test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        verify(ethDataStore, atLeastOnce()).ethWallet
        verify(ethDataStore).ethAddressResponse = any()
        verifyZeroInteractions(ethDataStore)
        verify(ethAccountApi).getEthAddress(listOf(ethAddress))
        verifyNoMoreInteractions(ethAccountApi)
    }

    @Test
    fun `get balance found`() {
        // Arrange
        val ethAddress = "ADDRESS"
        val ethAddressResponseMap: EthAddressResponseMap = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
        val response: EthAddressResponse = mock()
        whenever(response.balance).thenReturn(BigInteger.TEN)
        whenever(ethAddressResponseMap.ethAddressResponseMap.values).thenReturn(mutableListOf(response))
        whenever(ethAccountApi.getEthAddress(listOf(ethAddress)))
            .thenReturn(Observable.just(ethAddressResponseMap))
        // Act
        val testObserver = subject.getBalance(ethAddress).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(BigInteger.TEN)
        verify(ethAccountApi).getEthAddress(listOf(ethAddress))
        verifyNoMoreInteractions(ethAccountApi)
    }

    @Test
    fun `get balance error, still returns value`() {
        // Arrange
        val ethAddress = "ADDRESS"
        whenever(ethAccountApi.getEthAddress(listOf(ethAddress)))
            .thenReturn(Observable.error(Exception()))
        // Act
        val testObserver = subject.getBalance(ethAddress).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(BigInteger.ZERO)
        verify(ethAccountApi).getEthAddress(listOf(ethAddress))
        verifyNoMoreInteractions(ethAccountApi)
    }

    @Test
    fun getEthResponseModel() {
        // Arrange

        // Act
        subject.getEthResponseModel()
        // Assert
        verify(ethDataStore).ethAddressResponse
        verifyNoMoreInteractions(ethDataStore)
    }

    @Test
    fun getEthWallet() {
        // Arrange
        // Act
        subject.getEthWallet()
        // Assert
        verify(ethDataStore).ethWallet
        verifyNoMoreInteractions(ethDataStore)
    }

    @Test
    fun `getEthTransactions response found with 3 transactions`() {
        // Arrange
        val ethAddress = "ADDRESS"
        val ethTransaction: EthTransaction = mock()
        whenever(ethDataStore.ethWallet!!.account.address).thenReturn(ethAddress)
        whenever(ethAccountApi.getEthTransactions(any()))
            .thenReturn(Single.just(listOf(ethTransaction, ethTransaction, ethTransaction)))
        // Act
        val testObserver = subject.getEthTransactions().test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        val values = testObserver.values()
        values[0] `should contain` ethTransaction

        values.size `should be equal to` 1
    }

    @Test
    fun `getEthTransactions response not found`() {
        // Arrange
        whenever(ethDataStore.ethWallet!!.account.address).thenReturn(null)
        // Act
        val testObserver = subject.getEthTransactions().test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        testObserver.assertValueAt(0, emptyList())
    }

    @Test
    fun `lastTx is pending when there is at least one transaction pending`() {
        // Arrange
        whenever(ethDataStore.ethWallet!!.account.address).thenReturn("Address")

        whenever(ethAccountApi.getLastEthTransaction(any()))
            .thenReturn(Maybe.just(EthTransaction(state = "PENDING")))
        // Act
        val result = subject.isLastTxPending().test()
        // Assert

        result.assertValueCount(1)
        result.assertValueAt(0, true)
    }

    @Test
    fun `lastTx is not pending when there is no pending tx`() {
        // Arrange
        whenever(ethDataStore.ethWallet!!.account.address).thenReturn("Address")

        whenever(ethAccountApi.getLastEthTransaction(any()))
            .thenReturn(Maybe.just(EthTransaction(state = "CONFIRMED")))
        // Act
        val result = subject.isLastTxPending().test()
        // Assert

        result.assertValueCount(1)
        result.assertValueAt(0, false)
    }

    @Test
    fun `lastTx is not pending when there is no tx`() {
        // Arrange
        whenever(ethDataStore.ethWallet!!.account.address).thenReturn("Address")

        whenever(ethAccountApi.getLastEthTransaction(any()))
            .thenReturn(Maybe.empty())
        // Act
        val result = subject.isLastTxPending().test()
        // Assert

        result.assertValueCount(1)
        result.assertValueAt(0, false)
    }

    @Test
    fun getLatestBlock() {
        // Arrange
        val latestBlock = EthLatestBlockNumber()
        whenever(ethAccountApi.latestBlockNumber).thenReturn(Single.just(latestBlock))
        // Act
        val testObserver = subject.getLatestBlockNumber().test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(latestBlock)
        verify(ethAccountApi).latestBlockNumber
        verifyNoMoreInteractions(ethAccountApi)
    }

    @Test
    fun getIfContract() {
        // Arrange
        val address = "ADDRESS"
        whenever(ethAccountApi.getIfContract(address)).thenReturn(Observable.just(true))
        // Act
        val testObserver = subject.isContractAddress(address).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(true)
        verify(ethAccountApi).getIfContract(address)
        verifyNoMoreInteractions(ethAccountApi)
    }

    @Test
    fun `getTransactionNotes returns string object`() {
        // Arrange
        val hash = "HASH"
        val notes = "NOTES"
        whenever(ethDataStore.ethWallet!!.txNotes[hash]).thenReturn(notes)
        // Act
        val result = subject.getTransactionNotes(hash)
        // Assert
        verify(ethDataStore, atLeastOnce()).ethWallet
        verifyNoMoreInteractions(ethDataStore)
        result `should be equal to` notes
    }

    @Test
    fun `getTransactionNotes returns null object as wallet is missing`() {
        // Arrange
        val hash = "HASH"
        whenever(ethDataStore.ethWallet).thenReturn(null)
        // Act
        val result = subject.getTransactionNotes(hash)
        // Assert
        verify(ethDataStore, atLeastOnce()).ethWallet
        verifyNoMoreInteractions(ethDataStore)
        result `should be equal to` null
    }

    @Test
    fun `updateTransactionNotes success`() {
        // Arrange
        val hash = "HASH"
        val notes = "NOTES"
        val ethereumWallet: EthereumWallet = mock()
        whenever(ethDataStore.ethWallet).thenReturn(ethereumWallet)
        whenever(ethDataStore.ethWallet!!.toJson()).thenReturn("{}")
        whenever(metadataManager.saveToMetadata(any(), any())).thenReturn(Completable.complete())
        // Act
        val testObserver = subject.updateTransactionNotes(hash, notes).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        verify(ethDataStore, atLeastOnce()).ethWallet
        verifyNoMoreInteractions(ethDataStore)
        verify(metadataManager).saveToMetadata(any(), any())
        verifyNoMoreInteractions(metadataManager)
    }

    @Test
    fun `updateTransactionNotes wallet not found`() {
        // Arrange
        val hash = "HASH"
        val notes = "NOTES"
        whenever(ethDataStore.ethWallet).thenReturn(null)
        // Act
        val testObserver = subject.updateTransactionNotes(hash, notes).test()
        // Assert
        testObserver.assertNotComplete()
        testObserver.assertError(IllegalStateException::class.java)
        verify(ethDataStore).ethWallet
        verifyNoMoreInteractions(ethDataStore)
    }

    @Test
    fun signEthTransaction() {
        // Arrange
        val rawTransaction: RawTransaction = mock()
        val byteArray = ByteArray(32)
        val masterKey: MasterKey = mock()

        whenever(ethDataStore.ethWallet!!.account!!.signTransaction(
            eq(rawTransaction),
            eq(masterKey)
        )).thenReturn(byteArray)

        whenever(payloadManager.masterKey).thenReturn(masterKey)

        // Act
        val testObserver = subject.signEthTransaction(rawTransaction, "").test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(byteArray)
        verify(ethDataStore, atLeastOnce()).ethWallet
        verifyNoMoreInteractions(ethDataStore)
    }

    @Test
    fun pushEthTx() {
        // Arrange
        val byteArray = ByteArray(32)
        val hash = "HASH"
        whenever(ethAccountApi.pushTx(any())).thenReturn(Observable.just(hash))
        whenever(lastTxUpdater.updateLastTxTime()).thenReturn(Completable.complete())
        // Act
        val testObserver = subject.pushEthTx(byteArray).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(hash)
        verify(ethAccountApi).pushTx(any())
        verifyNoMoreInteractions(ethAccountApi)
    }

    @Test
    fun `pushEthTx returns hash despite update last tx failing`() {
        // Arrange
        val byteArray = ByteArray(32)
        val hash = "HASH"
        whenever(ethAccountApi.pushTx(any())).thenReturn(Observable.just(hash))
        whenever(lastTxUpdater.updateLastTxTime()).thenReturn(Completable.error(Exception()))
        // Act
        val testObserver = subject.pushEthTx(byteArray).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(hash)
        verify(ethAccountApi).pushTx(any())
        verifyNoMoreInteractions(ethAccountApi)
    }

    @Test
    fun setLastTxHashObservable() {
        // Arrange
        val hash = "HASH"
        val timestamp = System.currentTimeMillis()
        val ethereumWallet: EthereumWallet = mock()
        whenever(ethDataStore.ethWallet).thenReturn(ethereumWallet)
        whenever(ethDataStore.ethWallet!!.toJson()).thenReturn("{}")
        whenever(metadataManager.saveToMetadata(any(), any())).thenReturn(Completable.complete())
        // Act
        val testObserver = subject.setLastTxHashObservable(hash, timestamp).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(hash)
        verify(ethDataStore, atLeastOnce()).ethWallet
        verifyNoMoreInteractions(ethDataStore)
        verify(ethDataStore.ethWallet)!!.toJson()
        verify(ethereumWallet).setLastTransactionHash(hash)
        verify(ethereumWallet).setLastTransactionTimestamp(timestamp)
        verifyNoMoreInteractions(ethereumWallet)
        verify(metadataManager).saveToMetadata(any(), any())
        verifyNoMoreInteractions(metadataManager)
    }

    private val erc20AddressResponsePax = Erc20AddressResponse().apply {
        accountHash = "0x4058a004dd718babab47e14dd0d744742e5b9903"
        tokenHash = "0x8e870d67f660d95d5be530380d0ec0bd388289e1"
        balance = 2838277460000000000.toBigInteger()
        transfers = listOf(Erc20TransferResponse(), Erc20TransferResponse())
    }

    @Test
    fun fetchErc20AddressPax() {
        // Arrange
        val ethAddress = "ADDRESS"
        val tokenData: Erc20TokenData = mock {
            on { contractAddress }.thenReturn("CONTRACT_ADDRESS")
        }
        whenever(ethDataStore.ethWallet!!.account.address).thenReturn(ethAddress)
        whenever(ethDataStore.ethWallet!!.getErc20TokenData(any()))
            .thenReturn(tokenData)
        whenever(ethAccountApi.getErc20Address("ADDRESS", "CONTRACT_ADDRESS"))
            .thenReturn(Observable.just(erc20AddressResponsePax))
        ethDataStore.ethWallet
        // Act
        val testObserver = subject.fetchErc20DataModel(DUMMY_ERC20).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue {
            it.accountHash == erc20AddressResponsePax.accountHash &&
            it.totalBalance.toBigInteger() == erc20AddressResponsePax.balance &&
            it.totalBalance.currency == DUMMY_ERC20
        }
    }

    @Test
    fun `no transactions should be returned from empty model PAX`() {
        whenever(erc20DataStore.erc20DataModel[DUMMY_ERC20])
            .thenReturn(null)

        // Act
        subject.getErc20Transactions(DUMMY_ERC20)
            .test()
            .assertValue { it.isEmpty() }
            .assertComplete()
            .assertNoErrors()
    }

    @Test
    fun `transactions from not null model should return the correct transactions PAX`() {
        // Arrange
        whenever(erc20DataStore.erc20DataModel[DUMMY_ERC20])
            .thenReturn(Erc20DataModel(erc20AddressResponsePax, DUMMY_ERC20))

        // Act
        val testObserver = subject.getErc20Transactions(DUMMY_ERC20)
            .test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue {
            it[0] == Erc20Transfer(erc20AddressResponsePax.transfers[0]) &&
            it[1] == Erc20Transfer(erc20AddressResponsePax.transfers[1]) &&
            it.size == 2
        }
    }

    @Test
    fun `account has should be the correct one PAX`() {
        // Arrange
        whenever(erc20DataStore.erc20DataModel[DUMMY_ERC20])
            .thenReturn(Erc20DataModel(erc20AddressResponsePax, DUMMY_ERC20))

        // Act
        val testObserver = subject.getErc20AccountHash(DUMMY_ERC20)
            .test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue {
            it == erc20AddressResponsePax.accountHash
        }
    }

    @Test
    fun `raw transaction fields should be correct PAX`() {
        val nonce = 10.toBigInteger()
        val to = "0xD1220A0cf47c7B9Be7A2E63A89F429762e7b9aDb"
        val contractAddress = "0x8E870D67F660D95d5be530380D0eC0bd388289E1"
        val gasPrice = 1.toBigInteger()
        val gasLimit = 5.toBigInteger()
        val amount = 7.toBigInteger()

        val rawTransaction =
            subject.createErc20Transaction(nonce, to, contractAddress, gasPrice, gasLimit, amount)

        assertEquals(nonce, rawTransaction!!.nonce)
        assertEquals(gasPrice, rawTransaction.gasPrice)
        assertEquals(gasLimit, rawTransaction.gasLimit)
        assertEquals("0x8E870D67F660D95d5be530380D0eC0bd388289E1", rawTransaction.to)
        assertEquals(0.toBigInteger(), rawTransaction.value)
        assertEquals(
            "a9059cbb000000000000000000000000d1220a0cf47c7b9be7a2e63a89f429762e7b" +
            "9adb0000000000000000000000000000000000000000000000000000000000000007",
            rawTransaction.data
        )
    }
}
