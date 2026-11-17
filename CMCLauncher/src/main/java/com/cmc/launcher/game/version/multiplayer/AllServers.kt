package com.cmc.launcher.game.version.multiplayer

import com.github.steveice10.opennbt.NBTIO
import com.github.steveice10.opennbt.tag.builtin.ByteTag
import com.github.steveice10.opennbt.tag.builtin.CompoundTag
import com.github.steveice10.opennbt.tag.builtin.ListTag
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.nbt.asBoolean
import com.cmc.launcher.utils.nbt.asBooleanNotNull
import com.cmc.launcher.utils.nbt.asList
import com.cmc.launcher.utils.nbt.asString
import com.cmc.launcher.utils.nbt.asStringNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Files
import java.util.Base64

private const val TAG = "AllServers"

class AllServers {
    private val _serverList = mutableListOf<ServerData>()
    val serverList: List<ServerData>
        get() = _serverList

    private val hiddenServerList = mutableListOf<ServerData>()

    suspend fun loadServers(dataFile: File) {
        withContext(Dispatchers.IO) {
            runCatching {

                _serverList.clear()
                hiddenServerList.clear()

                if (!dataFile.exists()) return@withContext

                val compound = NBTIO.readFile(dataFile, false, false)
                    ?: error("Failed to read the server.dat file as a CompoundTag.")
                val servers = compound.asList("servers", null)
                    ?: error("servers entry not found in the NBT structure tree.")

                servers.forEach { tag ->
                    val serverTag = tag as? CompoundTag ?: return@forEach
                    val data = serverTag.parseServerData()
                    val isHidden = serverTag.asBooleanNotNull("hidden", false)
                    if (!isHidden) {
                        _serverList.add(data)
                    } else {
                        hiddenServerList.add(data)
                    }
                }
            }.onFailure {
                Logger.warning(TAG, "An exception occurred while reading and parsing the servers.dat file (${dataFile.absolutePath}).", it)
            }
        }
    }

    fun addServer(
        server: ServerData,
        isHidden: Boolean = false
    ) {
        if (isHidden) {
            hiddenServerList.add(server)

            while (this.hiddenServerList.size > 16) {
                this.hiddenServerList.removeAt(this.hiddenServerList.size - 1)
            }
        } else {
            _serverList.add(server)
        }
    }

    fun removeServer(data: ServerData) {
        if (!_serverList.remove(data)) {
            hiddenServerList.remove(data)
        }
    }

    suspend fun save(savePath: File) {
        withContext(Dispatchers.IO) {
            runCatching {
                val tag = CompoundTag("")
                val serverTags = ListTag("servers")

                serverTags.addServers(_serverList, false)
                serverTags.addServers(hiddenServerList, true)

                tag.put(serverTags)

                val currentPath = savePath.toPath()
                val newFile = Files.createTempFile(currentPath, "servers", ".dat").toFile()
                NBTIO.writeFile(tag, newFile, false, false)

                val currentDataFile = currentPath.resolve("servers.dat").toFile()
                val oldDataFile = currentPath.resolve("servers.dat_old").toFile()

                FileUtils.deleteQuietly(oldDataFile)
                if (currentDataFile.exists()) {
                    currentDataFile.copyTo(oldDataFile, true)
                    FileUtils.deleteQuietly(currentDataFile)
                }

                newFile.copyTo(currentDataFile, true)
                FileUtils.deleteQuietly(newFile)
            }.onFailure {
                Logger.warning(TAG, "Couldn't save server list", it)
            }
        }
    }

    private fun ListTag.addServers(
        list: List<ServerData>,
        hidden: Boolean
    ) {
        list.forEach { data ->
            val serverTag = data.save()
            serverTag.put(ByteTag("hidden", (if (hidden) 1 else 0).toByte()))
            add(serverTag)
        }
    }

    private fun CompoundTag.parseServerData(): ServerData {
        val name = asStringNotNull("name", "")
        val origin = asStringNotNull("ip", "")

        val icon = asString("icon", null)?.let { base64 ->
            runCatching {
                Base64.getDecoder().decode(base64)
            }.onFailure {
                Logger.warning(TAG, "Unable to recognize server $name's icon as a valid icon array")
            }.getOrNull()
        }

        val texturesStatus = asBoolean("acceptTextures", null).let { code ->
            when (code) {
                true -> ServerData.TexturePackStatus.ENABLED
                false -> ServerData.TexturePackStatus.DISABLED
                null -> ServerData.TexturePackStatus.PROMPT
            }
        }

        val acceptedCodeOfConduct = asBoolean("acceptedCodeOfConduct", null)

        return ServerData(
            name = name,
            originIp = origin,
            texturePackStatus = texturesStatus,
            acceptedCodeOfConduct = acceptedCodeOfConduct,
            icon = icon
        )
    }
}
