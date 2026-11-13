package com.pheeeew.feature.screens.collection.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.gaeguFontFamily
import com.pheeeew.feature.screens.collection.CollectionPreviewTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.collection_unissued_card_message
import pheeeew.shared.generated.resources.ic_bubble_icon_high

@Composable
internal fun CollectionUnissuedCard(modifier: Modifier = Modifier) {
    CollectionCardFrame(cornerIconTint = null, modifier = modifier) { scale ->
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(Res.drawable.ic_bubble_icon_high),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).offset(y = (-52).dp * scale).size(120.dp * scale),
            )
            Text(
                text = stringResource(Res.string.collection_unissued_card_message),
                modifier = Modifier.align(Alignment.Center).offset(y = 82.dp * scale),
                color = AppColors.TextPrimary,
                fontFamily = gaeguFontFamily(),
                fontWeight = FontWeight.Bold,
                fontSize = (18 * scale).sp,
                lineHeight = (23 * scale).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(widthDp = 300, heightDp = 400, showBackground = true)
@Composable
private fun CollectionUnissuedCardPreview() {
    CollectionPreviewTheme {
        CollectionUnissuedCard(Modifier.size(300.dp, 400.dp))
    }
}
