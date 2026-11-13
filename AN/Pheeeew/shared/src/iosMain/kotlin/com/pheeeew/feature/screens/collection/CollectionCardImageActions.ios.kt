@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.pheeeew.feature.screens.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import kotlin.coroutines.resume

@Composable
internal actual fun rememberCollectionCardImageActions(): CollectionCardImageActions =
    remember {
        CollectionCardImageActions(
            save = ::saveImage,
            share = ::shareImage,
        )
    }

private suspend fun saveImage(bytes: ByteArray): CollectionImageActionOutcome {
    val image = bytes.toImage() ?: return CollectionImageActionOutcome.FAILED
    return suspendCancellableCoroutine { continuation ->
        PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
            if (!continuation.isActive) return@requestAuthorizationForAccessLevel
            if (status != PHAuthorizationStatusAuthorized && status != PHAuthorizationStatusLimited) {
                reportOnMain(continuation, CollectionImageActionOutcome.FAILED)
                return@requestAuthorizationForAccessLevel
            }
            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                changeBlock = { PHAssetChangeRequest.creationRequestForAssetFromImage(image) },
                completionHandler = { succeeded, _ ->
                    reportOnMain(
                        continuation,
                        if (succeeded) CollectionImageActionOutcome.SUCCEEDED else CollectionImageActionOutcome.FAILED,
                    )
                },
            )
        }
    }
}

private suspend fun shareImage(
    bytes: ByteArray,
    url: String,
): CollectionImageActionOutcome {
    val image = bytes.toImage() ?: return CollectionImageActionOutcome.FAILED
    val shareUrl = NSURL.URLWithString(url) ?: return CollectionImageActionOutcome.FAILED
    val rootViewController =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { window -> window.isKeyWindow() }
            ?.rootViewController
    if (rootViewController == null) {
        return CollectionImageActionOutcome.FAILED
    }
    var presenter = requireNotNull(rootViewController)
    while (presenter.presentedViewController != null) presenter = requireNotNull(presenter.presentedViewController)
    val shareController = UIActivityViewController(listOf(image, shareUrl), null)
    shareController.popoverPresentationController()?.let { popover ->
        popover.sourceView = presenter.view
        popover.sourceRect = presenter.view.bounds
    }
    return suspendCancellableCoroutine { continuation ->
        presenter.presentViewController(shareController, animated = true) {
            if (continuation.isActive) continuation.resume(CollectionImageActionOutcome.SUCCEEDED)
        }
    }
}

private fun ByteArray.toImage(): UIImage? {
    if (isEmpty()) return null
    val data = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
    return UIImage(data = data)
}

private fun reportOnMain(
    continuation: CancellableContinuation<CollectionImageActionOutcome>,
    outcome: CollectionImageActionOutcome,
) {
    NSOperationQueue.mainQueue.addOperationWithBlock {
        if (continuation.isActive) continuation.resume(outcome)
    }
}
