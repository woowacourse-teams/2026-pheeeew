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
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.component.Snackbar
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.core.navigation.PredictiveBackContent
import com.pheeeew.core.permission.AppSettingsLauncher
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.ProductScreen
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.screens.settings.components.ContactCard
import com.pheeeew.feature.screens.settings.components.SETTINGS_CONTACT_EMAIL
import com.pheeeew.feature.screens.settings.components.SettingsActionRow
import com.pheeeew.feature.screens.settings.components.SettingsCard
import com.pheeeew.feature.screens.settings.components.SettingsDivider
import com.pheeeew.feature.screens.settings.components.SettingsHeader
import com.pheeeew.feature.screens.settings.components.SettingsIcon
import com.pheeeew.feature.screens.settings.components.SettingsSectionTitle
import com.pheeeew.feature.screens.settings.legal.LegalDocument
import com.pheeeew.feature.screens.settings.legal.LegalDocumentRoute
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.settings_app_section
import pheeeew.shared.generated.resources.settings_app_version
import pheeeew.shared.generated.resources.settings_help_section
import pheeeew.shared.generated.resources.settings_legal_open_source
import pheeeew.shared.generated.resources.settings_legal_privacy
import pheeeew.shared.generated.resources.settings_nickname_change
import pheeeew.shared.generated.resources.settings_permissions
import pheeeew.shared.generated.resources.settings_profile_section

@Composable
fun SettingsScreen(
    appVersion: String,
    onBackClick: () -> Unit,
    onPermissionClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onOpenSourceLicenseClick: () -> Unit,
    onContactClick: () -> Unit,
    onVersionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isNicknameDialogVisible by remember { mutableStateOf(false) }
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
        SettingsSectionTitle(stringResource(Res.string.settings_app_section))
        SettingsCard {
            SettingsActionRow(
                title = stringResource(Res.string.settings_permissions),
                icon = SettingsIcon.Tune,
                highlighted = true,
                onClick = onPermissionClick,
            )
        }

        Spacer(Modifier.height(22.dp))
        SettingsSectionTitle(stringResource(Res.string.settings_profile_section))
        SettingsCard {
            SettingsActionRow(
                title = stringResource(Res.string.settings_nickname_change),
                icon = SettingsIcon.Person,
                onClick = { isNicknameDialogVisible = true },
            )
        }

        Spacer(Modifier.height(37.dp))
        SettingsSectionTitle(stringResource(Res.string.settings_help_section))
        SettingsCard {
            SettingsActionRow(
                stringResource(Res.string.settings_legal_privacy),
                SettingsIcon.Shield,
                onClick = onPrivacyPolicyClick,
            )
            SettingsDivider()
            SettingsActionRow(
                stringResource(Res.string.settings_legal_open_source),
                SettingsIcon.Document,
                onClick = onOpenSourceLicenseClick,
            )
            SettingsDivider()
            SettingsActionRow(
                stringResource(Res.string.settings_app_version),
                SettingsIcon.Info,
                trailingText = appVersion,
                onClick = onVersionClick,
            )
        }

        Spacer(Modifier.height(32.dp))
        ContactCard(onClick = onContactClick)
    }

    if (isNicknameDialogVisible) {
        NicknameDialog(
            onDismiss = { isNicknameDialogVisible = false },
            onConfirm = { isNicknameDialogVisible = false },
        )
    }
}

@Composable
fun SettingsScreen(
    appVersion: String,
    onBackClick: () -> Unit,
    permissionSettingsLauncher: AppSettingsLauncher,
    monitoring: Monitoring = NoOpMonitoring,
    modifier: Modifier = Modifier,
) {
    var versionTaps by remember { mutableStateOf(0) }
    var analyticsIdentity by remember { mutableStateOf<String?>(null) }
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
                        onVersionClick = {
                            versionTaps++
                            if (versionTaps == 7) {
                                versionTaps = 0
                                coroutineScope.launch {
                                    analyticsIdentity = monitoring.analyticsIdentity() ?: "분석 기능이 초기화되지 않았어요."
                                }
                            }
                        },
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
                                    snackbarHostState.showSnackbar(
                                        "설정 화면을 열지 못했어요.",
                                        duration = SnackbarDuration.Indefinite,
                                    )
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
                                    snackbarHostState.showSnackbar(
                                        "메일 앱을 열 수 없어요.",
                                        duration = SnackbarDuration.Indefinite,
                                    )
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

            analyticsIdentity?.let { identity ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { analyticsIdentity = null },
                    title = { Text("분석용 익명 ID") },
                    text = {
                        androidx.compose.foundation.text.selection
                            .SelectionContainer { Text(identity) }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { analyticsIdentity = null }) { Text("닫기") }
                    },
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                snackbar = { data ->
                    Snackbar(
                        message = data.visuals.message,
                        onDismiss = data::dismiss,
                        isError = true,
                        maxLines = 3,
                        presentationKey = data,
                    )
                },
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
