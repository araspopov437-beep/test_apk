package com.example.shorts

import org.json.JSONArray
import org.json.JSONObject

/** Короткий профиль (автор, комментатор, репостнувший). */
data class MiniProfile(
    val id: String?,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String?
)

data class Profile(
    val id: String,
    val username: String?,
    val displayName: String?,
    val bio: String?,
    val avatarUrl: String?,
    val followers: Int,
    val following: Int,
    val likes: Int
) {
    fun mini() = MiniProfile(id, username, displayName, avatarUrl)
}

data class Video(
    val id: String,
    val userId: String,
    val videoUrl: String,
    val thumbnailUrl: String?,
    val title: String?,
    val caption: String?,
    val hashtags: List<String>,
    val music: String?,
    val likesCount: Int,
    val commentsCount: Int,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val repostedBy: List<MiniProfile> = emptyList()
) {
    fun author() = MiniProfile(userId, username, displayName, avatarUrl)
}

data class Comment(val id: String, val text: String, val profile: MiniProfile?)

data class Notice(
    val id: String,
    val actorId: String?,
    val message: String,
    val profile: MiniProfile?,
    val type: String? = null
)

// ---------- JSON helpers ----------

fun JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) optString(key) else null

fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONObject.toMini(): MiniProfile =
    MiniProfile(str("id"), str("username"), str("display_name"), str("avatar_url"))

fun JSONObject.toProfile(): Profile = Profile(
    id = optString("id"),
    username = str("username"),
    displayName = str("display_name"),
    bio = str("bio"),
    avatarUrl = str("avatar_url"),
    followers = optInt("followers_count", 0),
    following = optInt("following_count", 0),
    likes = optInt("likes_count", 0)
)

fun JSONObject.toVideo(): Video {
    val tags = ArrayList<String>()
    optJSONArray("hashtags")?.let { arr ->
        for (i in 0 until arr.length()) {
            val t = arr.optString(i, "")
            if (t.isNotEmpty()) tags.add(t)
        }
    }
    return Video(
        id = optString("id"),
        userId = optString("user_id"),
        videoUrl = optString("video_url"),
        thumbnailUrl = str("thumbnail_url")?.takeIf { it.isNotBlank() },
        title = str("title"),
        caption = str("caption"),
        hashtags = tags,
        music = str("music"),
        likesCount = optInt("likes_count", 0),
        commentsCount = optInt("comments_count", 0),
        username = str("username"),
        displayName = str("display_name"),
        avatarUrl = str("avatar_url")
    )
}

// ---------- Отображение имён (как в оригинале: почту никогда не показываем) ----------

fun nick(username: String?, displayName: String?): String {
    var value = (username ?: displayName ?: "").trim()
    if (value.contains("@")) value = value.substringBefore("@")
    return value.ifEmpty { "user" }
}

fun shownName(displayName: String?, username: String?): String {
    var value = (displayName ?: username ?: "").trim()
    if (value.contains("@") && value.contains(".")) value = value.substringBefore("@")
    return value.ifEmpty { "User" }
}

fun MiniProfile?.nick() = nick(this?.username, this?.displayName)
fun MiniProfile?.name() = shownName(this?.displayName, this?.username)
fun Video.nick() = nick(username, displayName)
fun Video.authorName() = shownName(displayName, username)
fun Profile.nick() = nick(username, displayName)
fun Profile.name() = shownName(displayName, username)

fun avatarFallback(name: String): String =
    "https://ui-avatars.com/api/?name=" + java.net.URLEncoder.encode(name.ifEmpty { "User" }, "UTF-8") +
        "&background=222&color=fff&size=200"

fun formatNumber(n: Int): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f", n / 1_000_000.0).replace(".0", "") + "M"
    n >= 1_000 -> String.format(java.util.Locale.US, "%.1f", n / 1_000.0).replace(".0", "") + "K"
    else -> n.toString()
}

/** Хэштег: без #, пробелов и спецсимволов, нижний регистр, до 30 символов. */
fun normalizeTag(value: String): String =
    value.trim()
        .trimStart('#')
        .replace(Regex("\\s+"), "")
        .replace(Regex("[^\\p{L}\\p{N}_]"), "")
        .lowercase()
        .take(30)
