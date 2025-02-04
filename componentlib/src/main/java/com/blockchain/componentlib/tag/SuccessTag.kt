package com.blockchain.componentlib.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blockchain.componentlib.theme.AppSurface
import com.blockchain.componentlib.theme.AppTheme
import com.blockchain.componentlib.theme.Dark900
import com.blockchain.componentlib.theme.Green100
import com.blockchain.componentlib.theme.Green400
import com.blockchain.componentlib.theme.Green600

@Composable
fun SuccessTag(text: String) {

    val defaultBackgroundColor = if (!isSystemInDarkTheme()) {
        Green100
    } else {
        Green400
    }

    val defaultTextColor = if (!isSystemInDarkTheme()) {
        Green600
    } else {
        Dark900
    }

    Text(
        text = text,
        style = AppTheme.typography.caption2,
        color = defaultTextColor,
        modifier = Modifier
            .clip(AppTheme.shapes.small)
            .background(defaultBackgroundColor)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

@Preview
@Composable
fun SuccessTag_Basic() {
    AppTheme {
        AppSurface {
            SuccessTag(text = "Default")
        }
    }
}

@Preview
@Composable
fun SuccessTag_Dark() {
    AppTheme(darkTheme = true) {
        AppSurface {
            SuccessTag(text = "Default")
        }
    }
}
