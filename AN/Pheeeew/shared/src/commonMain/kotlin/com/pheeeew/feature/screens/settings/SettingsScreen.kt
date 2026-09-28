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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.core.navigation.PredictiveBackContent
import com.pheeeew.core.permission.AppSettingsLauncher
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.ProductScreen
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.screens.settings.components.ContactCard
import com.pheeeew.feature.screens.settings.components.SETTINGS_CONTACT_EMAIL
import com.pheeeew.feature.screens.settings.components.SettingsActionRow
import com.pheeeew.feature.screens.settings.components.SettingsCard
import com.pheeeew.feature.screens.settings.components.SettingsColors
import com.pheeeew.feature.screens.settings.components.SettingsDivider
import com.pheeeew.feature.screens.settings.components.SettingsHeader
import com.pheeeew.feature.screens.settings.components.SettingsIcon
import com.pheeeew.feature.screens.settings.components.SettingsSectionTitle
import com.pheeeew.feature.screens.settings.legal.LegalDocument
import com.pheeeew.feature.screens.settings.legal.LegalDocumentRoute
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
    }
}

@Composable
fun SettingsScreen(
    appVersion: String,
    onBackClick: () -> Unit,
    permissionSettingsLauncher: AppSettingsLauncher,
    monitoring: com.pheeeew.core.monitoring.Monitoring = com.pheeeew.core.monitoring.NoOpMonitoring,
    modifier: Modifier = Modifier,
) {
    var selectedLegalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    val telemetry = remember(monitoring) { ProductMonitoring(monitoring, "settings") }
    val legalTelemetry = remember(monitoring) { ProductMonitoring(monitoring, "legaldocument") }
    ProductScreen(telemetry, selectedLegalDocument == null)
    ProductScreen(legalTelemetry, selectedLegalDocument != null)
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }

    SettingsTheme {
        Box(modifier = modifier.fillMaxSize()) {
            PredictiveBackContent(
                onBack = onBackClick,
                content = {
                    SettingsScreen(
                        appVersion = appVersion,
                        onBackClick = onBackClick,
                        onPermissionClick = {
                            telemetry.emit("settings_action_selected", labels("action" to "permission"))
                            coroutineScope.launch {
                                if (!telemetry
                                        .operation(
                                            "settings_external_open_finished",
                                            labels("action" to "permission"),
                                        ).observe(
                                            { if (it) "success" else "failed" },
                                        ) { permissionSettingsLauncher.openAppSettings() }
                                ) {
                                    snackbarHostState.showSnackbar("설정 화면을 열지 못했어.")
                                }
                            }
                        },
                        onPrivacyPolicyClick = {
                            telemetry.emit("settings_action_selected", labels("action" to "privacy_policy"))
                            selectedLegalDocument = LegalDocument.PrivacyPolicy
                        },
                        onOpenSourceLicenseClick = {
                            telemetry.emit("settings_action_selected", labels("action" to "licenses"))
                            selectedLegalDocument = LegalDocument.OpenSourceLicenses
                        },
                        onContactClick = {
                            telemetry.emit("settings_action_selected", labels("action" to "contact"))
                            val opened = runCatching { uriHandler.openUri("mailto:$SETTINGS_CONTACT_EMAIL") }.isSuccess
                            telemetry.emit(
                                "settings_external_open_finished",
                                labels(
                                    "action" to "contact",
                                    "outcome" to if (opened) "success" else "failed",
                                ),
                            )
                            if (!opened) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("메일 앱을 열 수 없어.")
                                }
                            }
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

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }
    }
}

@Preview(
    showSystemUi = true,
    device = "spec:width=390dp,height=844dp",
)
@Composable
private fun SettingsScreenPreview() {
    SettingsTheme {
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

@Preview
@Composable
private fun SettingsScreenStatefulPreview() {
    SettingsScreen(
        appVersion = "1.1.1",
        onBackClick = {},
        permissionSettingsLauncher = PreviewPermissionSettingsLauncher,
    )
}

private object PreviewPermissionSettingsLauncher : AppSettingsLauncher {
    override suspend fun openAppSettings(): Boolean = true

    override suspend fun openLocationSettings(): Boolean = true
}
