package com.cmc.launcher.utils.json

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

fun JsonObject.merge(other: JsonObject) {
    other.entrySet().forEach { (key, otherValue) ->
        when (val currentValue = this.get(key)) {

            is JsonObject -> if (otherValue is JsonObject) {
                currentValue.merge(otherValue)
            } else {

                this.add(key, otherValue.deepCopy())
            }

            is JsonArray -> if (otherValue is JsonArray) {
                otherValue.forEach { element ->
                    currentValue.add(element.deepCopy())
                }
            } else {
                this.add(key, otherValue.deepCopy())
            }

            else -> this.add(key, otherValue.deepCopy())
        }
    }
}

fun JsonObject.safeGetMember(memberName: String): String {
    return this.get(memberName)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
}

fun String.parseToJson(): JsonObject = JsonParser.parseString(this).asJsonObject
