package com.cmc.launcher.game.account.microsoft

import com.cmc.launcher.BuildKeys
import com.cmc.launcher.game.account.Account
import com.cmc.launcher.game.account.AccountType
import com.cmc.launcher.game.account.AccountsManager
import com.cmc.launcher.game.account.CredentialsExpiredException
import com.cmc.launcher.game.account.microsoft.MinecraftProfileException.ExceptionStatus.BLOCKED_IP
import com.cmc.launcher.game.account.microsoft.MinecraftProfileException.ExceptionStatus.FREQUENT
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.BANNED
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.BLOCKED_REGION
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.NOT_ACCEPTED_SERVICE
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.REACHED_PLAYTIME_LIMIT
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.REQUIRES_PROOF_OF_AGE
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.RESTRICTED
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.UNDERAGE
import com.cmc.launcher.game.account.microsoft.XboxLoginException.ExceptionStatus.UNREGISTERED
import com.cmc.launcher.game.account.microsoft.models.DeviceCodeResponse
import com.cmc.launcher.game.account.microsoft.models.MinecraftAuthResponse
import com.cmc.launcher.game.account.microsoft.models.TokenResponse
import com.cmc.launcher.game.account.microsoft.models.XBLProperties
import com.cmc.launcher.game.account.microsoft.models.XBLRequest
import com.cmc.launcher.game.account.microsoft.models.XSTSAuthResult
import com.cmc.launcher.game.account.microsoft.models.XSTSProperties
import com.cmc.launcher.game.account.microsoft.models.XSTSRequest
import com.cmc.launcher.game.account.wardrobe.SkinModelType
import com.cmc.launcher.game.account.yggdrasil.findUsing
import com.cmc.launcher.game.account.yggdrasil.getPlayerProfile
import com.cmc.launcher.game.account.yggdrasil.getSkinModel
import com.cmc.launcher.path.GLOBAL_CLIENT
import com.cmc.launcher.path.GLOBAL_JSON
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.network.httpPostJson
import com.cmc.launcher.utils.network.safeBodyAsJson
import com.cmc.launcher.utils.network.safeBodyAsText
import com.cmc.launcher.utils.network.submitForm
import com.cmc.launcher.utils.string.toUuidStr
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import io.ktor.client.request.forms.submitForm as submitFormRequest

private const val TAG = "MicrosoftAuth"

private val SCOPES = listOf("XboxLive.signin", "offline_access", "openid", "profile", "email")
private const val TENANT = "/consumers"

private const val MAX_CONSECUTIVE_POLL_FAILURES = 5

const val MICROSOFT_AUTH_URL = "https://login.microsoftonline.com"
const val LIVE_AUTH_URL = "https://login.live.com"
const val XBL_AUTH_URL = "https://user.auth.xboxlive.com"
const val XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com"
const val MINECRAFT_SERVICES_URL = "https://api.minecraftservices.com"

suspend fun fetchDeviceCodeResponse(context: CoroutineContext): DeviceCodeResponse = coroutineScope {
    withRetry {
        submitForm(
            url = "$MICROSOFT_AUTH_URL$TENANT/oauth2/v2.0/devicecode",
            parameters = Parameters.build {
                append("client_id", BuildKeys.OAUTH_CLIENT_ID)
                append("scope", SCOPES.joinToString(" "))
            },
            context = context
        )
    }
}

suspend fun getTokenResponse(
    codeResponse: DeviceCodeResponse,
    context: CoroutineContext,
    tokenUrl: String = "$MICROSOFT_AUTH_URL$TENANT/oauth2/v2.0/token",
    checkCancelled: suspend (time: Int) -> Boolean
): TokenResponse = coroutineScope {
    var pollingInterval = codeResponse.interval * 1000L
    val expireTime = System.currentTimeMillis() + codeResponse.expiresIn * 1000L

    var cancelled = 0
    suspend fun checkIsReallyCancelled(): Boolean {
        if (checkCancelled(cancelled)) cancelled++
        return cancelled > 1
    }

    var consecutiveFailures = 0

    Logger.debug(TAG, "Polling for token, interval = ${pollingInterval}ms, expires in ${codeResponse.expiresIn}s")

    while (System.currentTimeMillis() < expireTime) {
        context.ensureActive()

        try {
            val (httpResponse, responseBody) = withContext(context) {
                val response = GLOBAL_CLIENT.submitFormRequest(
                    url = tokenUrl,
                    formParameters = Parameters.build {
                        append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                        append("device_code", codeResponse.deviceCode)
                        append("client_id", BuildKeys.OAUTH_CLIENT_ID)
                        append("tenant", TENANT)
                    }
                ) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    expectSuccess = false
                }
                response to response.safeBodyAsText()
            }
            val response = GLOBAL_JSON.decodeFromString<JsonObject>(responseBody)
            consecutiveFailures = 0

            when (val error = response["error"].text()) {
                "authorization_pending" -> Unit
                "slow_down" -> {
                    pollingInterval += 1000L
                    Logger.debug(TAG, "Slowing down polling to ${pollingInterval}ms")
                }
                else -> {
                    if (response["token_type"]?.jsonPrimitive?.content == "Bearer") {
                        Logger.debug(TAG, "Access token successfully retrieved")
                        return@coroutineScope TokenResponse(
                            accessToken = response["access_token"].text(),
                            refreshToken = response["refresh_token"].text(),
                            expiresIn = response["expires_in"]?.jsonPrimitive?.int ?: 0
                        )
                    }
                    when (httpResponse.status.value) {
                        in 400..499 -> {
                            val exception = ClientRequestException(httpResponse, responseBody)
                            Logger.error(TAG, "Token endpoint rejected the polling request: error = $error", exception)
                            throw exception
                        }
                        !in 200..299 -> throw ServerResponseException(httpResponse, responseBody)
                    }
                    Logger.warning(TAG, "Token endpoint responded without a Bearer token, continuing to poll")
                }
            }
        } catch (e: ClientRequestException) {
            throw e
        } catch (e: CancellationException) {
            Logger.debug(TAG, "Authentication cancelled")
            throw e
        } catch (e: Exception) {

            consecutiveFailures++
            if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES ||
                System.currentTimeMillis() + pollingInterval >= expireTime
            ) {
                Logger.error(TAG, "Polling failed $consecutiveFailures time(s) in a row, giving up", e)
                throw e
            }
            Logger.warning(TAG, "Polling failed, will retry in ${pollingInterval}ms: ${e::class.simpleName}: ${e.message}")
        }

        if (checkIsReallyCancelled()) {
            Logger.debug(TAG, "The user left the web page, cancelling the authentication")
            throw CancellationException("Authentication cancelled")
        }

        delay(pollingInterval.milliseconds).also {
            context.ensureActive()
        }
    }
    Logger.warning(TAG, "Device code expired before the user completed authorization")
    throw HttpRequestTimeoutException("Authentication timed out!", expireTime)
}

private suspend fun ClientRequestException.errorCode(): String? {
    return runCatching {
        response.safeBodyAsJson<JsonObject>()["error"]?.jsonPrimitive?.content
    }.getOrNull()
}

suspend fun microsoftAuthAsync(
    authType: AuthType,
    refreshToken: String,
    accessToken: String = "NULL",
    context: CoroutineContext,
    statusUpdate: (AsyncStatus) -> Unit,
): Account = coroutineScope {
    val (finalAccessToken, newRefreshToken) = when (authType) {
        AuthType.Refresh -> refreshAccessToken(refreshToken, statusUpdate, context)
        else -> Pair(accessToken, refreshToken)
    }

    Logger.debug(TAG, "Authenticating with Xbox Live (XBL)")
    val xblToken = authenticateXBL(finalAccessToken, statusUpdate)
    Logger.debug(TAG, "Authenticating with Xbox Secure Token Service (XSTS)")
    val xstsToken = authenticateXSTS(xblToken.first, xblToken.second, statusUpdate, context)
    Logger.debug(TAG, "Authenticating with Minecraft services")
    val authResponse = authenticateMinecraft(xstsToken, statusUpdate, context)
    Logger.debug(TAG, "Verifying Minecraft ownership")
    verifyGameOwnership(authResponse.accessToken, statusUpdate)

    return@coroutineScope createAccount(authResponse, newRefreshToken, xblToken.second, statusUpdate)
}

suspend fun validateAccessToken(account: Account): Boolean = try {
    getPlayerProfile(MINECRAFT_SERVICES_URL, account.accessToken)
    true
} catch (e: MinecraftProfileException) {
    false
} catch (e: ResponseException) {
    when (e.response.status.value) {
        401, 403 -> false
        else -> throw e
    }
}

private suspend fun refreshAccessToken(
    refreshToken: String,
    update: (AsyncStatus) -> Unit,
    context: CoroutineContext
): Pair<String, String> {
    update(AsyncStatus.GETTING_ACCESS_TOKEN)

    return withRetry {
        try {
            val response = submitForm<JsonObject>(
                url = "$LIVE_AUTH_URL/oauth20_token.srf",
                parameters = Parameters.build {
                    append("client_id", BuildKeys.OAUTH_CLIENT_ID)
                    append("refresh_token", refreshToken)
                    append("grant_type", "refresh_token")
                },
                context = context
            )
            Pair(
                response["access_token"].text(),
                response["refresh_token"]?.jsonPrimitive?.content ?: refreshToken
            )
        } catch (e: ClientRequestException) {

            if (e.errorCode() == "invalid_grant") throw CredentialsExpiredException()
            throw e
        }
    }
}

private suspend fun authenticateXBL(accessToken: String, update: (AsyncStatus) -> Unit): Pair<String, String> {
    update(AsyncStatus.GETTING_XBL_TOKEN)

    suspend fun requestXblToken(rpsTicket: String): Pair<String, String> {
        val requestBody = XBLRequest(
            properties = XBLProperties(
                authMethod = "RPS",
                siteName = "user.auth.xboxlive.com",
                rpsTicket = rpsTicket
            ),
            relyingParty = "http://auth.xboxlive.com",
            tokenType = "JWT"
        )

        val response = GLOBAL_CLIENT.post("$XBL_AUTH_URL/user/authenticate") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.safeBodyAsJson<JsonObject>()

        val uhs = response["DisplayClaims"]?.jsonObject
            ?.get("xui")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("uhs")?.jsonPrimitive
            ?.content ?: throw Exception("Missing uhs in XBL response")

        return Pair(response["Token"].text(), uhs)
    }

    return withRetry {
        try {
            requestXblToken("d=$accessToken")
        } catch (e: ClientRequestException) {

            if (e.response.status.value == 400) {
                Logger.warning(TAG, "XBL authentication rejected the d= prefixed RpsTicket, retrying without the prefix")
                requestXblToken(accessToken)
            } else throw e
        }
    }
}

private suspend fun authenticateXSTS(
    xblToken: String,
    uhs: String,
    update: (AsyncStatus) -> Unit,
    context: CoroutineContext
): XSTSAuthResult {
    update(AsyncStatus.GETTING_XSTS_TOKEN)

    return withRetry {
        try {
            val response = httpPostJson<JsonObject>(
                url = "$XSTS_AUTH_URL/xsts/authorize",
                body = XSTSRequest(
                    properties = XSTSProperties(
                        sandboxId = "RETAIL",
                        userTokens = listOf(xblToken)
                    ),
                    relyingParty = "rp://api.minecraftservices.com/",
                    tokenType = "JWT"
                ),
                context = context
            )

            XSTSAuthResult(token = response["Token"].text(), uhs = uhs)
        } catch (e: ClientRequestException) {

            val errorBody = runCatching { e.response.safeBodyAsJson<JsonObject>() }.getOrNull()
            when (val xErr = errorBody?.get("XErr").text()) {

                "2148916227" -> throw XboxLoginException(BANNED)
                "2148916229" -> throw XboxLoginException(RESTRICTED)
                "2148916233" -> throw XboxLoginException(UNREGISTERED)
                "2148916234" -> throw XboxLoginException(NOT_ACCEPTED_SERVICE)
                "2148916235" -> throw XboxLoginException(BLOCKED_REGION)
                "2148916236" -> throw XboxLoginException(REQUIRES_PROOF_OF_AGE)
                "2148916237" -> throw XboxLoginException(REACHED_PLAYTIME_LIMIT)
                "2148916238" -> throw XboxLoginException(UNDERAGE)
                else -> {
                    Logger.error(
                        TAG,
                        "XSTS authentication failed: status = ${e.response.status}, XErr = $xErr, message = ${errorBody?.get("Message").text()}"
                    )
                    throw e
                }
            }
        }
    }
}

private suspend fun authenticateMinecraft(
    xstsResult: XSTSAuthResult,
    update: (AsyncStatus) -> Unit,
    context: CoroutineContext
): MinecraftAuthResponse {
    update(AsyncStatus.AUTHENTICATE_MINECRAFT)

    return withRetry {
        runCatching {
            httpPostJson<MinecraftAuthResponse>(
                url = "$MINECRAFT_SERVICES_URL/authentication/login_with_xbox",
                body = mapOf("identityToken" to "XBL3.0 x=${xstsResult.uhs};${xstsResult.token}"),
                context = context
            )
        }.onFailure { e ->
            if (e is ResponseException) {
                when (e.response.status.value) {
                    429 -> throw MinecraftProfileException(FREQUENT)
                    403 -> throw MinecraftProfileException(BLOCKED_IP)
                }
            }
        }.getOrThrow()
    }
}

private suspend fun verifyGameOwnership(accessToken: String, update: (AsyncStatus) -> Unit) {
    update(AsyncStatus.VERIFY_GAME_OWNERSHIP)
    withRetry {
        val response = GLOBAL_CLIENT.get("$MINECRAFT_SERVICES_URL/entitlements/mcstore") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.safeBodyAsJson<JsonObject>()["items"]?.jsonArray?.isEmpty() != false) {
            throw NotPurchasedMinecraftException()
        }
    }
}

private suspend fun createAccount(
    authResponse: MinecraftAuthResponse,
    refreshToken: String,
    uhs: String,
    statusUpdate: (AsyncStatus) -> Unit
): Account {
    statusUpdate(AsyncStatus.GETTING_PLAYER_PROFILE)

    val profile = getPlayerProfile(
        apiUrl = MINECRAFT_SERVICES_URL,
        accessToken = authResponse.accessToken
    )

    val profileId = profile.id

    val account = AccountsManager.loadFromProfileID(profileId, AccountType.MICROSOFT.tag) ?: Account()

    return account.apply {
        this.username = profile.name
        this.accessToken = authResponse.accessToken
        this.expiresAt = System.currentTimeMillis() + authResponse.expiresIn * 1000
        this.accountType = AccountType.MICROSOFT.tag
        this.clientToken = BuildKeys.LAUNCHER_NAME.toUuidStr().replace("-", "")
        this.profileId = profileId
        this.refreshToken = refreshToken.ifEmpty { "None" }
        this.xUid = uhs
        this.skinModelType = profile.skins.findUsing()?.getSkinModel() ?: SkinModelType.NONE
    }
}

private fun JsonElement?.text() = this?.jsonPrimitive?.content.orEmpty()

private suspend fun <T> withRetry(
    maxRetries: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10_000,
    block: suspend () -> T
): T = com.cmc.launcher.utils.network.withRetry(
    "MicrosoftAuthenticator", maxRetries, initialDelay, maxDelay, block
)
