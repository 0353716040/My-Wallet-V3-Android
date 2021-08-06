package piuk.blockchain.android.coincore.impl.txEngine.sell

import com.blockchain.android.testutils.rxInit
import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.Product
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.datamanagers.TransferLimits
import com.blockchain.nabu.datamanagers.TransferQuote
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.models.responses.nabu.NabuApiException
import com.blockchain.nabu.models.responses.nabu.NabuErrorCodes
import com.blockchain.nabu.service.TierService
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.testutils.bitcoin
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

import org.amshove.kluent.shouldEqual
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.FeeSelection
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TransactionTarget
import piuk.blockchain.android.coincore.ValidationState
import piuk.blockchain.android.coincore.btc.BtcAddress
import piuk.blockchain.android.coincore.btc.BtcCryptoWalletAccount
import piuk.blockchain.android.coincore.impl.CustodialTradingAccount
import piuk.blockchain.android.coincore.impl.injectMocks
import piuk.blockchain.android.coincore.impl.txEngine.OnChainTxEngineBase
import piuk.blockchain.android.coincore.impl.txEngine.PricedQuote
import piuk.blockchain.android.coincore.impl.txEngine.TransferQuotesEngine
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

class OnChainSellTxEngineTest {

    @get:Rule
    val initSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    private val walletManager: CustodialWalletManager = mock()
    private val quotesEngine: TransferQuotesEngine = mock()
    private val kycTierService: TierService = mock()

    private val exchangeRates: ExchangeRateDataManager = mock {
        on { getLastPrice(SRC_ASSET, TGT_ASSET) }.thenReturn(EXCHANGE_RATE)
    }

    private val currencyPrefs: CurrencyPrefs = mock {
        on { selectedFiatCurrency }.thenReturn(SELECTED_FIAT)
    }

    private val onChainEngine: OnChainTxEngineBase = mock {
        on { sourceAsset }.thenReturn(SRC_ASSET)
    }

    private val subject = OnChainSellTxEngine(
        engine = onChainEngine,
        walletManager = walletManager,
        quotesEngine = quotesEngine,
        kycTierService = kycTierService
    )

    @Before
    fun setup() {
        injectMocks(
            module {
                scope(payloadScopeQualifier) {
                    factory {
                        currencyPrefs
                    }
                }
            }
        )
    }

    @After
    fun teardown() {
        stopKoin()
    }

    @Test
    fun `inputs validate when correct`() {
        val sourceAccount = mockSourceAccount()
        val txTarget = mockTransactionTarget()

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        subject.assertInputsValid()

        // Assert
        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).fiatCurrency
        verifyQuotesEngineStarted()

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test(expected = IllegalStateException::class)
    fun `inputs fail validation when source Account incorrect`() {
        val sourceAccount: CustodialTradingAccount = mock {
            on { asset }.thenReturn(WRONG_ASSET)
        }

        val txTarget = mockTransactionTarget()

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        subject.assertInputsValid()
    }

    @Test(expected = IllegalStateException::class)
    fun `inputs fail validation when target account incorrect`() {
        val sourceAccount = mockSourceAccount()
        val txTarget: CryptoAccount = mock {
            on { asset }.thenReturn(WRONG_ASSET)
        }

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        subject.assertInputsValid()
    }

    /* todo fix test once assertInputsValid is called for decoration engine and making engine.start returns completable
     @Test(expected = IllegalStateException::class)
     fun `inputs fail validation when on chain engine validation fails`() {
         val sourceAccount = mockSourceAccount()
         val txTarget = mockTransactionTarget()

         val txQuote: TransferQuote = mock {
             on { sampleDepositAddress }.thenReturn(SAMPLE_DEPOSIT_ADDRESS
         }
         val pricedQuote: PricedQuote = mock {
             on { transferQuote }.thenReturn(txQuote
         }
         whenever(quotesEngine.pricedQuote).thenReturn(Observable.just(pricedQuote))
         whenever(onChainEngine.assertInputsValid()).thenThrow(IllegalStateException())

         // Act
         subject.start(
             sourceAccount,
             txTarget,
             exchangeRates
         )
         subject.assertInputsValid()
     }*/

    @Test
    fun `asset is returned correctly`() {
        // Arrange
        val sourceAccount = mockSourceAccount()
        val txTarget = mockTransactionTarget()

        // Act
        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val asset = subject.sourceAsset

        // Assert
        asset shouldEqual SRC_ASSET

        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).fiatCurrency
        verifyQuotesEngineStarted()

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `PendingTx is correctly initialised`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val availableBalance: Money = 20.bitcoin()

        whenUserIsGold()
        whenOnChainEngineInitOK(totalBalance, availableBalance)

        val sourceAccount = mockSourceAccount(totalBalance, availableBalance)
        val txTarget = mockTransactionTarget()

        val txQuote: TransferQuote = mock {
            on { sampleDepositAddress }.thenReturn(SAMPLE_DEPOSIT_ADDRESS)
        }

        val pricedQuote: PricedQuote = mock {
            on { transferQuote }.thenReturn(txQuote)
        }

        whenever(quotesEngine.pricedQuote).thenReturn(Observable.just(pricedQuote))

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        // Act
        subject.doInitialiseTx()
            .test()
            .assertValue {
                it.amount == CryptoValue.zero(SRC_ASSET) &&
                    it.totalBalance == totalBalance &&
                    it.availableBalance == availableBalance &&
                    it.feeAmount == CryptoValue.zero(SRC_ASSET) &&
                    it.selectedFiat == TGT_ASSET &&
                    it.confirmations.isEmpty() &&
                    it.minLimit == MIN_GOLD_LIMIT_ASSET &&
                    it.maxLimit == MAX_GOLD_LIMIT_ASSET &&
                    it.validationState == ValidationState.UNINITIALISED &&
                    it.engineState.isEmpty()
            }
            .assertValue { verifyFeeLevels(it.feeSelection) }
            .assertNoErrors()
            .assertComplete()

        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).fiatCurrency
        verifyQuotesEngineStarted()
        verifyOnChainEngineStarted(sourceAccount)
        verifyLimitsFetched()
        verify(quotesEngine).pricedQuote
        verify(exchangeRates).getLastPrice(SRC_ASSET, TGT_ASSET)
        verify(onChainEngine).doInitialiseTx()
        // todo fix once start engine returns completable
        // verify(onChainEngine).assertInputsValid()

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `PendingTx is correctly initialised with flat regular fees`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val availableBalance: Money = 20.bitcoin()

        whenUserIsGold()
        whenOnChainEngineInitOK(totalBalance, availableBalance)

        val sourceAccount = mockSourceAccount(totalBalance, availableBalance)

        val txTarget = mockTransactionTarget()

        val txQuote: TransferQuote = mock {
            on { sampleDepositAddress }.thenReturn(SAMPLE_DEPOSIT_ADDRESS)
        }

        val pricedQuote: PricedQuote = mock {
            on { transferQuote }.thenReturn(txQuote)
        }

        whenever(quotesEngine.pricedQuote).thenReturn(Observable.just(pricedQuote))

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        // Act
        subject.doInitialiseTx()
            .test()
            .assertValue {
                it.amount == CryptoValue.zero(SRC_ASSET) &&
                    it.totalBalance == totalBalance &&
                    it.availableBalance == availableBalance &&
                    it.feeAmount == CryptoValue.zero(SRC_ASSET) &&
                    it.selectedFiat == TGT_ASSET &&
                    it.confirmations.isEmpty() &&
                    it.minLimit == MIN_GOLD_LIMIT_ASSET &&
                    it.maxLimit == MAX_GOLD_LIMIT_ASSET &&
                    it.validationState == ValidationState.UNINITIALISED &&
                    it.engineState.isEmpty()
            }
            .assertValue { verifyFeeLevels(it.feeSelection) }
            .assertNoErrors()
            .assertComplete()

        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).fiatCurrency
        verifyQuotesEngineStarted()
        verifyOnChainEngineStarted(sourceAccount)
        verifyLimitsFetched()
        verify(quotesEngine).pricedQuote
        verify(exchangeRates).getLastPrice(SRC_ASSET, TGT_ASSET)
        verify(onChainEngine).doInitialiseTx()
        // todo fix once start engine returns completable
        // verify(onChainEngine).assertInputsValid()

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `PendingTx initialisation when limit reached`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val availableBalance: Money = 20.bitcoin()

        val sourceAccount = mockSourceAccount(totalBalance, availableBalance)
        val txTarget = mockTransactionTarget()

        val error: NabuApiException = mock {
            on { getErrorCode() }.thenReturn(NabuErrorCodes.PendingOrdersLimitReached)
        }

        whenever(quotesEngine.pricedQuote).thenReturn(Observable.error(error))

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        // Act
        subject.doInitialiseTx()
            .test()
            .assertValue {
                it.amount == CryptoValue.zero(SRC_ASSET) &&
                    it.totalBalance == CryptoValue.zero(SRC_ASSET) &&
                    it.availableBalance == CryptoValue.zero(SRC_ASSET) &&
                    it.feeAmount == CryptoValue.zero(SRC_ASSET) &&
                    it.selectedFiat == TGT_ASSET &&
                    it.confirmations.isEmpty() &&
                    it.minLimit == null &&
                    it.maxLimit == null &&
                    it.validationState == ValidationState.PENDING_ORDERS_LIMIT_REACHED &&
                    it.engineState.isEmpty()
            }
            .assertValue {
                // Special case - when init fails because limits, we expect an empty fee selection:
                it.feeSelection.selectedLevel == FeeLevel.None &&
                    it.feeSelection.availableLevels.size == 1 &&
                    it.feeSelection.availableLevels.contains(FeeLevel.None)
            }
            .assertNoErrors()
            .assertComplete()

        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).fiatCurrency
        verifyQuotesEngineStarted()
        verify(quotesEngine).pricedQuote

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `update amount modifies the pendingTx correctly`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val availableBalance: Money = 20.bitcoin()

        val sourceAccount = mockSourceAccount(totalBalance, availableBalance)
        val txTarget = mockTransactionTarget()

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val inputAmount = 2.bitcoin()
        val expectedFee = 0.bitcoin()

        val expectedFeeLevel = FeeLevel.Priority
        val expectedAvailableFeeLevels = setOf(FeeLevel.Priority)

        val pendingTx = PendingTx(
            amount = CryptoValue.zero(SRC_ASSET),
            totalBalance = CryptoValue.zero(SRC_ASSET),
            availableBalance = CryptoValue.zero(SRC_ASSET),
            feeForFullAvailable = CryptoValue.zero(SRC_ASSET),
            feeAmount = CryptoValue.zero(SRC_ASSET),
            selectedFiat = TGT_ASSET,
            feeSelection = FeeSelection(
                selectedLevel = expectedFeeLevel,
                availableLevels = expectedAvailableFeeLevels,
                asset = FEE_ASSET
            )
        )

        whenever(onChainEngine.doUpdateAmount(inputAmount, pendingTx))
            .thenReturn(
                Single.just(
                    pendingTx.copy(
                        amount = inputAmount,
                        totalBalance = totalBalance,
                        availableBalance = availableBalance,
                        feeAmount = expectedFee
                    )
                )
            )

        // Act
        subject.doUpdateAmount(
            inputAmount,
            pendingTx
        ).test()
            .assertValue {
                it.amount == inputAmount &&
                    it.totalBalance == totalBalance &&
                    it.availableBalance == availableBalance &&
                    it.feeAmount == expectedFee
            }
            .assertValue { verifyFeeLevels(it.feeSelection) }
            .assertComplete()
            .assertNoErrors()

        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).fiatCurrency
        verifyQuotesEngineStarted()
        verify(quotesEngine).updateAmount(inputAmount)
        verify(onChainEngine).doUpdateAmount(inputAmount, pendingTx)

        noMoreInteractions(sourceAccount, txTarget)
    }

    @Test
    fun `doUpdateFeeLevel has no effect`() {
        // Arrange
        val totalBalance: Money = 21.bitcoin()
        val availableBalance: Money = 20.bitcoin()

        val sourceAccount = mockSourceAccount(totalBalance, availableBalance)
        val txTarget = mockTransactionTarget()

        subject.start(
            sourceAccount,
            txTarget,
            exchangeRates
        )

        val initialFeeLevel = FeeLevel.Priority
        val expectedAvailableFeeLevels = setOf(FeeLevel.Priority)

        val pendingTx = PendingTx(
            amount = CryptoValue.zero(SRC_ASSET),
            totalBalance = CryptoValue.zero(SRC_ASSET),
            availableBalance = CryptoValue.zero(SRC_ASSET),
            feeForFullAvailable = CryptoValue.zero(SRC_ASSET),
            feeAmount = CryptoValue.zero(SRC_ASSET),
            selectedFiat = TGT_ASSET,
            feeSelection = FeeSelection(
                selectedLevel = initialFeeLevel,
                availableLevels = expectedAvailableFeeLevels,
                asset = FEE_ASSET
            )
        )

        whenever(
            onChainEngine.doUpdateFeeLevel(
                pendingTx,
                FeeLevel.Regular,
                -1
            )
        ).thenReturn(
            Single.just(
                pendingTx.copy(
                    feeSelection = pendingTx.feeSelection.copy(selectedLevel = FeeLevel.Regular)
                )
            )
        )

        // Act
        subject.doUpdateFeeLevel(
            pendingTx,
            FeeLevel.Priority,
            -1
        ).test()
            .assertValue {
                verifyFeeLevels(it.feeSelection)
            }
            .assertComplete()
            .assertNoErrors()

        verify(sourceAccount, atLeastOnce()).asset
        verify(txTarget, atLeastOnce()).fiatCurrency
        verifyQuotesEngineStarted()

        noMoreInteractions(sourceAccount, txTarget)
    }

    private fun mockSourceAccount(
        totalBalance: Money = CryptoValue.zero(SRC_ASSET),
        availableBalance: Money = CryptoValue.zero(SRC_ASSET)
    ) = mock<BtcCryptoWalletAccount> {
        on { asset }.thenReturn(SRC_ASSET)
        on { accountBalance }.thenReturn(Single.just(totalBalance))
        on { actionableBalance }.thenReturn(Single.just(availableBalance))
    }

    private fun mockTransactionTarget() = mock<FiatAccount> {
        on { fiatCurrency }.thenReturn(TGT_ASSET)
    }

    private fun whenOnChainEngineInitOK(
        totalBalance: Money,
        availableBalance: Money
    ) {
        val initialisedPendingTx = PendingTx(
            amount = CryptoValue.zero(SRC_ASSET),
            totalBalance = totalBalance,
            availableBalance = availableBalance,
            feeForFullAvailable = CryptoValue.zero(SRC_ASSET),
            feeAmount = CryptoValue.zero(SRC_ASSET),
            selectedFiat = SELECTED_FIAT,
            feeSelection = FeeSelection(
                selectedLevel = FeeLevel.Priority,
                availableLevels = setOf(FeeLevel.Priority),
                asset = FEE_ASSET
            )
        )
        whenever(onChainEngine.doInitialiseTx()).thenReturn(Single.just(initialisedPendingTx))
    }

    private fun whenUserIsGold() {
        val kycTiers: KycTiers = mock()
        whenever(kycTierService.tiers()).thenReturn(Single.just(kycTiers))

        whenever(walletManager.getProductTransferLimits(TGT_ASSET, Product.SELL, TransferDirection.FROM_USERKEY))
            .thenReturn(
                Single.just(
                    TransferLimits(
                        minLimit = MIN_GOLD_LIMIT,
                        maxOrder = MAX_GOLD_ORDER,
                        maxLimit = MAX_GOLD_LIMIT
                    )
                )
            )
    }

    private fun verifyLimitsFetched() {
        verify(kycTierService).tiers()
        verify(walletManager).getProductTransferLimits(TGT_ASSET, Product.SELL, TransferDirection.FROM_USERKEY)
    }

    private fun verifyOnChainEngineStarted(srcAccount: CryptoAccount) {
        verify(onChainEngine).start(
            sourceAccount = eq(srcAccount),
            txTarget = argThat { this is BtcAddress && address == SAMPLE_DEPOSIT_ADDRESS },
            exchangeRates = eq(exchangeRates),
            refreshTrigger = any()
        )
    }

    private fun verifyQuotesEngineStarted() {
        verify(quotesEngine).start(
            TransferDirection.FROM_USERKEY,
            CurrencyPair.CryptoToFiatCurrencyPair(SRC_ASSET, TGT_ASSET)
        )
    }

    private fun verifyFeeLevels(
        feeSelection: FeeSelection,
        feeAsset: AssetInfo? = FEE_ASSET
    ) = feeSelection.selectedLevel == EXPECTED_FEE_LEVEL &&
        feeSelection.availableLevels == EXPECTED_FEE_OPTIONS &&
        feeSelection.availableLevels.contains(feeSelection.selectedLevel) &&
        feeSelection.asset == feeAsset &&
        feeSelection.customAmount == -1L

    private fun noMoreInteractions(sourceAccount: BlockchainAccount, txTarget: TransactionTarget) {
        verifyNoMoreInteractions(txTarget)
        verifyNoMoreInteractions(walletManager)
        verifyNoMoreInteractions(currencyPrefs)
        verifyNoMoreInteractions(exchangeRates)
        verifyNoMoreInteractions(quotesEngine)
        verifyNoMoreInteractions(kycTierService)
        verifyNoMoreInteractions(onChainEngine)
        verifyNoMoreInteractions(sourceAccount)
    }

    companion object {
        private const val SELECTED_FIAT = "INR"
        private val SRC_ASSET = CryptoCurrency.BTC
        private const val TGT_ASSET = "EUR"
        private val WRONG_ASSET = CryptoCurrency.BTC
        private val FEE_ASSET = CryptoCurrency.BTC
        private val EXPECTED_FEE_LEVEL = FeeLevel.Priority
        private val EXPECTED_FEE_OPTIONS = setOf(FeeLevel.Priority)
        private val EXCHANGE_RATE = 2.toBigDecimal() // 1 btc == 2 EUR

        private const val SAMPLE_DEPOSIT_ADDRESS = "initial quote deposit address"

        private val MIN_GOLD_LIMIT = FiatValue.fromMajor(TGT_ASSET, 100.toBigDecimal())
        private val MAX_GOLD_ORDER = FiatValue.fromMajor(TGT_ASSET, 500.toBigDecimal())
        private val MAX_GOLD_LIMIT = FiatValue.fromMajor(TGT_ASSET, 2000.toBigDecimal())

        private val MIN_GOLD_LIMIT_ASSET = CryptoValue.fromMajor(SRC_ASSET, 50.toBigDecimal())
        private val MAX_GOLD_ORDER_ASSET = CryptoValue.fromMajor(SRC_ASSET, 250.toBigDecimal())
        private val MAX_GOLD_LIMIT_ASSET = CryptoValue.fromMajor(SRC_ASSET, 1000.toBigDecimal())
    }
}
