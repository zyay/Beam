package com.beammental.app.data

import com.beammental.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class ChatMessage(val role: String, val content: String)

data class AuthResult(val ok: Boolean, val hasProfile: Boolean, val error: String?)
data class ChatResult(val text: String?, val crisis: Boolean, val error: String?)

/** Thin REST client for the Beam backend (Vercel). Bearer token auth. */
class Api(private val session: Session) {

    private val base = BuildConfig.BEAM_URL.trimEnd('/') + "/"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS)
        .build()

    private suspend fun post(path: String, body: JsonObject, authed: Boolean = false): Pair<Int, JsonObject?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val b = RequestBody.create("application/json".toMediaType(), body.toString())
                val rb = Request.Builder().url(base + path).post(b)
                if (authed) rb.header("Authorization", "Bearer ${session.token().orEmpty()}")
                client.newCall(rb.build()).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val obj = runCatching {
                        json.parseToJsonElement(text).jsonObject
                    }.getOrNull()
                    resp.code to obj
                }
            }.getOrElse { 0 to null }
        }

    private suspend fun put(path: String, body: JsonObject): Pair<Int, JsonObject?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val b = RequestBody.create("application/json".toMediaType(), body.toString())
                val rb = Request.Builder().url(base + path).put(b)
                    .header("Authorization", "Bearer ${session.token().orEmpty()}")
                client.newCall(rb.build()).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val obj = runCatching {
                        json.parseToJsonElement(text).jsonObject
                    }.getOrNull()
                    resp.code to obj
                }
            }.getOrElse { 0 to null }
        }

    private suspend fun get(path: String): Pair<Int, JsonObject?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rb = Request.Builder().url(base + path)
                    .header("Authorization", "Bearer ${session.token().orEmpty()}")
                client.newCall(rb.build()).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                    resp.code to obj
                }
            }.getOrElse { 0 to null }
        }

    private fun JsonObject?.err(): String? =
        this?.get("error")?.jsonPrimitive?.contentOrNull

    suspend fun signup(email: String, password: String, name: String?): AuthResult {
        val (code, obj) = post("api/auth/signup", buildJsonObject {
            put("email", email)
            put("password", password)
            name?.let { put("name", it) }
        })
        val token = obj?.get("token")?.jsonPrimitive?.contentOrNull
        if (code == 200 && token != null) {
            session.saveAuth(token, name)
            return AuthResult(true, obj?.get("hasProfile")?.jsonPrimitive?.booleanOrNull == true, null)
        }
        return AuthResult(false, false, obj.err() ?: connError(code))
    }

    suspend fun login(email: String, password: String): AuthResult {
        val (code, obj) = post("api/auth/login", buildJsonObject {
            put("email", email)
            put("password", password)
        })
        val token = obj?.get("token")?.jsonPrimitive?.contentOrNull
        if (code == 200 && token != null) {
            val hasProfile = obj?.get("hasProfile")?.jsonPrimitive?.booleanOrNull == true
            session.saveAuth(token, null)
            // existing account already went through onboarding — don't ask again
            if (hasProfile) session.setOnboarded()
            return AuthResult(true, hasProfile, null)
        }
        return AuthResult(false, false, obj.err() ?: connError(code))
    }

    /** Signup/login currently set the session via httpOnly cookie on web —
     * for the native app the server also returns the JWT in the body. */
    suspend fun saveProfile(profile: JsonObject, name: String?): Boolean {
        val body = buildJsonObject {
            put("profile", profile)
            name?.let { put("name", it) }
        }
        val (code, _) = put("api/profile", body)
        if (code == 200) {
            name?.let { session.setName(it) }
            session.setOnboarded()
            return true
        }
        return false
    }

    /** Sync the display name from the server (fresh install / new device). */
    suspend fun fetchProfileName(): String? {
        val (code, obj) = get("api/profile")
        if (code != 200 || obj == null) return null
        return obj["name"]?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    /** Settings: update the name while keeping the onboarding profile intact. */
    suspend fun saveName(name: String): Boolean {
        val (codeGet, cur) = get("api/profile")
        if (codeGet != 200 || cur == null) return false
        val profile = cur["profile"]?.jsonObject ?: buildJsonObject { }
        val body = buildJsonObject {
            put("profile", profile)
            put("name", name)
        }
        val (code, _) = put("api/profile", body)
        if (code == 200) {
            session.setName(name)
            session.setOnboarded()
            return true
        }
        return false
    }

    suspend fun chat(history: List<ChatMessage>): ChatResult {
        val body = buildJsonObject {
            put("messages", kotlinx.serialization.json.JsonArray(
                history.map { m -> buildJsonObject {
                    put("role", m.role)
                    put("content", m.content)
                } }
            ))
        }
        val (code, obj) = post("api/chat", body, authed = true)
        if (code == 200 && obj != null) {
            val text = obj["text"]?.jsonPrimitive?.contentOrNull
            val crisis = obj["crisis"]?.jsonPrimitive?.booleanOrNull == true
            return ChatResult(text, crisis, null)
        }
        val err = obj.err() ?: when (code) {
            0 -> "Nepodarilo sa pripojiť k serveru. Skús to o chvíľu."
            502, 504 -> "Chat služba je preťažená. Skús to o chvíľu."
            else -> "Server neodpovedá ($code)."
        }
        return ChatResult(null, false, err)
    }

    /** Streaming chat: SSE deltas via [onDelta] (called on IO thread).
     * Errors and the crisis protocol still arrive as plain JSON. */
    suspend fun chatStream(history: List<ChatMessage>, onDelta: (String) -> Unit): ChatResult =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("stream", true)
                put("messages", kotlinx.serialization.json.JsonArray(
                    history.map { m -> buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    } }
                ))
            }
            val acc = StringBuilder()
            val outcome = runCatching {
                val rb = Request.Builder()
                    .url(base + "api/chat")
                    .header("Authorization", "Bearer ${session.token().orEmpty()}")
                    .post(RequestBody.create("application/json".toMediaType(), body.toString()))
                    .build()
                client.newCall(rb).execute().use { resp ->
                    val ctype = resp.header("Content-Type").orEmpty()
                    if (resp.code != 200 || "application/json" in ctype) {
                        val text = resp.body?.string().orEmpty()
                        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                        return@use if (resp.code == 200 && obj != null) {
                            ChatResult(
                                obj["text"]?.jsonPrimitive?.contentOrNull,
                                obj["crisis"]?.jsonPrimitive?.booleanOrNull == true,
                                null,
                            )
                        } else {
                            ChatResult(null, false, obj.err() ?: when (resp.code) {
                                502, 504 -> "Chat služba je preťažená. Skús to o chvíľu."
                                else -> "Server neodpovedá (${resp.code})."
                            })
                        }
                    }
                    resp.body?.byteStream()?.bufferedReader(Charsets.UTF_8)?.let { br ->
                        while (true) {
                            val line = br.readLine() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") break
                            val chunk = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                            val delta = chunk["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                                ?.get("delta")?.jsonObject
                                ?.get("content")?.jsonPrimitive?.contentOrNull
                            if (!delta.isNullOrEmpty()) {
                                acc.append(delta)
                                onDelta(delta)
                            }
                        }
                    }
                    val full = acc.toString().trim()
                    if (full.isEmpty()) {
                        ChatResult(null, false, "Chat služba neodpovedala. Skús to znova.")
                    } else {
                        ChatResult(full, false, null)
                    }
                }
            }.getOrNull()
            // mid-stream network failure with partial text: keep what arrived
            outcome ?: if (acc.isNotBlank()) {
                ChatResult(acc.toString().trim(), false, null)
            } else {
                ChatResult(null, false, "Nepodarilo sa pripojiť k serveru. Skús to o chvíľu.")
            }
        }

    private fun connError(code: Int): String =
        if (code == 0) "Nepodarilo sa pripojiť k serveru. Skús to o chvíľu."
        else "Server neodpovedá ($code)."

    companion object {
        fun crisisLike(text: String): Boolean {
            val normalized = text
                .normalizeDiacritics()
                .lowercase()
            return Regex(
                "(nechcem\\s+(uz\\s+)?zit|nemam\\s+(chut|silu)\\s+zi(t|t)|skoncit\\s+(so zivo|to)|zabit\\s+sa|sebavraz\\w*|suicid\\w*|zomrie(t|t)|umrie(t|t)|prehltn\\w*\\s+(table|pilul)|chcem\\s+zomrie|kill\\s+myself|end\\s+my\\s+life|want\\s+to\\s+die)"
            ).containsMatchIn(normalized)
        }

        private fun String.normalizeDiacritics(): String {
            val Form = java.text.Normalizer.Form.NFD
            return java.text.Normalizer.normalize(this, Form).replace(Regex("\\p{M}+"), "")
        }
    }
}
