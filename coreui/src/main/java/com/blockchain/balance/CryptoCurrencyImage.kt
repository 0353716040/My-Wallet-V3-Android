package com.blockchain.balance

import android.support.annotation.DrawableRes
import android.support.v7.content.res.AppCompatResources
import android.widget.ImageView
import info.blockchain.balance.CryptoCurrency
import piuk.blockchain.androidcoreui.R

@DrawableRes
fun CryptoCurrency.layerListDrawableRes() =
    when (this) {
        CryptoCurrency.BTC -> R.drawable.layer_list_bitcoin
        CryptoCurrency.ETHER -> R.drawable.layer_list_eth
        CryptoCurrency.BCH -> R.drawable.layer_list_bitcoin_cash
        CryptoCurrency.XLM -> R.drawable.layer_list_lumen
        CryptoCurrency.PAX -> TODO("PAX is not yet supported - AND-2003")
    }

@DrawableRes
fun CryptoCurrency.drawableRes() =
    when (this) {
        CryptoCurrency.BTC -> R.drawable.vector_bitcoin
        CryptoCurrency.ETHER -> R.drawable.vector_eth
        CryptoCurrency.BCH -> R.drawable.vector_bitcoin_cash
        CryptoCurrency.XLM -> R.drawable.vector_xlm_icon
        CryptoCurrency.PAX -> R.drawable.vector_pax
    }

@DrawableRes
fun CryptoCurrency.drawableResFilled() =
    when (this) {
        CryptoCurrency.BTC -> R.drawable.vector_bitcoin_filled
        CryptoCurrency.ETHER -> R.drawable.vector_eth_filled
        CryptoCurrency.BCH -> R.drawable.vector_bitcoin_cash_filled
        CryptoCurrency.XLM -> R.drawable.vector_xlm_icon
        CryptoCurrency.PAX -> R.drawable.vector_pax
    }

@DrawableRes
fun CryptoCurrency.coinIconWhite() =
    when (this) {
        CryptoCurrency.BTC -> R.drawable.vector_bitcoin_white
        CryptoCurrency.ETHER -> R.drawable.vector_eth_white
        CryptoCurrency.BCH -> R.drawable.vector_bitcoin_cash_white
        CryptoCurrency.XLM -> R.drawable.vector_xlm_white
        CryptoCurrency.PAX -> R.drawable.vector_pax
    }

fun ImageView.setImageDrawable(@DrawableRes res: Int) {
    setImageDrawable(AppCompatResources.getDrawable(context, res))
}
