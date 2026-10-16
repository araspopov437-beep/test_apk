package com.example.shorts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

const val SUPABASE_URL = "https://erhpojkijlvejwfjohyd.supabase.co"
const val SUPABASE_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVyaHBvamtpamx2ZWp3ZmpvaHlkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg4OTEzNzMsImV4cCI6MjEwNDQ2NzM3M30.kdK2UpBPnbkw2aQ8z-7LBq3-c7Vp6txC4QLByaJXYYM"

class ApiException(message: String) : Exception(message)

/**
 * Минимальный клиент Supabase поверх REST: Auth (GoTrue), PostgREST и Storage.
 * Сессия хранится в SharedPreferences, токен обновляется автоматически при 401.
 */
class Api(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("shorts_session", Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @Volatile var accessToken: String? = prefs.getString("access", null); private set
    @Volatile var refreshToken: String? = prefs.getString("refresh", null); private set
    @Volatile var userId: String? = prefs.getString("uid", null); private set

    private class Raw(val code: Int, val body: String)

    // ------------------------------------------------------------ низкий уровень

    private fun Request.Builder.withAuth(token: String?): Request.Builder = apply {
        header("apikey", SUPABASE_ANON_KEY)
        header("Authorization", "Bearer " + (token ?: SUPABASE_ANON_KEY))
    }

    private fun doCall(req: Request): Raw =
        http.newCall(req).execute().use { Raw(it.code, it.body?.string().orEmpty()) }

    private fun rawCall(build: (String?) -> Request): Raw {
        var r = doCall(build(accessToken))
        if (r.code == 401 && !refreshToken.isNullOrEmpty() && refreshBlocking()) {
            r = doCall(build(accessToken))
        }
        return r
    }

    private fun errorMessage(body: String, code: Int): String {
        try {
            val j = JSONObject(body)
            for (k in listOf("msg", "message", "error_description", "error")) {
                val v = j.optString(k, "")
                if (v.isNotEmpty()) return v
            }
        } catch (_: Exception) {
        }
        return "Ошибка сервера ($code)"
    }

    private fun check(raw: Raw) {
        if (raw.code !in 200..299) throw ApiException(errorMessage(raw.body, raw.code))
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: IOException) {
            throw ApiException("Нет соединения с интернетом")
        }
    }

    // ------------------------------------------------------------ сессия

    private fun saveSession(json: JSONObject) {
        val at = json.optString("access_token", "")
        if (at.isEmpty()) return
        accessToken = at
        val rt = json.optString("refresh_token", "")
        if (rt.isNotEmpty()) refreshToken = rt
        json.optJSONObject("user")?.optString("id", "")?.takeIf { it.isNotEmpty() }?.let { userId = it }
        prefs.edit()
            .putString("access", accessToken)
            .putString("refresh", refreshToken)
            .putString("uid", userId)
            .apply()
    }

    fun clearSession() {
        accessToken = null
        refreshToken = null
        userId = null
        prefs.edit().clear().apply()
    }

    @Synchronized
    private fun refreshBlocking(): Boolean {
        val rt = refreshToken ?: return false
        return try {
            val body = JSONObject().put("refresh_token", rt).toString().toRequestBody(jsonType)
            val raw = doCall(
                Request.Builder()
                    .url("$SUPABASE_URL/auth/v1/token?grant_type=refresh_token")
                    .withAuth(null)
                    .post(body)
                    .build()
            )
            if (raw.code in 200..299) {
                saveSession(JSONObject(raw.body)); true
            } else {
                if (raw.code == 400 || raw.code == 401) clearSession()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------ авторизация

    /** true — сессия создана сразу; false — нужно подтвердить email. */
    suspend fun signUp(email: String, password: String, displayName: String): Boolean = io {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("data", JSONObject().put("display_name", displayName))
        val raw = doCall(
            Request.Builder().url("$SUPABASE_URL/auth/v1/signup").withAuth(null)
                .post(body.toString().toRequestBody(jsonType)).build()
        )
        check(raw)
        val j = JSONObject(raw.body)
        if (j.has("access_token")) {
            saveSession(j); true
        } else false
    }

    suspend fun signIn(email: String, password: String) = io {
        val body = JSONObject().put("email", email).put("password", password)
        val raw = doCall(
            Request.Builder().url("$SUPABASE_URL/auth/v1/token?grant_type=password").withAuth(null)
                .post(body.toString().toRequestBody(jsonType)).build()
        )
        check(raw)
        saveSession(JSONObject(raw.body))
    }

    /** id текущего пользователя или null, если сессии нет / она недействительна. */
    suspend fun currentUserId(): String? = io {
        if (accessToken.isNullOrEmpty() && refreshToken.isNullOrEmpty()) return@io null
        val raw = rawCall { t ->
            Request.Builder().url("$SUPABASE_URL/auth/v1/user").withAuth(t).get().build()
        }
        when {
            raw.code in 200..299 -> JSONObject(raw.body).optString("id", "").takeIf { it.isNotEmpty() }
                ?.also { userId = it; prefs.edit().putString("uid", it).apply() }
            raw.code == 401 || raw.code == 403 -> {
                clearSession(); null
            }
            else -> throw ApiException(errorMessage(raw.body, raw.code))
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            doCall(
                Request.Builder().url("$SUPABASE_URL/auth/v1/logout").withAuth(accessToken)
                    .post("{}".toRequestBody(jsonType)).build()
            )
        } catch (_: Exception) {
        }
        clearSession()
    }

    // ------------------------------------------------------------ PostgREST

    private fun restUrl(table: String, params: List<Pair<String, String>>): HttpUrl =
        "$SUPABASE_URL/rest/v1/$table".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

    suspend fun select(table: String, vararg params: Pair<String, String>): JSONArray = io {
        val list = params.toList()
        val raw = rawCall { t ->
            Request.Builder().url(restUrl(table, list)).withAuth(t).get().build()
        }
        check(raw)
        JSONArray(raw.body)
    }

    suspend fun insert(table: String, data: JSONObject) = io {
        val raw = rawCall { t ->
            Request.Builder().url(restUrl(table, emptyList())).withAuth(t)
                .header("Prefer", "return=minimal")
                .post(data.toString().toRequestBody(jsonType)).build()
        }
        check(raw)
    }

    suspend fun update(table: String, filters: List<Pair<String, String>>, data: JSONObject) = io {
        val raw = rawCall { t ->
            Request.Builder().url(restUrl(table, filters)).withAuth(t)
                .header("Prefer", "return=minimal")
                .patch(data.toString().toRequestBody(jsonType)).build()
        }
        check(raw)
    }

    suspend fun delete(table: String, vararg filters: Pair<String, String>) = io {
        val list = filters.toList()
        val raw = rawCall { t ->
            Request.Builder().url(restUrl(table, list)).withAuth(t)
                .header("Prefer", "return=minimal").delete().build()
        }
        check(raw)
    }

    // ------------------------------------------------------------ Storage (бакет videos)

    fun publicUrl(path: String) = "$SUPABASE_URL/storage/v1/object/public/videos/$path"

    suspend fun upload(path: String, file: File, contentType: String): String = io {
        val type = try {
            contentType.toMediaType()
        } catch (_: Exception) {
            "application/octet-stream".toMediaType()
        }
        val raw = rawCall { t ->
            Request.Builder().url("$SUPABASE_URL/storage/v1/object/videos/$path").withAuth(t)
                .header("x-upsert", "false")
                .header("cache-control", "max-age=3600")
                .post(file.asRequestBody(type)).build()
        }
        check(raw)
        publicUrl(path)
    }

    suspend fun removeObjects(paths: List<String>) = io {
        val body = JSONObject().put("prefixes", JSONArray(paths)).toString().toRequestBody(jsonType)
        val raw = rawCall { t ->
            Request.Builder().url("$SUPABASE_URL/storage/v1/object/videos").withAuth(t)
                .delete(body).build()
        }
        check(raw)
    }
}
