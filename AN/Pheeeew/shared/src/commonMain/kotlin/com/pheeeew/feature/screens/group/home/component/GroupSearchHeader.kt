// Lucian UX/UI Prompt #027 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#027
package com.pheeeew.feature.screens.group.home.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_explore_clear_query
import pheeeew.shared.generated.resources.group_explore_query_hint
import pheeeew.shared.generated.resources.group_home_title
import pheeeew.shared.generated.resources.group_search_clear
import pheeeew.shared.generated.resources.group_search_close
import pheeeew.shared.generated.resources.group_search_open
import pheeeew.shared.generated.resources.group_search_recent
import pheeeew.shared.generated.resources.group_search_reset
import pheeeew.shared.generated.resources.group_search_summary

@Composable
internal fun GroupSearchHeader(
    isOpen: Boolean,
    query: String,
    recentQueries: List<String>,
    showSummary: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    tabsContent: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        AnimatedContent(
            targetState = isOpen,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            contentAlignment = Alignment.Center,
            transitionSpec = {
                (fadeIn(tween(180)) + expandHorizontally(tween(240), expandFrom = Alignment.End)) togetherWith
                    (fadeOut(tween(100)) + shrinkHorizontally(tween(180), shrinkTowards = Alignment.End))
            },
        ) { open ->
            if (open) {
                ExpandedGroupSearch(query, onQueryChanged, onSubmit, onClose)
            } else {
                Box(Modifier.fillMaxWidth().height(54.dp)) {
                    Text(
                        text = stringResource(Res.string.group_home_title),
                        modifier = Modifier.align(Alignment.Center),
                        color = AppColors.GroupInk,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val label = stringResource(Res.string.group_search_open)
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onOpen)
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) { SearchGlyph() }
                }
            }
        }
        AnimatedVisibility(visible = !isOpen) {
            tabsContent()
        }

        AnimatedVisibility(visible = isOpen && query.isBlank() && recentQueries.isNotEmpty()) {
            Column(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.group_search_recent), color = AppColors.TextSecondary, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentQueries, key = { it }) { keyword ->
                        Box(
                            Modifier
                                .height(40.dp)
                                .clip(CircleShape)
                                .background(AppColors.RankingSurface)
                                .clickable(role = Role.Button, onClick = { onQueryChanged(keyword) })
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(keyword, color = AppColors.GroupInk, fontSize = 13.sp) }
                    }
                }
            }
        }
        AnimatedVisibility(visible = !isOpen && showSummary && query.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.group_search_summary, query.trim()),
                    Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen),
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(Res.string.group_search_reset),
                    Modifier.clickable(role = Role.Button, onClick = { onQueryChanged("") }).padding(12.dp),
                    color = AppColors.GroupInk,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ExpandedGroupSearch(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) AppColors.GroupInk else AppColors.BorderLight)
    val label = stringResource(Res.string.group_explore_query_hint)
    val closeLabel = stringResource(Res.string.group_search_close)
    val clearLabel = stringResource(Res.string.group_explore_clear_query)
    val shape = RoundedCornerShape(13.dp)
    LaunchedEffect(Unit) {
        requester.requestFocus()
        keyboard?.show()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(AppColors.RecordSheetInputSurface)
            .border(1.dp, border, shape)
            .padding(start = 12.dp)
            .onPreviewKeyEvent {
                if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                    onClose()
                    true
                } else {
                    false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchGlyph()
        BasicTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(requester)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
            singleLine = true,
            textStyle = TextStyle(color = AppColors.GroupInk, fontSize = 14.sp),
            cursorBrush = SolidColor(AppColors.GroupInk),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onSubmit()
                }),
            decorationBox = { field ->
                Box {
                    if (query.isEmpty()) Text(label, color = AppColors.TextSecondary, fontSize = 14.sp)
                    field()
                }
            },
        )
        if (query.isNotEmpty()) {
            Text(
                stringResource(Res.string.group_search_clear),
                Modifier
                    .clickable(role = Role.Button, onClick = { onQueryChanged("") })
                    .semantics { contentDescription = clearLabel }
                    .padding(vertical = 12.dp),
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
        Box(
            Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = {
                    keyboard?.hide()
                    onClose()
                })
                .semantics { contentDescription = closeLabel },
            contentAlignment = Alignment.Center,
        ) { Text("×", color = AppColors.GroupInk, fontSize = 22.sp) }
    }
}

@Composable
private fun SearchGlyph() {
    Canvas(Modifier.size(18.dp)) {
        drawCircle(
            AppColors.GroupInk,
            size.minDimension * .32f,
            Offset(size.width * .42f, size.height * .42f),
            style = Stroke(1.6.dp.toPx()),
        )
        drawLine(
            AppColors.GroupInk,
            Offset(size.width * .65f, size.height * .65f),
            Offset(
                size.width * .91f,
                size.height * .91f,
            ),
            1.6.dp.toPx(),
        )
    }
}
