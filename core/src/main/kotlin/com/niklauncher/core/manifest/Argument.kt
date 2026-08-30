package com.niklauncher.core.manifest

import com.niklauncher.core.rules.Rule
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * One entry of `arguments.game` / `arguments.jvm`.
 *
 * Mojang mixes two shapes in the same JSON array: bare strings, and objects
 * carrying rules plus a value that is itself either a string or an array of
 * strings. That heterogeneity is why this type needs a hand-written serializer.
 */
@Serializable(with = ArgumentSerializer::class)
sealed interface Argument {

    /** The tokens this argument contributes when it applies. */
    val values: List<String>

    data class Literal(val value: String) : Argument {
        override val values: List<String> get() = listOf(value)
    }

    data class Conditional(
        val rules: List<Rule>,
        override val values: List<String>,
    ) : Argument
}

object ArgumentSerializer : KSerializer<Argument> {

    @OptIn(kotlinx.serialization.InternalSerializationApi::class, kotlinx.serialization.ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("com.niklauncher.core.manifest.Argument", PolymorphicKind.SEALED)

    override fun deserialize(decoder: Decoder): Argument {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("Argument can only be decoded from JSON")
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> Argument.Literal(element.content)
            is JsonObject -> {
                val rules = element["rules"]
                    ?.let { input.json.decodeFromJsonElement(ListSerializer(Rule.serializer()), it) }
                    ?: emptyList()
                Argument.Conditional(rules, readValues(element["value"]))
            }
            else -> throw SerializationException("Unsupported argument node: $element")
        }
    }

    private fun readValues(node: kotlinx.serialization.json.JsonElement?): List<String> = when (node) {
        null -> emptyList()
        is JsonPrimitive -> listOf(node.content)
        is JsonArray -> node.mapNotNull { (it as? JsonPrimitive)?.content }
        else -> emptyList()
    }

    override fun serialize(encoder: Encoder, value: Argument) {
        val output = encoder as? JsonEncoder
            ?: throw SerializationException("Argument can only be encoded to JSON")
        val element = when (value) {
            is Argument.Literal -> JsonPrimitive(value.value)
            is Argument.Conditional -> buildJsonObject {
                put("rules", output.json.encodeToJsonElement(ListSerializer(Rule.serializer()), value.rules))
                put("value", output.json.encodeToJsonElement(value.values))
            }
        }
        output.encodeJsonElement(element)
    }
}
