package com.pheeeew.feature.screens.group.create.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.group.create.GroupCreateRecoveryState
import com.pheeeew.feature.screens.group.model.GroupId
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_create_failure_close
import pheeeew.shared.generated.resources.group_create_recovery_body
import pheeeew.shared.generated.resources.group_create_recovery_candidate
import pheeeew.shared.generated.resources.group_create_recovery_check
import pheeeew.shared.generated.resources.group_create_recovery_checking
import pheeeew.shared.generated.resources.group_create_recovery_none
import pheeeew.shared.generated.resources.group_create_recovery_retry
import pheeeew.shared.generated.resources.group_create_recovery_title
import pheeeew.shared.generated.resources.group_create_recovery_unavailable

@Composable
internal fun CreateOutcomeUnknownDialog(
    recovery: GroupCreateRecoveryState,
    onDismiss: () -> Unit,
    onCheckGroups: () -> Unit,
    onSelectCandidate: (GroupId) -> Unit,
    onRetryCreate: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .heightIn(min = 294.dp, max = 620.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(BorderStroke(1.5.dp, AppColors.GroupInk), RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.group_create_recovery_title),
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.GroupInk,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.group_create_recovery_body),
                    color = AppColors.RankingSecondaryContent,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                when (recovery) {
                    GroupCreateRecoveryState.Idle -> {
                        RecoveryButton(
                            text = stringResource(Res.string.group_create_recovery_check),
                            onClick = onCheckGroups,
                        )
                    }

                    GroupCreateRecoveryState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(color = AppColors.GroupInk, strokeWidth = 2.dp)
                            Text(
                                text = stringResource(Res.string.group_create_recovery_checking),
                                color = AppColors.RankingSecondaryContent,
                                fontSize = 14.sp,
                            )
                        }
                    }

                    GroupCreateRecoveryState.Unavailable -> {
                        Text(
                            text = stringResource(Res.string.group_create_recovery_unavailable),
                            color = AppColors.RankingSecondaryContent,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        RecoveryButton(
                            text = stringResource(Res.string.group_create_recovery_check),
                            onClick = onCheckGroups,
                        )
                    }

                    is GroupCreateRecoveryState.Loaded -> {
                        if (recovery.candidates.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.group_create_recovery_none),
                                color = AppColors.RankingSecondaryContent,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(
                                            max = 220.dp,
                                        ).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                recovery.candidates.forEach { candidate ->
                                    OutlinedButton(
                                        onClick = { onSelectCandidate(candidate.groupId) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = "${candidate.name} · ${stringResource(
                                                Res.string.group_create_recovery_candidate,
                                            )}",
                                            color = AppColors.GroupInk,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        RecoveryButton(
                            text = stringResource(Res.string.group_create_recovery_retry),
                            onClick = onRetryCreate,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(Res.string.group_create_failure_close),
                        color = AppColors.RankingSecondaryContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecoveryButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = CircleShape,
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
