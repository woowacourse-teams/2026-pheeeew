package com.pheeeew.core.monitoring

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

enum class ValueType { TEXT, INTEGER, DECIMAL, BOOLEAN, PRIMITIVE }

data class PropertyRule(
    val type: ValueType,
    val required: Boolean = false,
    val allowed: Set<String>? = null,
    val minimum: Double? = null,
    val maxLength: Int = 128,
) {
    internal fun accepts(value: JsonPrimitive): Boolean {
        if (value.content == "null" && !value.isString) return false
        return when (type) {
            ValueType.TEXT -> {
                value.isString
            }

            ValueType.INTEGER -> {
                !value.isString && value.longOrNull != null
            }

            ValueType.DECIMAL -> {
                !value.isString && value.doubleOrNull?.isFinite() == true
            }

            ValueType.BOOLEAN -> {
                !value.isString && value.booleanOrNull != null
            }

            ValueType.PRIMITIVE -> {
                value.isString || value.booleanOrNull != null ||
                    value.doubleOrNull?.isFinite() == true
            }
        } && (!value.isString || (value.content.length <= maxLength && value.content.none(Char::isISOControl))) &&
            (allowed == null || value.content in allowed) &&
            (minimum == null || (!value.isString && value.doubleOrNull?.let { it >= minimum } == true))
    }
}

data class EventDefinition(
    val name: String,
    val version: Int = 1,
    val properties: Map<String, PropertyRule> = emptyMap(),
    val preserveOnOverflow: Boolean = false,
)

/** Immutable after startup; feature definitions are supplied by the composition root. */
class EventRegistry(
    definitions: Collection<EventDefinition>,
    screens: Set<String>,
) {
    private val definitions: Map<Pair<String, Int>, EventDefinition>
    private val screens = screens.toSet() + "unknown"

    init {
        val copy =
            definitions.map {
                it.copy(
                    properties =
                        it.properties.mapValues { (_, rule) ->
                            rule.copy(allowed = rule.allowed?.toSet())
                        },
                )
            }
        copy.forEach {
            require(it.name.matches(Regex("[a-z][a-z0-9_]{0,79}")) && it.version > 0)
            require(it.properties.keys.none { key -> key in RESERVED_PROPERTIES })
            require(it.properties.values.all { rule -> rule.maxLength in 1..1024 })
        }
        require(copy.groupBy { it.name to it.version }.values.all { it.distinct().size == 1 }) {
            "Conflicting monitoring definitions"
        }
        this.definitions = copy.associateBy { it.name to it.version }
        require(this.screens.all { it.matches(Regex("[a-z][a-z0-9_]{0,79}")) })
    }

    fun screen(value: String): String = value.takeIf { it in screens } ?: "unknown"

    internal fun definition(
        name: String,
        version: Int,
    ): EventDefinition? = definitions[name to version]

    internal fun validate(
        definition: EventDefinition,
        fields: Map<String, JsonPrimitive>,
    ): JsonObject? {
        val registered = definitions[definition.name to definition.version] ?: return null
        if (definition != registered || fields.keys.any { it in RESERVED_PROPERTIES }) return null
        if (registered.properties.any { (key, rule) -> rule.required && key !in fields }) return null
        val selected = fields.filterKeys { it in registered.properties }
        if (selected.any { (key, value) -> !registered.properties.getValue(key).accepts(value) }) return null
        return JsonObject(selected)
    }

    /** Used again at the SDK boundary, including persisted SDK batches from old versions. */
    internal fun sanitizeEnvelope(
        name: String,
        fields: Map<String, JsonPrimitive>,
    ): JsonObject? {
        val version = fields["event_schema_version"]?.intOrNull ?: return null
        val definition = definition(name, version) ?: return null
        if (listOf("event_id", "anonymous_id").any {
                val value = fields[it]
                value == null || !value.isString || value.content.isBlank() ||
                    !PropertyRule(ValueType.TEXT).accepts(value)
            }
        ) {
            return null
        }
        if (COLLECTION_PROPERTY_RULES.any { (key, rule) ->
                fields[key]?.let { !rule.accepts(it) } == true
            }
        ) {
            return null
        }
        fields["product_generation"]?.let {
            if (!it.content.matches(Regex("[a-z][a-z0-9_]{0,79}"))) return null
        }
        val safe = validate(definition, fields.filterKeys { it !in RESERVED_PROPERTIES }) ?: return null
        val common =
            fields
                .filterKeys {
                    it in RESERVED_PROPERTIES
                }.filterValues { PropertyRule(ValueType.PRIMITIVE).accepts(it) }
        val normalized = common.toMutableMap()
        common["screen"]?.let { normalized["screen"] = JsonPrimitive(screen(it.content)) }
        return JsonObject(safe + normalized)
    }
}

internal val RESERVED_PROPERTIES =
    setOf(
        "product_generation",
        "data_source",
        "is_test_user",
        "is_research_participant",
        "classification_source",
        "event_id",
        "event_schema_version",
        "measurement_config_version",
        "anonymous_id",
        "occurred_at",
        "process_id",
        "event_sequence",
        "environment",
        "platform",
        "app_version",
        "build_number",
        "os_version",
        "device_class",
        "install_class",
        "session_id",
        "operation_id",
        "parent_operation_id",
        "screen",
    )

internal fun EventValue.primitive(): JsonPrimitive =
    when (this) {
        is EventValue.Text -> JsonPrimitive(value)
        is EventValue.Integer -> JsonPrimitive(value)
        is EventValue.Decimal -> JsonPrimitive(value)
        is EventValue.Flag -> JsonPrimitive(value)
    }

// Optional on historical envelopes: never backfill classification while restoring or sending.
private val COLLECTION_PROPERTY_RULES =
    mapOf(
        "product_generation" to PropertyRule(ValueType.TEXT, maxLength = 80),
        "data_source" to PropertyRule(ValueType.TEXT, allowed = DataSource.entries.map { it.wireValue }.toSet()),
        "is_test_user" to PropertyRule(ValueType.BOOLEAN),
        "is_research_participant" to PropertyRule(ValueType.BOOLEAN),
        "classification_source" to
            PropertyRule(
                ValueType.TEXT,
                allowed = ClassificationSource.entries.map { it.wireValue }.toSet(),
            ),
    )

internal fun CollectionMetadata.eventProperties(): Map<String, JsonPrimitive> =
    buildMap {
        put("product_generation", JsonPrimitive(productGeneration))
        put("data_source", JsonPrimitive(dataSource.wireValue))
        put("classification_source", JsonPrimitive(classificationSource.wireValue))
        isTestUser?.let { put("is_test_user", JsonPrimitive(it)) }
        isResearchParticipant?.let { put("is_research_participant", JsonPrimitive(it)) }
    }
