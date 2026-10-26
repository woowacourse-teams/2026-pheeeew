package com.pheeeew.feature.screens.collection

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

private class PendingCollectionCardSave(
    var bytes: ByteArray?,
    val continuation: CancellableContinuation<CollectionImageActionOutcome>,
)

@Composable
internal actual fun rememberCollectionCardImageActions(): CollectionCardImageActions {
    val context = LocalContext.current
    val pendingSave = remember { mutableStateOf<PendingCollectionCardSave?>(null) }
    val documentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            val pending = pendingSave.value
            pendingSave.value = null
            val bytes = pending?.bytes
            pending?.bytes = null
            if (pending != null && pending.continuation.isActive) {
                val outcome =
                    when {
                        uri == null -> CollectionImageActionOutcome.CANCELLED
                        bytes == null -> CollectionImageActionOutcome.FAILED
                        else -> {
                            val succeeded =
                                runCatching {
                                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                                        ?: error("저장 위치를 열 수 없습니다")
                                }.isSuccess
                            if (succeeded) CollectionImageActionOutcome.SUCCEEDED else CollectionImageActionOutcome.FAILED
                        }
                    }
                pending.continuation.resume(outcome)
            }
        }

    return remember(context, documentLauncher) {
        CollectionCardImageActions(
            save = { bytes ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val result = runCatching { saveToMediaStore(context, bytes) }.getOrDefault(false)
                    if (result) CollectionImageActionOutcome.SUCCEEDED else CollectionImageActionOutcome.FAILED
                } else {
                    suspendCancellableCoroutine { continuation ->
                        if (pendingSave.value != null) {
                            continuation.resume(CollectionImageActionOutcome.FAILED)
                        } else {
                            val pending = PendingCollectionCardSave(bytes, continuation)
                            pendingSave.value = pending
                            continuation.invokeOnCancellation { pending.bytes = null }
                            try {
                                documentLauncher.launch("pheeeew-card-${System.currentTimeMillis()}.png")
                            } catch (_: Exception) {
                                pendingSave.value = null
                                pending.bytes = null
                                continuation.resume(CollectionImageActionOutcome.FAILED)
                            }
                        }
                    }
                }
            },
            share = { bytes, url ->
                val result = runCatching { sharePng(context, bytes, url) }.getOrDefault(false)
                if (result) CollectionImageActionOutcome.SUCCEEDED else CollectionImageActionOutcome.FAILED
            },
        )
    }
}

private fun saveToMediaStore(
    context: android.content.Context,
    bytes: ByteArray,
): Boolean {
    val resolver = context.contentResolver
    val values =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "pheeeew-card-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Pheeeew")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("저장 위치를 열 수 없습니다")
        val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        resolver.update(uri, published, null, null) > 0
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}

private fun sharePng(
    context: android.content.Context,
    bytes: ByteArray,
    url: String,
): Boolean {
    val directory = File(context.cacheDir, "collection-cards").apply { mkdirs() }
    directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach(
        File::delete,
    )
    val file = File(directory, "pheeeew-card-${System.currentTimeMillis()}.png")
    file.writeBytes(bytes)
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.collection.fileprovider", file)
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, url)
            clipData = android.content.ClipData.newUri(context.contentResolver, "Pheeeew card", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(sendIntent, null))
    return true
}
