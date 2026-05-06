package com.solanadistributionmarketdemo.core

import java.math.BigInteger
import kotlin.math.max
import kotlin.math.min

const val FIXED_SCALE = 1_000_000_000.0
const val FIXED_EPSILON = 0.000000001

fun encodeHex(bytes: ByteArray): String {
    val hex = StringBuilder(bytes.size * 2)
    bytes.forEach { byte ->
        hex.append(((byte.toInt() ushr 4) and 0x0f).toString(16))
        hex.append((byte.toInt() and 0x0f).toString(16))
    }
    return hex.toString()
}

fun decodeHex(value: String): ByteArray {
    val bytes = ByteArray(value.length / 2)
    for (index in bytes.indices) {
        val high = value[index * 2].digitToInt(16)
        val low = value[index * 2 + 1].digitToInt(16)
        bytes[index] = ((high shl 4) or low).toByte()
    }
    return bytes
}

fun packU64(value: Long): List<Byte> =
    List(8) { index -> ((value ushr (index * 8)) and 0xff).toByte() }

fun packU32(value: Long): List<Byte> =
    List(4) { index -> ((value ushr (index * 8)) and 0xff).toByte() }

fun packFixed(value: Double): List<Byte> {
    val scaled = BigInteger.valueOf((value * FIXED_SCALE).let { kotlin.math.round(it).toLong() })
    return packI128LittleEndian(scaled)
}

fun packI128LittleEndian(value: BigInteger): List<Byte> {
    val signByte: Byte = if (value.signum() < 0) 0xff.toByte() else 0x00
    val bigEndian = value.toByteArray()
    val padded = MutableList(16) { signByte }
    val copyStart = max(0, bigEndian.size - 16)
    val copyLength = min(16, bigEndian.size)
    for (index in 0 until copyLength) {
        padded[16 - copyLength + index] = bigEndian[copyStart + index]
    }
    return padded.reversed()
}

fun shortHash(value: String, head: Int = 4, tail: Int = 4): String {
    if (value.length <= head + tail + 1) return value
    return value.take(head) + "…" + value.takeLast(tail)
}
