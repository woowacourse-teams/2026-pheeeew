package com.pheeeew.feature.map.sighlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_menu

@Composable
fun SighListButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    interactionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier.toggleable(
                value = checked,
                enabled = interactionEnabled,
                role = Role.Button,
                onValueChange = onCheckedChange,
            ),
        shape = RoundedCornerShape(24.dp),
        color = if (checked) AppColors.Navy700 else AppColors.Blue100,
        contentColor = if (checked) AppColors.Cream100 else AppColors.Navy800,
        border =
            BorderStroke(
                width = 1.dp,
                color = if (checked) AppColors.Blue100 else AppColors.Cream100.copy(alpha = 0.45f),
            ),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_menu),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "한숨목록",
                style = AppTheme.typography.menuItem.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Preview
@Composable
private fun SighListButtonPreview() {
    AppTheme {
        Box(modifier = Modifier.padding(24.dp)) {
            SighListButton(
                checked = false,
                onCheckedChange = {},
                interactionEnabled = true,
            )
        }
    }
}
