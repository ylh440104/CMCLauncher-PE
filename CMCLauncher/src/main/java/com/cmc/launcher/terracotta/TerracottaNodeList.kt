package com.cmc.launcher.terracotta

import com.google.gson.JsonParseException
import com.cmc.launcher.path.GLOBAL_CLIENT
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.utils.isChinaMainland
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.network.safeBodyAsJson
import com.cmc.launcher.utils.string.isNotEmptyOrBlank
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URISyntaxException

private const val TAG = "TerracottaNodeList"

private const val NODE_LIST_URL = "https://terracotta.glavo.site/nodes"

@Serializable
private data class TerracottaNode(
    @SerialName("url")
    val url: String? = null,
    @SerialName("region")
    val region: String? = null
) {
    fun validate() {
        requireNotNull(url, { "TerracottaNode.url cannot be null" })
        try {
            URI(url)
        } catch (e: URISyntaxException) {
            throw JsonParseException("Invalid URL: $url", e)
        }
    }
}

@Volatile
private var nodeList: List<URI>? = null

private val fetchMutex = Mutex()

suspend fun fetchNodes(): List<URI> {
    nodeList?.let { return it }

    return fetchMutex.withLock {
        nodeList?.let { return it }

        if (AllSettings.enableTerracottaNodes.getValue()) {

            val url = AllSettings.terracottaNodes.getValue()
            if (url.isNotEmptyOrBlank()) {
                val tempList = listOf(URI(url))
                nodeList = tempList
                return tempList
            }

        }

        withContext(Dispatchers.IO) {
            fetchNodesFromRemote()
        }.also { result ->
            nodeList = result
        }
    }
}

private suspend fun fetchNodesFromRemote(): List<URI> {
    return runCatching {
        val nodes = GLOBAL_CLIENT
            .get(NODE_LIST_URL)
            .safeBodyAsJson<List<TerracottaNode?>?>()

        if (nodes.isNullOrEmpty()) {
            Logger.info(TAG, "No available Terracotta nodes found")
            return emptyList()
        }

        val result = nodes
            .asSequence()
            .mapNotNull { node ->
                if (node == null) return@mapNotNull null
                parseNode(node)
            }
            .toList()

        result
    }.onFailure {
        Logger.warning(TAG, "Failed to fetch terracotta node list", it)
    }.getOrDefault(emptyList())
}

private fun parseNode(
    node: TerracottaNode
): URI? {
    return try {
        node.validate()

        if (!shouldUseNode(node.region)) {
            return null
        }

        URI.create(node.url)

    } catch (e: Exception) {
        Logger.warning(TAG, "Invalid terracotta node: $node", e)
        null
    }
}

private fun shouldUseNode(
    region: String?
): Boolean {
    if (region.isNullOrBlank()) return true

    val isMainLand = isChinaMainland()
    return isMainLand && region.equals("CN", ignoreCase = true)
}
