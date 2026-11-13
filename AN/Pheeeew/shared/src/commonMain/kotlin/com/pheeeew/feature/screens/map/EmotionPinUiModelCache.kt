package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.emotion.EmotionMapPin
import kotlin.time.Instant

/** Bounded presentation cache for pin conversion and the existing createdAt / ID ordering. */
internal class EmotionPinUiModelCache(
    private val maximumEntries: Int = DEFAULT_MAXIMUM_ENTRIES,
) {
    private val entries = linkedMapOf<Long, Entry>()

    internal val size: Int get() = entries.size

    init {
        require(maximumEntries > 0) { "maximumEntries must be positive" }
    }

    fun toUiModel(pin: EmotionMapPin): EmotionPinUiModel {
        val cached = entries[pin.id]
        if (cached?.source == pin) {
            touch(pin.id, cached)
            return cached.model
        }
        val model = pin.toUiModel()
        put(pin.id, Entry(pin, model, Instant.parse(model.createdAt)))
        return model
    }

    fun orderedModels(pins: Collection<EmotionPinUiModel>): List<EmotionPinUiModel> =
        pins
            .distinctBy(EmotionPinUiModel::id)
            .map { model ->
                val cached = entries[model.id]
                val entry =
                    if (cached?.model == model) {
                        touch(model.id, cached)
                        cached
                    } else {
                        Entry(source = null, model = model, createdAt = Instant.parse(model.createdAt))
                            .also { put(model.id, it) }
                    }
                model to entry.createdAt
            }.sortedWith(compareBy<Pair<EmotionPinUiModel, Instant>> { it.second }.thenBy { it.first.id })
            .map { it.first }
            .toList()

    fun orderedPins(
        pins: Collection<EmotionMapPin>,
        localPins: Collection<EmotionPinUiModel>,
        hiddenIds: Set<Long>,
        contains: (EmotionMapPin) -> Boolean,
        containsLocal: (EmotionPinUiModel) -> Boolean,
    ): List<EmotionPinUiModel> {
        val distinctPins = pins.filter(contains).distinctBy(EmotionMapPin::id)
        val domainIds = distinctPins.mapTo(mutableSetOf(), EmotionMapPin::id)
        val models =
            distinctPins
                .asSequence()
                .filterNot { it.id in hiddenIds }
                .map(::toUiModel)
                .toList() +
                localPins
                    .asSequence()
                    .filter(containsLocal)
                    .filterNot { it.id in hiddenIds || it.id in domainIds }
                    .toList()
        return orderedModels(models)
    }

    private fun touch(
        id: Long,
        entry: Entry,
    ) {
        entries.remove(id)
        entries[id] = entry
    }

    private fun put(
        id: Long,
        entry: Entry,
    ) {
        entries.remove(id)
        entries[id] = entry
        while (entries.size > maximumEntries) entries.remove(entries.keys.first())
    }

    private data class Entry(
        val source: EmotionMapPin?,
        val model: EmotionPinUiModel,
        val createdAt: Instant,
    )

    companion object {
        const val DEFAULT_MAXIMUM_ENTRIES = 4_096
    }
}
