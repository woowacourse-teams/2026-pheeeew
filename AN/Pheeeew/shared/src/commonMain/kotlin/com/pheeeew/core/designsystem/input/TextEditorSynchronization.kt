package com.pheeeew.core.designsystem.input

/** A delayed acknowledgement must not replace a newer local edit or its IME selection. */
internal class TextEditorSynchronization(
    initialText: String,
) {
    private var externalText = initialText
    private var publishedText = initialText
    private val pendingTexts = mutableListOf<String>()

    fun publish(text: String): Boolean {
        if (text == publishedText) return false
        publishedText = text
        pendingTexts.add(text)
        return true
    }

    fun acceptExternal(text: String): Boolean {
        val echoedIndex = pendingTexts.lastIndexOf(text)
        if (echoedIndex >= 0) {
            pendingTexts.subList(0, echoedIndex + 1).clear()
            externalText = text
            return false
        }
        if (text == externalText) return false
        pendingTexts.clear()
        externalText = text
        publishedText = text
        return true
    }
}
