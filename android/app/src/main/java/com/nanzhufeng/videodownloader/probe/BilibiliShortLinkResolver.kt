package com.nanzhufeng.videodownloader.probe

import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface ShortLinkResolver {
    fun resolve(url: String): String
}

class BilibiliShortLinkResolver(
    private val fetchFinalUrl: (String) -> String = TrustedBilibiliShortLinkNetwork::resolve,
) : ShortLinkResolver {
    override fun resolve(url: String): String {
        val source = URI(url)
        if (!source.scheme.equals("https", ignoreCase = true) || !source.host.equals(B23_HOST, ignoreCase = true)) {
            return url
        }

        val resolved = URI(fetchFinalUrl(url))
        require(resolved.scheme.equals("https", ignoreCase = true)) {
            "哔哩哔哩短链接没有跳转到安全地址，已拒绝读取"
        }
        require(resolved.host.isBilibiliHost()) {
            "哔哩哔哩短链接跳转到了非官方域名，已拒绝读取"
        }
        return resolved.toString()
    }

    private fun String?.isBilibiliHost(): Boolean {
        val host = this?.lowercase().orEmpty()
        return host == BILIBILI_HOST || host.endsWith(".$BILIBILI_HOST")
    }

    private companion object {
        const val B23_HOST = "b23.tv"
        const val BILIBILI_HOST = "bilibili.com"
    }
}

internal object TrustedBilibiliShortLinkNetwork {
    private const val B23_HOST = "b23.tv"
    private const val CLOUDFLARE_DNS_HOST = "cloudflare-dns.com"
    private const val GOOGLE_DNS_HOST = "dns.google"

    private val bootstrapAddresses = mapOf(
        CLOUDFLARE_DNS_HOST to listOf(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("1.0.0.1"),
        ),
        GOOGLE_DNS_HOST to listOf(
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("8.8.4.4"),
        ),
    )
    private val trustedDnsEndpoints = listOf(
        "https://cloudflare-dns.com/dns-query?name=b23.tv&type=A",
        "https://dns.google/resolve?name=b23.tv&type=A",
    )

    private val bootstrapClient = OkHttpClient.Builder()
        .dns(
            object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    bootstrapAddresses[hostname.lowercase()] ?: Dns.SYSTEM.lookup(hostname)
            },
        )
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private val trustedClient = OkHttpClient.Builder()
        .dns(
            object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    if (hostname.equals(B23_HOST, ignoreCase = true)) {
                        trustedB23Addresses()
                    } else {
                        Dns.SYSTEM.lookup(hostname)
                    }
            },
        )
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val systemClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    fun resolve(url: String): String {
        val trustedFailure = runCatching { fetchFinalUrl(trustedClient, url) }
        trustedFailure.getOrNull()?.let { return it }

        return try {
            fetchFinalUrl(systemClient, url)
        } catch (systemFailure: Exception) {
            throw IOException(
                "bilibili short link secure resolution failed; " +
                    "trusted_dns=${trustedFailure.exceptionOrNull()?.message}; " +
                    "system=${systemFailure.message}",
                systemFailure,
            )
        }
    }

    private fun trustedB23Addresses(): List<InetAddress> {
        val failures = mutableListOf<String>()
        trustedDnsEndpoints.forEach { endpoint ->
            val result = runCatching {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/dns-json")
                    .build()
                bootstrapClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw UnknownHostException("可信 DNS 返回 HTTP ${response.code}")
                    }
                    parseIpv4Addresses(response.body?.string().orEmpty())
                }
            }
            result.getOrNull()?.takeIf(List<InetAddress>::isNotEmpty)?.let { return it }
            failures += result.exceptionOrNull()?.message ?: "没有返回 IPv4 地址"
        }
        throw UnknownHostException("可信 DNS 无法解析 b23.tv：${failures.joinToString("；")}")
    }

    private fun fetchFinalUrl(client: OkHttpClient, url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15; Mobile)")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("哔哩哔哩短链接返回 HTTP ${response.code}")
            }
            return response.request.url.toString()
        }
    }

    internal fun parseIpv4Addresses(payload: String): List<InetAddress> {
        val answers = ANSWERS.find(payload)?.groupValues?.get(1) ?: return emptyList()
        return OBJECT.findAll(answers).mapNotNull { match ->
            val item = match.value
            if (!IPV4_TYPE.containsMatchIn(item)) return@mapNotNull null
            val address = DATA.find(item)?.groupValues?.get(1) ?: return@mapNotNull null
            val octets = address.split('.').map(String::toIntOrNull)
            if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) {
                return@mapNotNull null
            }
            InetAddress.getByAddress(octets.map { it!!.toByte() }.toByteArray())
        }.distinctBy(InetAddress::getHostAddress).toList()
    }

    private val ANSWERS = Regex(""""Answer"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
    private val OBJECT = Regex("""\{[^{}]*\}""")
    private val IPV4_TYPE = Regex(""""type"\s*:\s*1(?:\D|$)""")
    private val DATA = Regex(""""data"\s*:\s*"([^"]+)"""")
}
