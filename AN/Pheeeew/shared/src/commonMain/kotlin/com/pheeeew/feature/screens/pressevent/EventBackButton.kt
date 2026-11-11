package com.pheeeew.feature.screens.pressevent

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.appInk
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_arrow_back

@Composable
internal fun EventBackButton(
    onBack: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onBack, modifier = modifier) {
        Icon(
            painterResource(Res.drawable.ic_arrow_back),
            contentDescription = "뒤로가기",
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
    }
}

@Preview(name = "이벤트 뒤로가기 버튼", widthDp = 48, heightDp = 48)
@Composable
private fun EventBackButtonPreview() {
    AppTheme { EventBackButton(onBack = {}, tint = MaterialTheme.colorScheme.appInk) }
}
