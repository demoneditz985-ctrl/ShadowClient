package com.project.vortex.client.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.JsonParser
import com.project.vortex.client.constructors.AccountManager
import com.project.vortex.client.model.Account
import com.project.vortex.relay.util.XboxDeviceInfo
import com.project.vortex.relay.util.XboxGamerTagException
import com.project.vortex.relay.util.base64Decode
import com.project.vortex.relay.util.fetchIdentityToken
import com.project.vortex.relay.util.fetchRawChain
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.random.nextInt

@SuppressLint("SetJavaScriptEnabled")
class AuthWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : WebView(context, attrs) {

    /** Login entry point, reused by the "Try again" button on the error page. */
    private val loginUrl: String
        get() = "https://login.live.com/oauth20_authorize.srf" +
                "?client_id=${deviceInfo!!.appId}" +
                "&redirect_uri=https://login.live.com/oauth20_desktop.srf" +
                "&response_type=code" +
                "&scope=service::user.auth.xboxlive.com::MBI_SSL"

    private var loadingPageHtml: String? = null

    private var account: Pair<String, String>? = null

    private val handler = Handler(Looper.getMainLooper())

    /** Watchdog so the user never gets stuck on "Please wait" forever. */
    private var stepToken = 0
    private val watchdog = Runnable {
        showErrorPage(
            "This is taking longer than expected. Check your internet connection " +
                    "(a VPN can block Xbox sign-in) and try again."
        )
    }

    var deviceInfo: XboxDeviceInfo? = null

    var callback: ((success: Boolean) -> Unit)? = null

    init {
        CookieManager.getInstance()
            .removeAllCookies(null)

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = AuthWebViewClient()
    }

    fun addAccount() {
        loadUrl(loginUrl)
    }

    inner class AuthWebViewClient : WebViewClient() {

        override fun onReceivedSslError(
            view: WebView?,
            handler: android.webkit.SslErrorHandler?,
            error: android.net.http.SslError?
        ) {
            // Never proceed through an invalid certificate during sign-in; just explain it.
            handler?.cancel()
            error?.let { errors.ssl(it) }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: android.webkit.WebResourceError?
        ) {
            if (request?.isForMainFrame == true) {
                errors.page(error?.description?.toString() ?: "network error", request.url?.toString())
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            if (account != null && (request.url.scheme ?: "").startsWith("ms-xal")) {
                thread {
                    startStep()
                    try {
                        handler.post { showLoadingPage("Verifying your credentials...") }

                        val identityToken = fetchIdentityToken(account!!.first, deviceInfo!!)
                        handler.post { showLoadingPage("Almost done...") }
                        val username = getUsernameFromChain(
                            fetchRawChain(
                                identityToken.token,
                                EncryptionUtils.createKeyPair().public
                            ).readText()
                        )

                        val newAccount = Account(
                            username,
                            deviceInfo!!,
                            account!!.second
                        )
                        AccountManager.accounts.add(newAccount)
                        AccountManager.save()

                        AccountManager.selectAccount(newAccount)

                        finishStep()
                        handler.post { callback?.invoke(true) }
                    } catch (t: Throwable) {
                        failStep("Sign-in could not be completed", t)
                    }
                }
                return true
            }
            val url = request.url.toString().toHttpUrlOrNull() ?: return false
            if (url.host != "login.live.com" || url.encodedPath != "/oauth20_desktop.srf") {
                if (url.queryParameter("res") == "cancel") {
                    Log.e("AuthWebView", "Action cancelled")
                    finishStep()
                    callback?.invoke(false)
                    return false
                }
                Log.e("AuthWebView", "Invalid url ${request.url}")
                return false
            }

            val authCode = url.queryParameter("code") ?: return false

            showLoadingPage("Setting up your account...")
            thread {
                startStep()
                try {
                    val (accessToken, refreshToken) = deviceInfo!!.refreshToken(authCode, isAuthCode = true)
                    handler.post { showLoadingPage("Authenticating with Xbox...") }

                    val username = try {
                        val identityToken = fetchIdentityToken(accessToken, deviceInfo!!)
                        handler.post { showLoadingPage("Retrieving your profile...") }
                        getUsernameFromChain(
                            fetchRawChain(
                                identityToken.token,
                                EncryptionUtils.createKeyPair().public
                            ).readText()
                        )
                    } catch (e: XboxGamerTagException) {
                        account = accessToken to refreshToken
                        handler.post {
                            showLoadingPage("Xbox profile needed...")
                            loadUrl(e.sisuStartUrl)
                        }
                        return@thread
                    }

                    val account = Account(username, deviceInfo!!, refreshToken)
                    while (AccountManager.accounts.map { it.remark }.contains(account.remark)) {
                        account.remark += Random.nextInt(0..9)
                    }
                    AccountManager.accounts.add(account)
                    AccountManager.save()

                    AccountManager.selectAccount(account)

                    finishStep()
                    handler.post { callback?.invoke(true) }
                } catch (t: Throwable) {
                    failStep("Sign-in could not be completed", t)
                }
            }
            return true
        }
    }

    inner class Errors {
        fun ssl(error: android.net.http.SslError) = showErrorPage(
            "The sign-in page could not be opened securely.\n\n" +
                    "1. Turn on automatic date & time in your phone settings.\n" +
                    "2. Turn off any VPN, ad-blocker or Private DNS.\n" +
                    "3. Try another network (switch between Wi-Fi and mobile data).",
            error.url ?: "SSL error"
        )

        fun page(description: String, failingUrl: String?) = showErrorPage(
            "The sign-in page could not be loaded ($description). Check your internet " +
                    "connection and press Try again.",
            failingUrl ?: description
        )
    }

    val errors = Errors()

    // ---------------------------------------------------------------- helpers

    private fun startStep() {
        stepToken++
        val token = stepToken
        handler.postDelayed(watchdog, STEP_TIMEOUT_MS)
        // cancel the watchdog if a newer step replaced this one
        handler.postDelayed({
            if (token != stepToken) handler.removeCallbacks(watchdog)
        }, 0)
    }

    private fun finishStep() {
        stepToken++
        handler.removeCallbacks(watchdog)
    }

    private fun failStep(title: String, t: Throwable) {
        finishStep()
        Log.e("AuthWebView", "$title: ${t.stackTraceToString()}")
        val raw = (t.message ?: t.javaClass.simpleName).take(400)
        showErrorPage(describe(t), raw)
    }

    /** Turns a raw exception into something a user can act on. */
    private fun describe(t: Throwable): String {
        val raw = t.message ?: t.javaClass.simpleName
        return when {
            raw.contains("401") || raw.contains("403") ->
                "Xbox rejected the sign-in. Make sure the account owns Minecraft " +
                        "(or is added to a family), then try again."
            raw.contains("no Xbox profile", ignoreCase = true) ||
                    raw.contains("gamertag", ignoreCase = true) ->
                "This Microsoft account has no Xbox profile yet. Create a gamertag " +
                        "at xbox.com, then sign in again."
            raw.contains("did not return a certificate chain") ->
                "Xbox sign-in was refused. Try signing in with a different account " +
                        "or after disconnecting any VPN."
            raw.contains("timeout", ignoreCase = true) || t is java.net.SocketTimeoutException ->
                "The connection to Xbox timed out. Check your internet connection and try again."
            t is java.net.UnknownHostException ->
                "No internet connection. Connect to a network and try again."
            raw.contains("SSL", ignoreCase = true) ||
                    raw.contains("handshake", ignoreCase = true) ||
                    raw.contains("certificate", ignoreCase = true) ||
                    t is javax.net.ssl.SSLException ->
                "The secure connection to Xbox could not be established.\n\n" +
                        "1. Turn on automatic date & time in your phone settings.\n" +
                        "2. Turn off any VPN, ad-blocker or Private DNS.\n" +
                        "3. Try another network (switch between Wi-Fi and mobile data)."
            else -> raw.take(300)
        }
    }

    private fun showErrorPage(message: String, details: String = message) {
        handler.post {
            val safe = message
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            val detailsEscaped = details
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .take(400)
            val html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <style>
                    body { margin:0; height:100vh; display:flex; align-items:center; justify-content:center;
                           background:#0A0611; color:#F6EFFF; font-family:'Segoe UI',Arial,sans-serif; text-align:center; }
                    .wrap { max-width: 90%; padding: 24px; }
                    h1 { color:#B026FF; font-size:24px; margin-bottom:16px; }
                    p { color:#D9C2FF; font-size:15px; line-height:1.5; }
                    a.btn { display:inline-block; margin-top:28px; padding:14px 28px; border-radius:12px;
                            background:#B026FF; color:#0A0611; font-weight:700; text-decoration:none; }
                    .detail { margin-top:26px; font-size:11px; color:#8A7BA8; word-break:break-word; }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <h1>Sign-in failed</h1>
                    <p>$safe</p>
                    <a class="btn" href="$loginUrl">Try again</a>
                    <p class="detail">Details: $detailsEscaped</p>
                  </div>
                </body>
                </html>
            """.trimIndent()
            val encoded = Base64.encodeToString(html.toByteArray(), Base64.DEFAULT)
            loadData(encoded, "text/html; charset=UTF-8", "base64")
        }
    }

    private fun getUsernameFromChain(chains: String): String {
        val root = try {
            JsonParser.parseString(chains).asJsonObject
        } catch (e: Exception) {
            // not JSON at all (HTML error page, empty body, ...)
            throw IllegalStateException(
                "Xbox returned an unexpected response: ${chains.take(300)}"
            )
        }

        // getAsJsonArray() returns null when the member is missing - iterating it
        // was the NullPointerException reported from this method.
        val body = root.getAsJsonArray("chain")
            ?: throw IllegalStateException(
                "Xbox did not return a profile chain: ${chains.take(300)}"
            )

        for (chain in body) {
            val parts = (chain.asString).split(".")
            if (parts.size < 2) continue

            val chainBody = try {
                JsonParser.parseString(
                    base64Decode(parts[1]).toString(Charsets.UTF_8)
                ).asJsonObject
            } catch (e: Exception) {
                continue
            }

            val extraData = chainBody.getAsJsonObject("extraData") ?: continue

            val displayName = extraData.get("displayName")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.takeIf { it.isNotBlank() }
            if (displayName != null) return displayName

            // no gamertag yet -> let the caller run the gamertag creation flow
            val xuid = extraData.get("xid")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.takeIf { it.isNotBlank() }
            if (xuid != null) return "Player${xuid.takeLast(6)}"
        }

        throw IllegalStateException(
            "This Microsoft account has no Xbox profile yet, so Vortex cannot read a gamertag."
        )
    }

    fun showLoadingPage(title: String) {
        val data = loadingPageHtml
            ?: context.assets.open("loading.html").readBytes().decodeToString().also { loadingPageHtml = it }
        val replacedData = data.replace("\$title", title)
        val encodedText = Base64.encodeToString(replacedData.toByteArray(), Base64.DEFAULT)
        loadData(encodedText, "text/html; charset=UTF-8", "base64")
    }

    fun loadData(text: String) {
        loadData(text, "text/html", "UTF-8")
    }

    override fun onDetachedFromWindow() {
        finishStep()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val STEP_TIMEOUT_MS = 90_000L
    }
}
