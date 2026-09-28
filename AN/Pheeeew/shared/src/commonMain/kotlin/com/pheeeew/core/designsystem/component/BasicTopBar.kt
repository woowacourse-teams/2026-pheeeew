package com.pheeeew.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_arrow_back

@Composable
fun BasicTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backContentDescription: String = "뒤로가기",
    titleColor: Color = AppColors.GroupInk,
    titleFontFamily: FontFamily? = null,
    height: Dp = 56.dp,
) {
    Box(modifier = modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        onBack?.let { onBackClick ->
            IconButton(
                onClick = onBackClick,
                enabled = enabled,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(48.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_back),
                    contentDescription = backContentDescription,
                    tint = titleColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = title,
            color = titleColor,
            fontFamily = titleFontFamily ?: notoSansKrFontFamily(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 64.dp).semantics { heading() },
        )
    }
}
