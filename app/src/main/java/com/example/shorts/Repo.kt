package com.example.shorts

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private fun inList(ids: Collection<String>) = "in.(" + ids.joinToString(",") + ")"

class Repo(val api: Api) {

    // ---------------------------------------------------------------- профили

    suspend fun getProfile(id: String): Profile {
        val rows = api.select("profiles", "select" to "*", "id" to "eq.$id", "limit" to "1")
        val o = rows.optJSONObject(0) ?: throw ApiException("Профиль не найден")
        return o.toProfile()
    }

    suspend fun isUsernameTaken(username: String, exceptId: String): Boolean =
        api.select(
            "profiles", "select" to "id",
            "username" to "eq.$username", "id" to "neq.$exceptId", "limit" to "1"
        ).length() > 0

    suspend fun updateProfile(id: String, fields: JSONObject) =
        api.update("profiles", listOf("id" to "eq.$id"), fields)

    suspend fun searchUsers(q: String): List<Profile> {
        val term = q.replace(Regex("[,()]"), " ")
        return api.select(
            "profiles", "select" to "*",
            "or" to "(username.ilike.%$term%,display_name.ilike.%$term%)",
            "limit" to "30"
        ).objects().map { it.toProfile() }
    }

    // ---------------------------------------------------------------- подписки

    suspend fun followingIds(userId: String): Set<String> =
        api.select("follows", "select" to "following_id", "follower_id" to "eq.$userId")
            .objects().mapNotNull { it.str("following_id") }.toSet()

    suspend fun followerIds(userId: String): Set<String> =
        api.select("follows", "select" to "follower_id", "following_id" to "eq.$userId")
            .objects().mapNotNull { it.str("follower_id") }.toSet()

    suspend fun follow(me: String, target: String) =
        api.insert("follows", JSONObject().put("follower_id", me).put("following_id", target))

    suspend fun unfollow(me: String, target: String) =
        api.delete("follows", "follower_id" to "eq.$me", "following_id" to "eq.$target")

    // ---------------------------------------------------------------- видео

    suspend fun feedVideos(onlyUsers: Collection<String>?): List<Video> {
        val params = ArrayList<Pair<String, String>>()
        params += "select" to "*"
        params += "order" to "created_at.desc"
        params += "limit" to "30"
        if (onlyUsers != null) params += "user_id" to inList(onlyUsers)
        return api.select("feed_videos", *params.toTypedArray()).objects().map { it.toVideo() }
    }

    suspend fun videosByIds(ids: Collection<String>): List<Video> {
        if (ids.isEmpty()) return emptyList()
        return api.select("feed_videos", "select" to "*", "id" to inList(ids))
            .objects().map { it.toVideo() }
    }

    /** Репосты друзей, сгруппированные по видео (одно видео — одна карточка). */
    suspend fun friendReposts(friendIds: Collection<String>): List<Video> {
        if (friendIds.isEmpty()) return emptyList()
        val rows = api.select(
            "reposts",
            "select" to "video_id,created_at,profiles:user_id(id,username,display_name,avatar_url)",
            "user_id" to inList(friendIds),
            "order" to "created_at.desc",
            "limit" to "20"
        ).objects()
        if (rows.isEmpty()) return emptyList()
        val byId = videosByIds(rows.mapNotNull { it.str("video_id") }).associateBy { it.id }
        val grouped = LinkedHashMap<String, Video>()
        for (r in rows) {
            val vid = r.str("video_id") ?: continue
            val video = byId[vid] ?: continue
            val prof = r.optJSONObject("profiles")?.toMini() ?: continue
            val existing = grouped[vid]
            if (existing == null) {
                grouped[vid] = video.copy(repostedBy = listOf(prof))
            } else if (existing.repostedBy.none { it.id == prof.id }) {
                grouped[vid] = existing.copy(repostedBy = existing.repostedBy + prof)
            }
        }
        return grouped.values.toList()
    }

    suspend fun userVideos(userId: String): List<Video> =
        api.select(
            "feed_videos", "select" to "*",
            "user_id" to "eq.$userId", "order" to "created_at.desc"
        ).objects().map { it.toVideo() }

    /** table: reposts / video_likes / bookmarks — порядок добавления сохраняется. */
    suspend fun videosByJoin(table: String, userId: String): List<Video> {
        val rows = api.select(
            table, "select" to "video_id,created_at",
            "user_id" to "eq.$userId", "order" to "created_at.desc", "limit" to "100"
        ).objects()
        if (rows.isEmpty()) return emptyList()
        val byId = videosByIds(rows.mapNotNull { it.str("video_id") }).associateBy { it.id }
        return rows.mapNotNull { byId[it.str("video_id")] }
    }

    suspend fun searchVideos(q: String): List<Video> {
        val term = q.trimStart('#').trim().replace(Regex("[,()]"), " ")
        val result = ArrayList<Video>()
        val seen = HashSet<String>()
        api.select(
            "feed_videos", "select" to "*",
            "or" to "(title.ilike.%$term%,caption.ilike.%$term%,username.ilike.%$term%)",
            "limit" to "60"
        ).objects().map { it.toVideo() }.forEach { if (seen.add(it.id)) result += it }
        try {
            api.select(
                "feed_videos", "select" to "*",
                "hashtags" to "cs.{${term.lowercase()}}", "limit" to "60"
            ).objects().map { it.toVideo() }.forEach { if (seen.add(it.id)) result += it }
        } catch (_: Exception) {
        }
        return result
    }

    suspend fun insertVideo(
        userId: String, videoUrl: String, thumbnailUrl: String?,
        title: String, caption: String, hashtags: List<String>
    ) {
        val o = JSONObject()
            .put("user_id", userId)
            .put("video_url", videoUrl)
            .put("thumbnail_url", thumbnailUrl ?: JSONObject.NULL)
            .put("title", title)
            .put("caption", caption)
            .put("hashtags", JSONArray(hashtags))
            .put("music", "Original sound")
        api.insert("videos", o)
    }

    suspend fun deleteVideo(video: Video, userId: String) {
        api.delete("videos", "id" to "eq.${video.id}", "user_id" to "eq.$userId")
        val marker = "/videos/"
        val idx = video.videoUrl.indexOf(marker)
        if (idx >= 0) {
            val path = java.net.URLDecoder.decode(video.videoUrl.substring(idx + marker.length), "UTF-8")
            try {
                api.removeObjects(listOf(path))
            } catch (_: Exception) {
            }
        }
    }

    suspend fun uploadFile(userId: String, folder: String?, file: File, ext: String, type: String): String {
        val name = UUID.randomUUID().toString() + "." + ext
        val path = if (folder == null) "$userId/$name" else "$userId/$folder/$name"
        return api.upload(path, file, type)
    }

    // ---------------------------------------------------------------- лайки / закладки / репосты

    suspend fun likedAmong(userId: String, ids: Collection<String>): Set<String> =
        marked("video_likes", userId, ids)

    suspend fun savedAmong(userId: String, ids: Collection<String>): Set<String> =
        marked("bookmarks", userId, ids)

    private suspend fun marked(table: String, userId: String, ids: Collection<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        return api.select(
            table, "select" to "video_id",
            "user_id" to "eq.$userId", "video_id" to inList(ids)
        ).objects().mapNotNull { it.str("video_id") }.toSet()
    }

    suspend fun setLike(userId: String, videoId: String, on: Boolean) = toggleRow("video_likes", userId, videoId, on)
    suspend fun setSaved(userId: String, videoId: String, on: Boolean) = toggleRow("bookmarks", userId, videoId, on)
    suspend fun setRepost(userId: String, videoId: String, on: Boolean) = toggleRow("reposts", userId, videoId, on)

    private suspend fun toggleRow(table: String, userId: String, videoId: String, on: Boolean) {
        if (on) api.insert(table, JSONObject().put("user_id", userId).put("video_id", videoId))
        else api.delete(table, "user_id" to "eq.$userId", "video_id" to "eq.$videoId")
    }

    suspend fun isReposted(userId: String, videoId: String): Boolean =
        api.select(
            "reposts", "select" to "video_id",
            "user_id" to "eq.$userId", "video_id" to "eq.$videoId", "limit" to "1"
        ).length() > 0

    // ---------------------------------------------------------------- комментарии

    suspend fun comments(videoId: String): List<Comment> =
        api.select(
            "comments",
            "select" to "id,text,created_at,profiles(id,username,display_name,avatar_url)",
            "video_id" to "eq.$videoId", "order" to "created_at.asc"
        ).objects().map {
            Comment(it.optString("id"), it.optString("text"), it.optJSONObject("profiles")?.toMini())
        }

    suspend fun addComment(videoId: String, userId: String, text: String) =
        api.insert("comments", JSONObject().put("video_id", videoId).put("user_id", userId).put("text", text))

    // ---------------------------------------------------------------- уведомления

    suspend fun notices(userId: String): List<Notice> =
        api.select(
            "notifications",
            "select" to "*,profiles:actor_id(username,display_name,avatar_url)",
            "user_id" to "eq.$userId", "order" to "created_at.desc", "limit" to "50"
        ).objects().map {
            Notice(
                it.optString("id"), it.str("actor_id"), it.optString("message"),
                it.optJSONObject("profiles")?.toMini()
            )
        }

    suspend fun markNoticesRead(userId: String) =
        api.update(
            "notifications",
            listOf("user_id" to "eq.$userId", "is_read" to "eq.false"),
            JSONObject().put("is_read", true)
        )

    // ---------------------------------------------------------------- жалобы

    suspend fun report(videoId: String, reporterId: String, category: String, reason: String) =
        api.insert(
            "reports",
            JSONObject().put("video_id", videoId).put("reporter_id", reporterId)
                .put("category", category).put("reason", reason).put("status", "pending")
        )
}
