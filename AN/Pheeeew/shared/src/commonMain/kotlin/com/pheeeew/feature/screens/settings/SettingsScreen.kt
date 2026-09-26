package com.pheeeew.feature.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.settings.components.ContactCard
import com.pheeeew.feature.screens.settings.components.SettingsActionRow
import com.pheeeew.feature.screens.settings.components.SettingsCard
import com.pheeeew.feature.screens.settings.components.SettingsColors
import com.pheeeew.feature.screens.settings.components.SettingsDivider
import com.pheeeew.feature.screens.settings.components.SettingsHeader
import com.pheeeew.feature.screens.settings.components.SettingsIcon
import com.pheeeew.feature.screens.settings.components.SettingsSectionTitle
import com.pheeeew.feature.screens.settings.components.SETTINGS_CONTACT_EMAIL
import com.pheeeew.legacy.core.designsystem.theme.AppTheme
import com.pheeeew.legacy.core.permission.LocationPermissionSettingsLauncher
import com.pheeeew.legacy.core.navigation.PredictiveBackContent
import com.pheeeew.legacy.feature.setting.legal.LegalDocument
import com.pheeeew.legacy.feature.setting.legal.LegalDocumentRoute
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    appVersion: String,
    onBackClick: () -> Unit,
    onPermissionClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onOpenSourceLicenseClick: () -> Unit,
    onContactClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SettingsHeader(onBackClick = onBackClick)
        Spacer(Modifier.height(25.dp))
        SettingsSectionTitle("앱 설정")
        SettingsCard {
            SettingsActionRow(
                title = "접근 권한 설정",
                icon = SettingsIcon.Tune,
                highlighted = true,
                onClick = onPermissionClick,
            )
        }

        Spacer(Modifier.height(37.dp))
        SettingsSectionTitle("이용 안내")
        SettingsCard {
            SettingsActionRow("개인정보 처리방침", SettingsIcon.Shield, onClick = onPrivacyPolicyClick)
            SettingsDivider()
            SettingsActionRow("오픈소스 라이선스", SettingsIcon.Document, onClick = onOpenSourceLicenseClick)
            SettingsDivider()
            SettingsActionRow("앱 버전", SettingsIcon.Info, trailingText = appVersion)
        }

        Spacer(Modifier.height(32.dp))
        ContactCard(onClick = onContactClick)
        Spacer(Modifier.height(72.dp))
        Text(
            text = "pheeeew.",
            color = SettingsColors.Footer,
            style = AppTheme.typography.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 22.dp),
        )
    }
}

@Composable
fun SettingsScreen(
    appVersion: String,
    onBackClick: () -> Unit,
    permissionSettingsLauncher: LocationPermissionSettingsLauncher,
    modifier: Modifier = Modifier,
) {
    var selectedLegalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    AppTheme {
        Box(modifier = modifier.fillMaxSize()) {
            PredictiveBackContent(
                onBack = onBackClick,
                content = {
                    SettingsScreen(
                        appVersion = appVersion,
                        onBackClick = onBackClick,
                        onPermissionClick = {
                            coroutineScope.launch {
                                permissionSettingsLauncher.openAppSettings()
                            }
                        },
                        onPrivacyPolicyClick = {
                            selectedLegalDocument = LegalDocument.PrivacyPolicy
                        },
                        onOpenSourceLicenseClick = {
                            selectedLegalDocument = LegalDocument.OpenSourceLicenses
                        },
                        onContactClick = {
                            runCatching { uriHandler.openUri("mailto:$SETTINGS_CONTACT_EMAIL") }
                        },
                    )
                },
            )

            selectedLegalDocument?.let { document ->
                PredictiveBackContent(
                    onBack = { selectedLegalDocument = null },
                    content = {
                        LegalDocumentRoute(
                            document = document,
                            onBack = { selectedLegalDocument = null },
                        )
                    },
                )
            }
        }
    }
}

@Preview(
    showSystemUi = true,
    device = "spec:width=390dp,height=844dp",
)
@Composable
private fun SettingsScreenPreview() {
    AppTheme {
        SettingsScreen(
            appVersion = "1.1.1",
            onBackClick = {},
            onPermissionClick = {},
            onPrivacyPolicyClick = {},
            onOpenSourceLicenseClick = {},
            onContactClick = {},
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun SettingsScreenStatefulPreview() {
    SettingsScreen(
        appVersion = "1.1.1",
        onBackClick = {},
        permissionSettingsLauncher = PreviewPermissionSettingsLauncher,
    )
}

private object PreviewPermissionSettingsLauncher : LocationPermissionSettingsLauncher {
    override suspend fun openAppSettings(): Boolean = true

    override suspend fun openLocationSettings(): Boolean = true
}
