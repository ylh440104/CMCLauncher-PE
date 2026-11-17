package com.cmc.launcher.utils.network

import com.cmc.launcher.utils.logging.Logger
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ResilientDns : Dns {
    private const val TAG = "ResilientDns"

    private const val CACHE_TTL_MILLIS = 10 * 60 * 1000L

    private val cache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()

    private val dohClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .sslSocketFactory(TLSSecurity.sslSocketFactory, TLSSecurity.trustManager)
            .build()
    }

    private val dohProviders = listOf(
        "https://1.1.1.1/dns-query?name=%s&type=A",
        "https://8.8.8.8/resolve?name=%s&type=A",
        "https://223.5.5.5/resolve?name=%s&type=A"
    )

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            return Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            Logger.warning(TAG, "System DNS resolution failed for $hostname, falling back to DoH: ${e.message}")
        }

        cache[hostname]?.let { (addresses, timestamp) ->
            if (System.currentTimeMillis() - timestamp < CACHE_TTL_MILLIS) {
                return addresses
            }
            cache.remove(hostname)
        }

        var lastError: Exception? = null
        for (template in dohProviders) {
            try {
                val addresses = dohLookup(template.format(hostname))
                if (addresses.isNotEmpty()) {
                    Logger.info(
                        TAG,
                        "Resolved $hostname via DoH: ${addresses.joinToString { it.hostAddress ?: "?" }}"
                    )
                    cache[hostname] = addresses to System.currentTimeMillis()
                    return addresses
                }
            } catch (e: Exception) {
                lastError = e
                Logger.warning(TAG, "DoH lookup failed for $hostname: ${e.message}")
            }
        }

        throw UnknownHostException("Unable to resolve $hostname: system DNS and all DoH providers failed").apply {
            lastError?.let { initCause(it) }
        }
    }

    private fun dohLookup(url: String): List<InetAddress> {
        val request = Request.Builder()
            .url(url)
            .header("accept", "application/dns-json")
            .build()

        dohClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("DoH request failed: HTTP ${response.code}")

            val json = JSONObject(response.body.string())
            val status = json.optInt("Status", -1)
            if (status != 0) throw IOException("DoH response status: $status")

            val answers = json.optJSONArray("Answer") ?: return emptyList()
            return (0 until answers.length())
                .mapNotNull { answers.optJSONObject(it) }
                .filter { it.optInt("type") == 1 || it.optInt("type") == 28 }
                .mapNotNull { record ->
                    record.optString("data").takeIf { it.isNotBlank() }?.let { ip ->

                        runCatching { InetAddress.getByName(ip) }.getOrNull()
                    }
                }
                .distinctBy { it.hostAddress }
        }
    }
}
