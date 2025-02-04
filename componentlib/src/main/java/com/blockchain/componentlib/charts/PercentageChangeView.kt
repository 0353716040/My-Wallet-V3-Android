package com.blockchain.componentlib.charts

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import com.blockchain.componentlib.theme.AppSurface
import com.blockchain.componentlib.theme.AppTheme

class PercentageChangeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    var priceChange by mutableStateOf("")
    var percentChange by mutableStateOf(0.0)
    var interval by mutableStateOf("")
    var state by mutableStateOf(PercentageChangeState.Neutral)

    @Composable
    override fun Content() {
        AppTheme {
            AppSurface {
                PercentageChange(
                    priceChange = priceChange,
                    percentChange = percentChange,
                    state = state,
                    interval = interval
                )
            }
        }
    }
}
