package com.example.kpkn.data.sync

import kotlinx.serialization.json.*
import java.math.BigDecimal

/** Canonical representation shared with the server's guest manifest; numeric scale is not identity. */
object CanonicalJson {
    fun encode(value: JsonElement): String = when (value) {
        is JsonObject -> value.keys.sorted().joinToString(",", "{", "}") { JsonPrimitive(it).toString() + ":" + encode(value.getValue(it)) }
        is JsonArray -> value.joinToString(",", "[", "]") { encode(it) }
        is JsonPrimitive -> if (value.isString || value == JsonNull || value.booleanOrNull != null) value.toString() else {
            val number = BigDecimal(value.content).stripTrailingZeros()
            if (number.signum() == 0) "0" else if (number.abs() >= BigDecimal("1e21") || number.abs() < BigDecimal("1e-6"))
                number.toString().lowercase().replace(Regex("e([0-9])"), "e+$1") else number.toPlainString()
        }
        else -> error("Unsupported JSON")
    }
}
