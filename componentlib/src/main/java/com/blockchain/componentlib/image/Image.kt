package com.blockchain.componentlib.image

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberImagePainter
import coil.request.ImageRequest
import com.blockchain.componentlib.theme.AppTheme

@Composable
fun Image(
    imageResource: ImageResource,
    modifier: Modifier = Modifier,
    coilImageBuilderScope: (ImageRequest.Builder.() -> Unit)? = null,
) {
    val placeholderColor = AppTheme.colors.light.toArgb()

    val defaultBuilderScope: ImageRequest.Builder.() -> Unit = {
        crossfade(true)
        placeholder(ColorDrawable(placeholderColor))
    }

    when (imageResource) {
        is ImageResource.Local ->
            androidx.compose.foundation.Image(
                painter = painterResource(id = imageResource.id),
                contentDescription = imageResource.contentDescription,
                modifier = modifier,
            )
        is ImageResource.Remote ->
            androidx.compose.foundation.Image(
                painter = rememberImagePainter(
                    data = imageResource.url,
                    builder = coilImageBuilderScope ?: defaultBuilderScope
                ),
                contentDescription = imageResource.contentDescription,
                modifier = modifier,
            )
        is ImageResource.LocalWithBackground -> {
            val filterColor = Color(ContextCompat.getColor(LocalContext.current, imageResource.filterColorId))
            val tintColor = Color(ContextCompat.getColor(LocalContext.current, imageResource.tintColorId))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .alpha(imageResource.alpha)
                        .background(
                            color = tintColor,
                            shape = CircleShape
                        )
                        .size(32.dp)
                )
                androidx.compose.foundation.Image(
                    painter = painterResource(id = imageResource.id),
                    contentDescription = imageResource.contentDescription,
                    modifier = modifier,
                    colorFilter = ColorFilter.tint(filterColor)
                )
            }
        }
        ImageResource.None -> return
    }
}
