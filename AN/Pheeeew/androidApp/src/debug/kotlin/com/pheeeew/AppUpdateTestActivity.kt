package com.pheeeew

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Debug-only entry reachable through adb; settings apply to the same installed test app. */
class AppUpdateTestActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.APPLICATION_ID.endsWith(".updatetest")) {
            finish()
            return
        }
        val prefs = getSharedPreferences("update_test", Context.MODE_PRIVATE)
        intent
            .getStringExtra(
                "policy",
            )?.takeIf { it in listOf("server", "current", "suggested", "required", "offline") }
            ?.let { prefs.edit().putString("policy", it).apply() }
        intent
            .getStringExtra("play")
            ?.takeIf { it in listOf("real", "cancel", "fail", "download", "unavailable") }
            ?.let { prefs.edit().putString("play", it).apply() }
        setContent {
            MaterialTheme {
                var policy by remember { mutableStateOf(prefs.getString("policy", "server")) }
                var play by remember { mutableStateOf(prefs.getString("play", "real")) }
                Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                    Text("업데이트 테스트 설정", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${BuildConfig.APPLICATION_ID}\n${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n정책: $policy / Play: $play",
                    )
                    Text("재현 정책은 운영 서버와 캐시에 저장되지 않습니다.")
                    listOf(
                        "server" to "실제 개발 서버",
                        "current" to "업데이트 없음",
                        "suggested" to "권장 (최소 2.5.0 / 최신 2.6.0)",
                        "required" to "필수 (최소·최신 2.6.0)",
                        "offline" to "정책 조회 실패",
                    ).forEach { (value, label) ->
                        Button(onClick = {
                            policy = value
                            prefs.edit().putString("policy", value).apply()
                        }) { Text(label) }
                    }
                    Text("Play 재현 (실제 설치와 구분)")
                    listOf(
                        "real" to "실제 Play",
                        "cancel" to "Fake 취소",
                        "fail" to "Fake 실패",
                        "download" to "Fake 다운로드 완료",
                        "unavailable" to "Play 업데이트 불가",
                    ).forEach { (value, label) ->
                        Button(onClick = {
                            play = value
                            prefs.edit().putString("play", value).apply()
                        }) { Text(label) }
                    }
                    Button(onClick = {
                        startActivity(
                            Intent(
                                this@AppUpdateTestActivity,
                                MainActivity::class.java,
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        )
                    }) { Text("앱 다시 실행") }
                    Button(onClick = { finish() }) { Text("기존 앱으로 복귀 (정책 재조회)") }
                }
            }
        }
    }
}
