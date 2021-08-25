package com.blockchain.coreui.price

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.blockchain.coreui.R
import com.blockchain.coreui.databinding.ViewPriceRowBinding
import com.blockchain.coreui.utils.toPx
import com.bumptech.glide.Glide
import java.text.NumberFormat

class PriceView: ConstraintLayout {
    private val binding = ViewPriceRowBinding.inflate(LayoutInflater.from(context), this)

    data class Price(val icon: String, val name: String, val ticker: String, val price: String, val gain: Double)

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {
        minHeight = 90.toPx()
    }

    var price: Price? = null
        set(value) {
            if (value != null) {
                Glide.with(context)
                    .load(value.icon)
                    .into(binding.icon)
                binding.name.text = value.name
                binding.ticker.text = value.ticker
                binding.price.text = value.price
                binding.gain.text = getGainSpannable(value.gain)
            }

            field = value
        }

    private fun getGainSpannable(gain: Double): SpannableString {
        val percentFormatter = NumberFormat.getPercentInstance()
        percentFormatter.minimumFractionDigits = 2
        val percent = percentFormatter.format(gain)

        val spannableString = if (gain < 0) {
            SpannableString( "↓ $percent")
        } else {
            SpannableString( "↑ $percent")
        }

        val foregroundSpan = if (gain < 0) {
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.paletteBaseError))
        } else {
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.paletteBaseSuccess))
        }

        spannableString.setSpan(foregroundSpan, 0, spannableString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannableString
    }
}