package com.blockchain.sunriver

import com.blockchain.sunriver.datamanager.XlmAccount
import com.blockchain.sunriver.datamanager.XlmMetaData
import com.blockchain.sunriver.datamanager.XlmMetaDataInitializer
import com.blockchain.sunriver.models.XlmTransaction
import com.blockchain.testutils.lumens
import com.blockchain.testutils.rxInit
import com.blockchain.testutils.stroops
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.AccountReference
import info.blockchain.balance.CryptoValue
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.amshove.kluent.`it returns`
import org.amshove.kluent.`it throws`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should throw`
import org.amshove.kluent.mock
import org.junit.Rule
import org.junit.Test
import org.stellar.sdk.AssetTypeNative
import org.stellar.sdk.KeyPair
import org.stellar.sdk.PaymentOperation
import org.stellar.sdk.Transaction
import org.stellar.sdk.responses.AccountResponse
import org.stellar.sdk.responses.TransactionResponse
import org.stellar.sdk.responses.operations.CreateAccountOperationResponse
import org.stellar.sdk.responses.operations.ManageDataOperationResponse
import org.stellar.sdk.responses.operations.OperationResponse
import org.stellar.sdk.responses.operations.PaymentOperationResponse
import org.stellar.sdk.responses.operations.SetOptionsOperationResponse

class XlmDataManagerTest {

    @get:Rule
    val initSchedulers = rxInit {
        ioTrampoline()
    }

    @Test
    fun `fee matches the SDK hardcoded figure`() {
        val singleOperationFeeFromSdk =
            Transaction.Builder(AccountResponse(KeyPair.random(), 0))
                .addOperation(
                    PaymentOperation.Builder(
                        KeyPair.random(),
                        AssetTypeNative(),
                        "10"
                    ).build()
                )
                .build()
                .fee

        val fees = XlmDataManager(mock(), mock(), mock()).fees()
        fees `should equal` singleOperationFeeFromSdk.stroops()
        fees `should equal` 100.stroops()
    }

    @Test
    fun `getBalance - there should be no interactions before subscribe`() {
        verifyNoInteractionsBeforeSubscribe {
            getBalance()
        }
    }

    @Test
    fun `getBalance with reference - there should be no interactions before subscribe`() {
        verifyNoInteractionsBeforeSubscribe {
            getBalance(AccountReference.Xlm("", "ANY"))
        }
    }

    @Test
    fun `get balance for an address`() {
        XlmDataManager(
            givenBalances("ANY" to 123.lumens()),
            mock(),
            givenNoExpectedSecretAccess()
        )
            .getBalance(AccountReference.Xlm("", "ANY"))
            .testSingle() `should equal` 123.lumens()
    }

    @Test
    fun `get default account balance`() {
        XlmDataManager(
            givenBalances("GABC1234" to 456.lumens()),
            givenMetaDataMaybe(
                XlmMetaData(
                    defaultAccountIndex = 0,
                    accounts = listOf(
                        XlmAccount(
                            publicKey = "GABC1234",
                            label = "",
                            archived = false
                        )
                    ),
                    transactionNotes = emptyMap()
                )
            ),
            givenNoExpectedSecretAccess()
        )
            .getBalance()
            .testSingle() `should equal` 456.lumens()
    }

    @Test
    fun `get default account max spendable`() {
        XlmDataManager(
            givenBalancesAndMinimums(
                "GABC1234" to BalanceAndMin(
                    balance = 456.lumens(),
                    minimumBalance = 4.lumens()
                )
            ),
            givenMetaDataMaybe(
                XlmMetaData(
                    defaultAccountIndex = 0,
                    accounts = listOf(
                        XlmAccount(
                            publicKey = "GABC1234",
                            label = "",
                            archived = false
                        )
                    ),
                    transactionNotes = emptyMap()
                )
            ),
            givenNoExpectedSecretAccess()
        )
            .getMaxSpendable()
            .testSingle() `should equal` 456.lumens() - 4.lumens() - 100.stroops()
    }

    @Test
    fun `get default account balance without metadata`() {
        XlmDataManager(
            givenBalances("GABC1234" to 456.lumens()),
            givenNoMetaData(),
            givenNoExpectedSecretAccess()
        )
            .getBalance()
            .testSingle() `should equal` 0.lumens()
    }

    @Test
    fun `get default account 0`() {
        XlmDataManager(
            mock(),
            givenMetaDataPrompt(
                XlmMetaData(
                    defaultAccountIndex = 0,
                    accounts = listOf(
                        XlmAccount(
                            publicKey = "ADDRESS1",
                            label = "Account #1",
                            archived = false
                        ),
                        XlmAccount(
                            publicKey = "ADDRESS2",
                            label = "Account #2",
                            archived = false
                        )
                    ),
                    transactionNotes = emptyMap()
                )
            ),
            givenNoExpectedSecretAccess()
        )
            .defaultAccount()
            .testSingle()
            .apply {
                label `should equal` "Account #1"
                accountId `should equal` "ADDRESS1"
            }
    }

    @Test
    fun `get maybe default account 0`() {
        XlmDataManager(
            mock(),
            givenMetaDataMaybe(
                XlmMetaData(
                    defaultAccountIndex = 0,
                    accounts = listOf(
                        XlmAccount(
                            publicKey = "ADDRESS1",
                            label = "Account #1",
                            archived = false
                        ),
                        XlmAccount(
                            publicKey = "ADDRESS2",
                            label = "Account #2",
                            archived = false
                        )
                    ),
                    transactionNotes = emptyMap()
                )
            ),
            givenNoExpectedSecretAccess()
        )
            .maybeDefaultAccount()
            .toSingle()
            .testSingle()
            .apply {
                label `should equal` "Account #1"
                accountId `should equal` "ADDRESS1"
            }
    }

    @Test
    fun `get default account 1`() {
        XlmDataManager(
            mock(),
            givenMetaDataPrompt(
                XlmMetaData(
                    defaultAccountIndex = 1,
                    accounts = listOf(
                        XlmAccount(
                            publicKey = "ADDRESS1",
                            label = "Account #1",
                            archived = false
                        ),
                        XlmAccount(
                            publicKey = "ADDRESS2",
                            label = "Account #2",
                            archived = false
                        )
                    ),
                    transactionNotes = emptyMap()
                )
            ),
            givenNoExpectedSecretAccess()
        )
            .defaultAccount()
            .testSingle()
            .apply {
                label `should equal` "Account #2"
                accountId `should equal` "ADDRESS2"
            }
    }

    @Test
    fun `get maybe default account 1`() {
        XlmDataManager(
            mock(),
            givenMetaDataMaybe(
                XlmMetaData(
                    defaultAccountIndex = 1,
                    accounts = listOf(
                        XlmAccount(
                            publicKey = "ADDRESS1",
                            label = "Account #1",
                            archived = false
                        ),
                        XlmAccount(
                            publicKey = "ADDRESS2",
                            label = "Account #2",
                            archived = false
                        )
                    ),
                    transactionNotes = emptyMap()
                )
            ),
            givenNoExpectedSecretAccess()
        )
            .maybeDefaultAccount()
            .toSingle()
            .testSingle()
            .apply {
                label `should equal` "Account #2"
                accountId `should equal` "ADDRESS2"
            }
    }

    @Test
    fun `get default account 1 - balance`() {
        XlmDataManager(
            givenBalances(
                "ADDRESS1" to 10.lumens(),
                "ADDRESS2" to 20.lumens()
            ),
            givenMetaDataMaybe(
                XlmMetaData(
                    defaultAccountIndex = 1,
                    accounts = listOf(
                        XlmAccount(
                            publicKey = "ADDRESS1",
                            label = "Account #1",
                            archived = false
                        ),
                        XlmAccount(
                            publicKey = "ADDRESS2",
                            label = "Account #2",
                            archived = false
                        )
                    ),
                    transactionNotes = emptyMap()
                )
            ),
            givenNoExpectedSecretAccess()
        )
            .getBalance()
            .testSingle() `should equal` 20.lumens()
    }

    @Test
    fun `get either balance by address`() {
        val xlmDataManager = XlmDataManager(
            givenBalances(
                "ADDRESS1" to 10.lumens(),
                "ADDRESS2" to 20.lumens()
            ),
            givenMetaDataPrompt(
                XlmMetaData(
                    defaultAccountIndex = 1,
                    accounts = listOf(
                        XlmAccount(
                            publicKey = "ADDRESS1",
                            label = "Account #1",
                            archived = false
                        ),
                        XlmAccount(
                            publicKey = "ADDRESS2",
                            label = "Account #2",
                            archived = false
                        )
                    ),
                    transactionNotes = emptyMap()
                )
            ),
            givenNoExpectedSecretAccess()
        )
        xlmDataManager
            .getBalance(AccountReference.Xlm("", "ADDRESS1"))
            .testSingle() `should equal` 10.lumens()
        xlmDataManager
            .getBalance(AccountReference.Xlm("", "ADDRESS2"))
            .testSingle() `should equal` 20.lumens()
    }
}

class XlmDataManagerTransactionListTest {

    @get:Rule
    val initSchedulers = rxInit {
        ioTrampoline()
    }

    @Test
    fun `getTransactionList - there should be no interactions before subscribe`() {
        verifyNoInteractionsBeforeSubscribe {
            getTransactionList()
        }
    }

    @Test
    fun `getTransactionList with reference - there should be no interactions before subscribe`() {
        verifyNoInteractionsBeforeSubscribe {
            getTransactionList(AccountReference.Xlm("", "ANY"))
        }
    }

    @Test
    fun `get transaction list from default account`() {
        XlmDataManager(
            givenTransactions("GC24LNYWXIYYB6OGCMAZZ5RX6WPI2F74ZV7HNBV4ADALLXJRT7ZTLHP2" to getResponseList()),
            givenMetaDataPrompt(
                XlmMetaData(
                    defaultAccountIndex = 0,
                    accounts = listOf(
                        XlmAccount(
                            publicKey = "GC24LNYWXIYYB6OGCMAZZ5RX6WPI2F74ZV7HNBV4ADALLXJRT7ZTLHP2",
                            label = "",
                            archived = false
                        )
                    ),
                    transactionNotes = emptyMap()
                )
            ),
            givenNoExpectedSecretAccess()
        )
            .getTransactionList()
            .testSingle() `should equal` getXlmList()
    }

    @Test
    fun `get transactions`() {
        XlmDataManager(
            givenTransactions("GC24LNYWXIYYB6OGCMAZZ5RX6WPI2F74ZV7HNBV4ADALLXJRT7ZTLHP2" to getResponseList()),
            mock(),
            givenNoExpectedSecretAccess()
        )
            .getTransactionList(AccountReference.Xlm("", "GC24LNYWXIYYB6OGCMAZZ5RX6WPI2F74ZV7HNBV4ADALLXJRT7ZTLHP2"))
            .testSingle() `should equal` getXlmList()
    }

    @Test
    fun `map response rejects unsupported types`() {
        val unsupportedResponse: ManageDataOperationResponse = mock();
        {
            mapOperationResponse(unsupportedResponse, "")
        } `should throw` IllegalArgumentException::class
    }

    @Test
    fun `get transaction fee`() {
        XlmDataManager(
            givenTransaction("HASH" to getTransaction()),
            mock(),
            givenNoExpectedSecretAccess()
        )
            .getTransactionFee("HASH")
            .testSingle() `should equal` 100.stroops()
    }

    private fun getXlmList(): List<XlmTransaction> = listOf(
        XlmTransaction(
            timeStamp = "createdAt",
            total = 10000.lumens(),
            hash = "transactionHash",
            to = HorizonKeyPair.Public("GCO724H2FOHPBFF4OQ6IB5GB3CVE4W3UGDY4RIHHG6UPQ2YZSSCINMAI"),
            from = HorizonKeyPair.Public("GAIH3ULLFQ4DGSECF2AR555KZ4KNDGEKN4AFI4SU2M7B43MGK3QJZNSR")
        ),
        XlmTransaction(
            timeStamp = "createdAt",
            total = (-100).lumens(),
            hash = "transactionHash",
            to = HorizonKeyPair.Public("GBAHSNSG37BOGBS4GXUPMHZWJQ22WIOJQYORRBHTABMMU6SGSKDEAOPT"),
            from = HorizonKeyPair.Public("GC24LNYWXIYYB6OGCMAZZ5RX6WPI2F74ZV7HNBV4ADALLXJRT7ZTLHP2")
        )
    )

    private fun getResponseList(): List<OperationResponse> {
        val mockIgnored: SetOptionsOperationResponse = mock()
        val mockCreate: CreateAccountOperationResponse = mock {
            on { createdAt } `it returns` "createdAt"
            on { startingBalance } `it returns` "10000"
            on { transactionHash } `it returns` "transactionHash"
            on { account } `it returns`
                KeyPair.fromAccountId("GCO724H2FOHPBFF4OQ6IB5GB3CVE4W3UGDY4RIHHG6UPQ2YZSSCINMAI")
            on { funder } `it returns`
                KeyPair.fromAccountId("GAIH3ULLFQ4DGSECF2AR555KZ4KNDGEKN4AFI4SU2M7B43MGK3QJZNSR")
        }
        val mockPayment: PaymentOperationResponse = mock {
            on { createdAt } `it returns` "createdAt"
            on { amount } `it returns` "100"
            on { transactionHash } `it returns` "transactionHash"
            on { to } `it returns`
                KeyPair.fromAccountId("GBAHSNSG37BOGBS4GXUPMHZWJQ22WIOJQYORRBHTABMMU6SGSKDEAOPT")
            on { from } `it returns`
                KeyPair.fromAccountId("GC24LNYWXIYYB6OGCMAZZ5RX6WPI2F74ZV7HNBV4ADALLXJRT7ZTLHP2")
            on { type } `it returns` "payment"
        }

        return listOf(mockIgnored, mockCreate, mockPayment)
    }

    private fun getTransaction(): TransactionResponse = mock {
        on { feePaid } `it returns` 100L
    }
}

class XlmDataManagerSendTransactionTest {

    @get:Rule
    val initSchedulers = rxInit {
        ioTrampoline()
    }

    @Test
    fun `sendFromDefault - there should be no interactions before subscribe`() {
        verifyNoInteractionsBeforeSubscribe {
            sendFromDefault(HorizonKeyPair.Public("ANY"), 100.lumens())
        }
    }

    @Test
    fun `sendFunds with reference - there should be no interactions before subscribe`() {
        verifyNoInteractionsBeforeSubscribe {
            sendFunds(
                AccountReference.Xlm("", "ANY"),
                100.lumens(),
                "ANY"
            )
        }
    }

    @Test
    fun `can send`() {
        val horizonProxy: HorizonProxy = mock {
            on {
                sendTransaction(
                    source = keyPairEq(
                        KeyPair.fromSecretSeed(
                            "SCIB3NRLJR6BPQRF3WCSPBICSZIXNLGHKWDZZ32OA6TFOJJKWGNHOHIA"
                        )
                    ),
                    destination = keyPairEq(
                        KeyPair.fromAccountId(
                            "GDKDDBJNREDV4ITL65Z3PNKAGWYJQL7FZJSV4P2UWGLRXI6AWT36UED3"
                        )
                    ),
                    amount = eq(199.456.lumens())
                )
            } `it returns` HorizonProxy.SendResult(
                success = true,
                transaction = mock()
            )
        }
        XlmDataManager(
            horizonProxy,
            givenMetaDataPrompt(
                XlmMetaData(
                    defaultAccountIndex = 0,
                    accounts = listOf(
                        XlmAccount(
                            publicKey = "GB5INYM5XFJHAIQYXUQMGMQEM5KWBM4OYVLTWQI5JSQBRQKFYH3M3XWR",
                            label = "",
                            archived = false
                        )
                    ),
                    transactionNotes = emptyMap()
                )
            ),
            givenPrivateForPublic(
                "GB5INYM5XFJHAIQYXUQMGMQEM5KWBM4OYVLTWQI5JSQBRQKFYH3M3XWR" to
                    "SCIB3NRLJR6BPQRF3WCSPBICSZIXNLGHKWDZZ32OA6TFOJJKWGNHOHIA"
            )
        ).sendFromDefault(
            HorizonKeyPair.createValidatedPublic("GDKDDBJNREDV4ITL65Z3PNKAGWYJQL7FZJSV4P2UWGLRXI6AWT36UED3"),
            199.456.lumens()
        ).test()
            .assertNoErrors()
            .assertComplete()
        horizonProxy.verifyJustTheOneSendAttempt()
    }

    @Test
    fun `any failure bubbles up`() {
        val horizonProxy: HorizonProxy = mock {
            on { sendTransaction(any(), any(), any()) } `it returns` HorizonProxy.SendResult(
                success = false,
                transaction = mock()
            )
        }
        XlmDataManager(
            horizonProxy,
            givenMetaDataPrompt(
                XlmMetaData(
                    defaultAccountIndex = 0,
                    accounts = listOf(
                        XlmAccount(
                            publicKey = "GB5INYM5XFJHAIQYXUQMGMQEM5KWBM4OYVLTWQI5JSQBRQKFYH3M3XWR",
                            label = "",
                            archived = false
                        )
                    ),
                    transactionNotes = emptyMap()
                )
            ),
            givenPrivateForPublic(
                "GB5INYM5XFJHAIQYXUQMGMQEM5KWBM4OYVLTWQI5JSQBRQKFYH3M3XWR" to
                    "SCIB3NRLJR6BPQRF3WCSPBICSZIXNLGHKWDZZ32OA6TFOJJKWGNHOHIA"
            )
        ).sendFromDefault(
            HorizonKeyPair.createValidatedPublic("GDKDDBJNREDV4ITL65Z3PNKAGWYJQL7FZJSV4P2UWGLRXI6AWT36UED3"),
            199.456.lumens()
        ).test()
            .assertFailureAndMessage(XlmSendException::class.java, "Send failed")
        horizonProxy.verifyJustTheOneSendAttempt()
    }

    @Test
    fun `can send from a specific account`() {
        val horizonProxy: HorizonProxy = mock {
            on {
                sendTransaction(
                    source = keyPairEq(
                        KeyPair.fromSecretSeed(
                            "SCIB3NRLJR6BPQRF3WCSPBICSZIXNLGHKWDZZ32OA6TFOJJKWGNHOHIA"
                        )
                    ),
                    destination = keyPairEq(
                        KeyPair.fromAccountId(
                            "GDKDDBJNREDV4ITL65Z3PNKAGWYJQL7FZJSV4P2UWGLRXI6AWT36UED3"
                        )
                    ),
                    amount = eq(1.23.lumens())
                )
            } `it returns` HorizonProxy.SendResult(
                success = true,
                transaction = mock()
            )
        }
        XlmDataManager(
            horizonProxy,
            givenMetaDataPrompt(
                XlmMetaData(
                    defaultAccountIndex = 0,
                    accounts = listOf(
                        XlmAccount(
                            publicKey = "GBVO27UV2OXJFLFNXHMXOR5WRPKETM64XAQHUEKQ67W5LQDPZCDSTUTF",
                            label = "",
                            archived = false
                        ),
                        XlmAccount(
                            publicKey = "GB5INYM5XFJHAIQYXUQMGMQEM5KWBM4OYVLTWQI5JSQBRQKFYH3M3XWR",
                            label = "",
                            archived = false
                        )
                    ),
                    transactionNotes = emptyMap()
                )
            ),
            givenPrivateForPublic(
                "GBVO27UV2OXJFLFNXHMXOR5WRPKETM64XAQHUEKQ67W5LQDPZCDSTUTF" to
                    "SBGS72YDKMO7K6YBDGXSD2U7BGFK3LRDCR36KNNXVL7N7L2OSEQSWO25",
                "GB5INYM5XFJHAIQYXUQMGMQEM5KWBM4OYVLTWQI5JSQBRQKFYH3M3XWR" to
                    "SCIB3NRLJR6BPQRF3WCSPBICSZIXNLGHKWDZZ32OA6TFOJJKWGNHOHIA"
            )
        ).sendFunds(
            AccountReference.Xlm("", "GB5INYM5XFJHAIQYXUQMGMQEM5KWBM4OYVLTWQI5JSQBRQKFYH3M3XWR"),
            1.23.lumens(),
            "GDKDDBJNREDV4ITL65Z3PNKAGWYJQL7FZJSV4P2UWGLRXI6AWT36UED3"
        ).test()
            .assertNoErrors()
            .assertComplete()
        horizonProxy.verifyJustTheOneSendAttempt()
    }

    @Test
    fun `when can't find the specific account in the meta data - throw`() {
        val horizonProxy = mock<HorizonProxy>()
        XlmDataManager(
            horizonProxy,
            givenMetaDataPrompt(
                XlmMetaData(
                    defaultAccountIndex = 0,
                    accounts = listOf(
                        XlmAccount(
                            publicKey = "GBVO27UV2OXJFLFNXHMXOR5WRPKETM64XAQHUEKQ67W5LQDPZCDSTUTF",
                            label = "",
                            archived = false
                        )
                    ),
                    transactionNotes = emptyMap()
                )
            ),
            givenNoExpectedSecretAccess()
        ).sendFunds(
            AccountReference.Xlm("", "GB5INYM5XFJHAIQYXUQMGMQEM5KWBM4OYVLTWQI5JSQBRQKFYH3M3XWR"),
            1.23.lumens(),
            "GDKDDBJNREDV4ITL65Z3PNKAGWYJQL7FZJSV4P2UWGLRXI6AWT36UED3"
        ).test()
            .assertFailureAndMessage(XlmSendException::class.java, "Account not found in meta data")
            .assertNotComplete()
        verifyZeroInteractions(horizonProxy)
    }

    @Test
    fun `when the address is not valid - throw`() {
        val horizonProxy = mock<HorizonProxy>()
        XlmDataManager(
            horizonProxy,
            mock(),
            givenNoExpectedSecretAccess()
        ).sendFunds(
            AccountReference.Xlm("", "GB5INYM5XFJHAIQYXUQMGMQEM5KWBM4OYVLTWQI5JSQBRQKFYH3M3XWR"),
            1.23.lumens(),
            "GDKDDBJNREDV4ITL65Z3PNKAGWYJQL7FZJSV4P2UWGLRXI6AWT36UED4"
        ).test()
            .assertFailureAndMessage(InvalidAccountIdException::class.java, "Invalid Account Id, Checksum invalid")
            .assertNotComplete()
        verifyZeroInteractions(horizonProxy)
    }

    @Test
    fun `when the from address reference is not an Xlm one - throw`() {
        val horizonProxy = mock<HorizonProxy>()
        XlmDataManager(
            horizonProxy,
            mock(),
            givenNoExpectedSecretAccess()
        ).sendFunds(
            AccountReference.Ethereum("", "0xAddress"),
            1.23.lumens(),
            "GDKDDBJNREDV4ITL65Z3PNKAGWYJQL7FZJSV4P2UWGLRXI6AWT36UED3"
        ).test()
            .assertFailureAndMessage(XlmSendException::class.java, "Source account reference is not an Xlm reference")
            .assertNotComplete()
        verifyZeroInteractions(horizonProxy)
    }

    private fun HorizonProxy.verifyJustTheOneSendAttempt() {
        verify(this).sendTransaction(any(), any(), any())
        verifyNoMoreInteractions(this)
    }
}

private fun <T> Single<T>.testSingle() = test().values().single()

private fun givenBalances(
    vararg balances: Pair<String, CryptoValue>
): HorizonProxy {
    val horizonProxy: HorizonProxy = mock()
    balances.forEach { pair ->
        whenever(horizonProxy.getBalance(pair.first)) `it returns` pair.second
    }
    return horizonProxy
}

private fun givenBalancesAndMinimums(
    vararg balances: Pair<String, BalanceAndMin>
): HorizonProxy {
    val horizonProxy: HorizonProxy = mock()
    balances.forEach { pair ->
        whenever(horizonProxy.getBalanceAndMin(pair.first)) `it returns` pair.second
    }
    return horizonProxy
}

private fun givenTransactions(
    vararg transactions: Pair<String, List<OperationResponse>>
): HorizonProxy {
    val horizonProxy: HorizonProxy = mock()
    transactions
        .forEach { pair ->
            whenever(horizonProxy.getTransactionList(pair.first)) `it returns` pair.second
        }
    return horizonProxy
}

private fun givenTransaction(
    vararg transactions: Pair<String, TransactionResponse>
): HorizonProxy {
    val horizonProxy: HorizonProxy = mock()
    transactions
        .forEach { pair ->
            whenever(horizonProxy.getTransaction(pair.first)) `it returns` pair.second
        }
    return horizonProxy
}

private fun givenMetaDataMaybe(metaData: XlmMetaData): XlmMetaDataInitializer =
    mock {
        on { initWalletMaybe } `it returns` Maybe.just(
            metaData
        ).subscribeOn(Schedulers.io())
    }

private fun givenMetaDataPrompt(metaData: XlmMetaData): XlmMetaDataInitializer =
    mock {
        on { initWalletMaybePrompt } `it returns` Maybe.just(
            metaData
        ).subscribeOn(Schedulers.io())
    }

private fun givenNoMetaData(): XlmMetaDataInitializer =
    mock {
        on { initWalletMaybe } `it returns` Maybe.empty<XlmMetaData>()
            .subscribeOn(Schedulers.io())
        on { initWalletMaybePrompt } `it returns` Maybe.empty<XlmMetaData>()
            .subscribeOn(Schedulers.io())
    }

private fun verifyNoInteractionsBeforeSubscribe(function: XlmDataManager.() -> Unit) {
    val horizonProxy = mock<HorizonProxy>()
    val metaDataInitializer = mock<XlmMetaDataInitializer>()
    val xlmDataManager = XlmDataManager(
        horizonProxy,
        metaDataInitializer,
        givenNoExpectedSecretAccess()
    )
    function(xlmDataManager)
    verifyZeroInteractions(horizonProxy)
    verifyZeroInteractions(metaDataInitializer)
}

private fun givenNoExpectedSecretAccess(): XlmSecretAccess =
    mock {
        on { getPrivate(any()) } `it throws` RuntimeException("Not expected")
    }

private fun givenPrivateForPublic(vararg pairs: Pair<String, String>): XlmSecretAccess {
    val mock: XlmSecretAccess = mock()
    for (pair in pairs) {
        whenever(mock.getPrivate(HorizonKeyPair.Public(pair.first))).thenReturn(
            Maybe.just(
                HorizonKeyPair.Private(
                    pair.first,
                    pair.second.toCharArray()
                )
            )
        )
    }
    return mock
}
