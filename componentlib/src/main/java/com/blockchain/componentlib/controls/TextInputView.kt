package com.blockchain.componentlib.controls

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import com.blockchain.componentlib.image.ImageResource
import com.blockchain.componentlib.theme.AppSurface
import com.blockchain.componentlib.theme.AppTheme

class TextInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    var value by mutableStateOf("")
    var onValueChange by mutableStateOf({ _: String -> })
    var isInputEnabled by mutableStateOf(true)
    var isError by mutableStateOf(false)
    var assistiveText by mutableStateOf("")
    var errorText by mutableStateOf("")
    var labelText by mutableStateOf("")
    var placeholderText by mutableStateOf("")
    var trailingIconResource: ImageResource by mutableStateOf(ImageResource.None)
    var leadingIconResource: ImageResource by mutableStateOf(ImageResource.None)

    @Composable
    override fun Content() {
        AppTheme {
            AppSurface {
                TextInput(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = isInputEnabled,
                    assistiveText = assistiveText,
                    placeholder = placeholderText,
                    label = labelText,
                    errorMessage = errorText,
                    isError = isError,
                    trailingIcon = trailingIconResource,
                    leadingIcon = leadingIconResource
                )
            }
        }
    }
}
