package com.pheeeew.developertools

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DeveloperToolsSettingsEntry(onOpen: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("개발자 도구") }
}
