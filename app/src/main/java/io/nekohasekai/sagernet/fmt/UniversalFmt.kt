package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import moe.matsuri.nb4a.utils.Util
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val protectKey = "82a6f45643ab498eb48c0d8686a6b4d1a78ed9a2124ca1f71bd3647de26c9a8a"
    .chunked(2)
    .map { it.toInt(16).toByte() }
    .toByteArray()

fun parseUniversal(link: String): AbstractBean {
    if (link.startsWith("sn://protect?")) {
        return parseProtectedUniversal(link)
    }

    return if (link.contains("?")) {
        val type = link.substringAfter("sn://").substringBefore("?")
        ProxyEntity(type = TypeMap[type] ?: error("Type $type not found")).apply {
            putByteArray(Util.zlibDecompress(Util.b64Decode(link.substringAfter("?"))))
        }.requireBean()
    } else {
        val type = link.substringAfter("sn://").substringBefore(":")
        ProxyEntity(type = TypeMap[type] ?: error("Type $type not found")).apply {
            putByteArray(Util.b64Decode(link.substringAfter(":").substringAfter(":")))
        }.requireBean()
    }
}

fun AbstractBean.toUniversalLink(): String {
    var link = "sn://"
    link += TypeMap.reversed[ProxyEntity().putBean(this).type]
    link += "?"
    link += Util.b64EncodeUrlSafe(Util.zlibCompress(KryoConverters.serialize(this), 9))
    return link
}

fun AbstractBean.toProtectedUniversalLink(): String {
    val type = TypeMap.reversed[ProxyEntity().putBean(this).type]
        ?: error("Unknown profile type")

    val typeBytes = type.toByteArray(Charsets.UTF_8)
    val serialized = KryoConverters.serialize(this)
    val compressed = Util.zlibCompress(
        typeBytes + byteArrayOf(0) + serialized,
        9
    )

    val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
        Cipher.ENCRYPT_MODE,
        SecretKeySpec(protectKey, "AES"),
        GCMParameterSpec(128, iv)
    )

    val encrypted = cipher.doFinal(compressed)
    return "sn://protect?" + Util.b64EncodeUrlSafe(iv + encrypted)
}

fun parseProtectedUniversal(link: String): AbstractBean {
    require(link.startsWith("sn://protect?")) {
        "Invalid Protect link"
    }

    val payload = Util.b64Decode(link.substringAfter("?"))
    require(payload.size > 28) {
        "Invalid Protect payload"
    }

    val iv = payload.copyOfRange(0, 12)
    val encrypted = payload.copyOfRange(12, payload.size)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
        Cipher.DECRYPT_MODE,
        SecretKeySpec(protectKey, "AES"),
        GCMParameterSpec(128, iv)
    )

    val raw = Util.zlibDecompress(cipher.doFinal(encrypted))
    val separator = raw.indexOf(0.toByte())

    require(separator > 0 && separator < raw.lastIndex) {
        "Invalid protected profile data"
    }

    val type = String(raw, 0, separator, Charsets.UTF_8)
    val serialized = raw.copyOfRange(separator + 1, raw.size)

    return ProxyEntity(
        type = TypeMap[type] ?: error("Protected profile type not found")
    ).apply {
        putByteArray(serialized)
    }.requireBean()
}

fun ProxyGroup.toUniversalLink(): String {
    var link = "sn://subscription?"
    export = true
    link += Util.b64EncodeUrlSafe(Util.zlibCompress(KryoConverters.serialize(this), 9))
    export = false
    return link
}
