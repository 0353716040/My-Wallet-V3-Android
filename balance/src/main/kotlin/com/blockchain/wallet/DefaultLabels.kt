package com.blockchain.wallet

import info.blockchain.balance.CryptoCurrency

interface DefaultLabels {

    fun getDefaultNonCustodialWalletLabel(cryptoCurrency: CryptoCurrency): String
    fun getDefaultCustodialWalletLabel(cryptoCurrency: CryptoCurrency): String
}
