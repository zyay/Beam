package com.beammental.app.data

import com.beammental.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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
        }

    private suspend fun put(path: String, body: JsonObject): Pair<Int, JsonObject?> =
        withContext(Dispatchers.IO) {
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
        }

    private suspend fun get(path: String): Pair<Int, JsonObject?> =
        withContext(Dispatchers.IO) {
            val rb = Request.Builder().url(base + path)
                .header("Authorization", "Bearer ${session.token().orEmpty()}")
            client.newCall(rb.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                resp.code to obj
            }
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
            return AuthResult(true, obj?.get("hasProfile")?.jsonPrimitive?.contentOrNull == "true", null)
        }
        return AuthResult(false, false, obj.err() ?: "Server neodpovedá ($code).")
    }

    suspend fun login(email: String, password: String): AuthResult {
        val (code, obj) = post("api/auth/login", buildJsonObject {
            put("email", email)
            put("password", password)
        })
        val token = obj?.get("token")?.jsonPrimitive?.contentOrNull
        if (code == 200 && token != null) {
            session.saveAuth(token, null)
            return AuthResult(true, obj?.get("hasProfile")?.jsonPrimitive?.contentOrNull == "true", null)
        }
        return AuthResult(false, false, obj.err() ?: "Server neodpovedá ($code).")
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
            val crisis = obj["crisis"]?.jsonPrimitive?.contentOrNull == "true"
            return ChatResult(text, crisis, null)
        }
        val err = obj.err() ?: when (code) {
            504, 502 -> "Chat služba je preťažená. Skús to o chvíľu."
            else -> "Server neodpovedá ($code)."
        }
        return ChatResult(null, false, err)
    }

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
