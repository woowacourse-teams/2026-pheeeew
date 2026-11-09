package com.pheeeew.hapticcomparison

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.pheeeew.core.haptics.USE_JINDONG_HAPTICS
import com.pheeeew.feature.emotion.component.EmotionPad
import com.pheeeew.feature.emotion.component.dispatchEmotionTapFeedback
import com.pheeeew.feature.emotion.component.rememberEmotionHapticFeedback
import com.pheeeew.feature.emotion.model.EmotionCountUiModel
import com.pheeeew.feature.emotion.model.EmotionKind
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/** Available only with -PhapticComparisonBuild=true, never in ordinary product compilation. */
object HapticComparisonProbe {
    var scenario = "manual"
    val rows = CopyOnWriteArrayList<JSONObject>()

    fun record(row: JSONObject) {
        rows.add(row.put("scenario", scenario).put("backend", if (USE_JINDONG_HAPTICS) "jindong" else "native"))
    }

    fun snapshot(): JSONArray = JSONArray(rows.toList())
}

@Composable
fun HapticComparisonContent() {
    var shown by remember { mutableStateOf(true) }
    var projectPad by remember { mutableStateOf(false) }
    Column(Modifier.padding(24.dp).semantics { testTagsAsResourceId = true }) {
        Text(if (USE_JINDONG_HAPTICS) "Jindong 1.1.0" else "기존 Android 시스템 햅틱")
        Text("실제 프로젝트 햅틱 호출 · 별도 패키지 비교")
        Button(onClick = { shown = !shown }, modifier = Modifier.semantics { contentDescription = "toggle-screen" }) {
            Text(if (shown) "화면 나가기" else "화면 돌아오기")
        }
        Button(
            onClick = { projectPad = !projectPad },
            modifier =
                Modifier.semantics {
                    contentDescription =
                        "toggle-project-pad"
                },
        ) {
            Text(if (projectPad) "계측 버튼으로 전환" else "실제 감정 패드로 전환")
        }
        if (shown) {
            if (projectPad) ProjectEmotionPad() else ComparisonPad()
        }
    }
}

@Composable
private fun ComparisonPad() {
    val feedback = rememberEmotionHapticFeedback()
    val view = LocalView.current
    var count by remember { mutableIntStateOf(0) }
    var reject by remember { mutableStateOf(false) }
    var visualOnly by remember { mutableStateOf(false) }
    var viewEnabled by remember { mutableStateOf(true) }

    fun tap() {
        val start = SystemClock.elapsedRealtimeNanos()
        var invoked = false
        var callNanos = 0L
        var visual = false
        dispatchEmotionTapFeedback(
            accepted = !reject,
            allowVisualFeedbackWhenRejected = visualOnly,
            hapticFeedback = {
                invoked = true
                val callStart = SystemClock.elapsedRealtimeNanos()
                feedback()
                callNanos = SystemClock.elapsedRealtimeNanos() - callStart
            },
            visualFeedback = { visual = true },
        )
        count++
        HapticComparisonProbe.record(
            JSONObject()
                .put("startElapsedNanos", start)
                .put("wallTimeMillis", System.currentTimeMillis())
                .put("callbackNanos", callNanos)
                .put("hapticCallbackInvoked", invoked)
                .put("visualFeedbackInvoked", visual)
                .put("accepted", !reject)
                .put("visualOnly", visualOnly)
                .put("viewEnabled", view.isHapticFeedbackEnabled)
                .put(
                    "systemHapticsEnabled",
                    Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1),
                ),
        )
    }
    Text("입력 $count 회")
    Button(
        onClick = ::tap,
        modifier =
            Modifier.fillMaxWidth().height(100.dp).semantics {
                contentDescription =
                    "tap-haptic"
            },
    ) {
        Text("감정 누르기")
    }
    Button(onClick = { reject = !reject }, modifier = Modifier.semantics { contentDescription = "toggle-reject" }) {
        Text("입력 거절: $reject")
    }
    Button(
        onClick = { visualOnly = !visualOnly },
        modifier =
            Modifier.semantics {
                contentDescription =
                    "toggle-visual-only"
            },
    ) {
        Text("시각 효과 전용: $visualOnly")
    }
    Button(onClick = {
        viewEnabled = !viewEnabled
        view.isHapticFeedbackEnabled = viewEnabled
    }, modifier = Modifier.semantics { contentDescription = "toggle-view-haptics" }) {
        Text("화면 햅틱 허용: $viewEnabled")
    }
}

@Composable
private fun ProjectEmotionPad() {
    val view = LocalView.current
    EmotionPad(
        counts = EmotionKind.entries.map { EmotionCountUiModel(it, 0) },
        enabled = true,
        onEmotionTap = {
            HapticComparisonProbe.record(
                JSONObject()
                    .put("measurementKind", "project-pad-admission")
                    .put("startElapsedNanos", SystemClock.elapsedRealtimeNanos())
                    .put("wallTimeMillis", System.currentTimeMillis())
                    .put("callbackNanos", 0)
                    .put("hapticCallbackInvoked", JSONObject.NULL)
                    .put("visualFeedbackInvoked", JSONObject.NULL)
                    .put("accepted", true)
                    .put("viewEnabled", view.isHapticFeedbackEnabled)
                    .put(
                        "systemHapticsEnabled",
                        Settings.System.getInt(
                            view.context.contentResolver,
                            Settings.System.HAPTIC_FEEDBACK_ENABLED,
                            1,
                        ),
                    ),
            )
            true
        },
        modifier = Modifier.height(380.dp),
    )
}
