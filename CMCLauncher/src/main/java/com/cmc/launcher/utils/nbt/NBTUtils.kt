package com.cmc.launcher.utils.nbt

import com.github.steveice10.opennbt.tag.builtin.ByteTag
import com.github.steveice10.opennbt.tag.builtin.CompoundTag
import com.github.steveice10.opennbt.tag.builtin.DoubleTag
import com.github.steveice10.opennbt.tag.builtin.FloatTag
import com.github.steveice10.opennbt.tag.builtin.IntTag
import com.github.steveice10.opennbt.tag.builtin.ListTag
import com.github.steveice10.opennbt.tag.builtin.LongTag
import com.github.steveice10.opennbt.tag.builtin.ShortTag
import com.github.steveice10.opennbt.tag.builtin.StringTag
import com.github.steveice10.opennbt.tag.builtin.Tag

private inline fun <reified T : Tag, R> CompoundTag.getAs(key: String, crossinline mapper: (T) -> R, defaultValue: R): R {
    return (this.get(key) as? T)?.let(mapper) ?: defaultValue
}

fun CompoundTag.asCompoundTag(key: String): CompoundTag? {
    return this.get(key) as? CompoundTag
}

fun CompoundTag.asBoolean(key: String, defaultValue: Boolean?): Boolean? {
    return asByte(key, defaultValue?.let { if (it) 1 else 0 })?.let { it == 1.toByte() }
}

fun CompoundTag.asBooleanNotNull(key: String, defaultValue: Boolean): Boolean {
    return asByteNotNull(key, if (defaultValue) 1 else 0) == 1.toByte()
}

fun CompoundTag.asByte(key: String, defaultValue: Byte?): Byte? {
    return getAs<ByteTag, Byte?>(key, { it.value }, defaultValue)
}

fun CompoundTag.asByteNotNull(key: String, defaultValue: Byte): Byte {
    return getAs<ByteTag, Byte>(key, { it.value }, defaultValue)
}

fun CompoundTag.asShort(key: String, defaultValue: Short?): Short? {
    return getAs<ShortTag, Short?>(key, { it.value }, defaultValue)
}

fun CompoundTag.asShortNotNull(key: String, defaultValue: Short): Short {
    return getAs<ShortTag, Short>(key, { it.value }, defaultValue)
}

fun CompoundTag.asInt(key: String, defaultValue: Int?): Int? {
    return getAs<IntTag, Int?>(key, { it.value }, defaultValue)
}

fun CompoundTag.asIntNotNull(key: String, defaultValue: Int): Int {
    return getAs<IntTag, Int>(key, { it.value }, defaultValue)
}

fun CompoundTag.asLong(key: String, defaultValue: Long?): Long? {
    return getAs<LongTag, Long?>(key, { it.value }, defaultValue)
}

fun CompoundTag.asLongNotNull(key: String, defaultValue: Long): Long {
    return getAs<LongTag, Long>(key, { it.value }, defaultValue)
}

fun CompoundTag.asFloat(key: String, defaultValue: Float?): Float? {
    return getAs<FloatTag, Float?>(key, { it.value }, defaultValue)
}

fun CompoundTag.asFloatNotNull(key: String, defaultValue: Float): Float {
    return getAs<FloatTag, Float>(key, { it.value }, defaultValue)
}

fun CompoundTag.asDouble(key: String, defaultValue: Double?): Double? {
    return getAs<DoubleTag, Double?>(key, { it.value }, defaultValue)
}

fun CompoundTag.asDoubleNotNull(key: String, defaultValue: Double): Double {
    return getAs<DoubleTag, Double>(key, { it.value }, defaultValue)
}

fun CompoundTag.asString(key: String, defaultValue: String?): String? {
    return getAs<StringTag, String?>(key, { it.value }, defaultValue)
}

fun CompoundTag.asStringNotNull(key: String, defaultValue: String): String {
    return getAs<StringTag, String>(key, { it.value }, defaultValue)
}

fun CompoundTag.asList(key: String, defaultValue: ListTag?): ListTag? {
    return getAs<ListTag, ListTag?>(key, { it }, defaultValue)
}
