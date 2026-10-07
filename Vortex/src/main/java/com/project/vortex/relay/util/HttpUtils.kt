package com.project.vortex.relay.util

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object HttpUtils {
    private const val DEFAULT_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36 Edg/114.0.1788.0"

    /**
     * OkHttp's default spec only offers MODERN_TLS. Some networks, older devices and
     * TLS-inspecting proxies can only complete a handshake with the compatible
     * (TLS 1.0/1.1 + legacy ciphers) spec, which surfaced to users as
     * "SSL routines:OPENSSL_internal:HANDSHAKE_FAILURE". Offering both, after the
     * modern one, makes sign-in work in far more places.
     */
    private val connectionSpecs = listOf(
        ConnectionSpec.MODERN_TLS,
        ConnectionSpec.COMPATIBLE_TLS,
        ConnectionSpec.CLEARTEXT
    )

    val client = OkHttpClient.Builder()
        .proxy(getSystemProxyConfig())
        .connectionSpecs(connectionSpecs)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request()
                .newBuilder()
                .header("User-Agent", DEFAULT_AGENT)
                .build())
        }
        .build()

    /**
     * @return http proxy from JVM Options, [Proxy.NO_PROXY] if JVM Option not set
     */
    private fun getSystemProxyConfig(): Proxy {
        val proxyHost = System.getProperty("http.proxyHost") ?: return Proxy.NO_PROXY
        val proxyPort = System.getProperty("http.proxyPort") ?: return Proxy.NO_PROXY

        return try {
            Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort.toInt()))
        } catch (t: Throwable) {
            t.printStackTrace()
            Proxy.NO_PROXY
        }
    }

}