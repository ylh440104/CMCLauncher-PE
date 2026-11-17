package com.cmc.launcher.game.account.offline

import com.cmc.launcher.game.account.wardrobe.SkinModelType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoadedSkin(
    @SerialName("skinHash")
    val skinHash: String? = null,
    @SerialName("skinBytes")
    val skinBytes: ByteArray? = null,
    @SerialName("capeHash")
    val capeHash: String? = null,
    @SerialName("capeBytes")
    val capeBytes: ByteArray? = null,
    @SerialName("model")
    val model: SkinModelType = SkinModelType.NONE
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LoadedSkin

        if (skinHash != other.skinHash) return false
        if (!skinBytes.contentEquals(other.skinBytes)) return false
        if (capeHash != other.capeHash) return false
        if (!capeBytes.contentEquals(other.capeBytes)) return false
        if (model != other.model) return false

        return true
    }

    override fun hashCode(): Int {
        var result = skinHash?.hashCode() ?: 0
        result = 31 * result + (skinBytes?.contentHashCode() ?: 0)
        result = 31 * result + (capeHash?.hashCode() ?: 0)
        result = 31 * result + (capeBytes?.contentHashCode() ?: 0)
        result = 31 * result + model.hashCode()
        return result
    }
}
