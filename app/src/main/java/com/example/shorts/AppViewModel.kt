package com.example.shorts

import android.app.Application
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class Tab { HOME, SEARCH, NOTIFICATIONS, PROFILE }
enum class FeedType { FOLLOWING, FORYOU }
enum class ProfileTab { VIDEOS, REPOSTS, LIKED, SAVED }
enum class SearchTab { USERS, VIDEOS }

class PublishDraft(val videoFile: File, val mime: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val api = Api(app)
    private val repo = Repo(api)
    private val ctx: Context get() = getApplication<Application>()

    val settings = AppSettings(app)

    /** Звук ленты: стартует по настройке, переключается кнопкой на видео. */
    var muted by mutableStateOf(settings.startMuted); private set

    fun toggleMute() {
        muted = !muted
    }

    fun toast(text: String) {
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
    }

    // ================================================================ СЕССИЯ

    var loading by mutableStateOf(true); private set
    var authed by mutableStateOf(false); private set
    var meId by mutableStateOf<String?>(null); private set
    var me by mutableStateOf<Profile?>(null); private set
    var authBusy by mutableStateOf(false); private set
    var authError by mutableStateOf(""); private set

    // Шаг регистрации «выберите фото профиля»
    var avatarSetup by mutableStateOf(false); private set
    var avatarSetupName by mutableStateOf(""); private set
    var avatarBusy by mutableStateOf(false); private set

    /** uri == null — «Пропустить». */
    fun finishAvatarSetup(uri: Uri?) {
        viewModelScope.launch {
            avatarBusy = true
            try {
                val uid = api.userId
                if (uri != null && uid != null) {
                    val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                    if (!mime.startsWith("image/")) {
                        toast("Выберите изображение")
                    } else {
                        val file = copyToCache(uri, "avatar_setup")
                        if (file.length() > 5L * 1024 * 1024) {
                            file.delete()
                            toast("Изображение слишком большое (макс. 5 МБ)")
                        } else {
                            val ext = if (mime.contains("png")) "png" else "jpg"
                            val url = repo.uploadFile(uid, "avatars", file, ext, mime)
                            file.delete()
                            repo.updateProfile(uid, JSONObject().put("avatar_url", url))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Не удалось загрузить фото — его можно добавить позже в профиле")
            } finally {
                avatarBusy = false
            }
            avatarSetup = false
            startSession()
        }
    }

    private suspend fun startSession() {
        try {
            val uid = api.currentUserId()
            if (uid == null) {
                authed = false
            } else {
                meId = uid
                loadMe()
                loadFriends()
                authed = true
                tab = Tab.HOME
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            authed = false
        } finally {
            loading = false
        }
        if (authed) loadFeedInternal()
    }

    fun submitAuth(register: Boolean, email: String, password: String, name: String) {
        viewModelScope.launch {
            authBusy = true
            authError = ""
            try {
                if (register) {
                    val hasSession = api.signUp(email.trim(), password, name.trim().ifEmpty { "User" })
                    if (hasSession) {
                        avatarSetupName = name.trim().ifEmpty { "User" }
                        avatarSetup = true
                    } else {
                        toast("Проверьте email для подтверждения")
                    }
                } else {
                    api.signIn(email.trim(), password)
                    startSession()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                authError = e.message ?: "Ошибка"
            } finally {
                authBusy = false
            }
        }
    }

    fun clearAuthError() {
        authError = ""
    }

    fun logout() {
        viewModelScope.launch {
            api.signOut()
            meId = null
            me = null
            authed = false
            tab = Tab.HOME
            feed = emptyList()
            feedMessage = null
            followingIds = emptySet()
            followerIds = emptySet()
            likedIds = emptySet()
            savedIds = emptySet()
            otherProfile = null
            profileVideos = emptyList()
            notices = emptyList()
            searchQuery = ""
            searchUsers = emptyList()
            searchVideos = emptyList()
            viewerVideos = null
            settingsOpen = false
            avatarSetup = false
            closeAllSheets()
        }
    }

    private suspend fun loadMe() {
        val uid = meId ?: return
        me = repo.getProfile(uid)
        // Если при регистрации в ник попала почта — заменяем на обычный ник.
        if (me?.username?.contains("@") == true) fixEmailUsername()
    }

    private suspend fun fixEmailUsername() {
        val p = me ?: return
        val uid = meId ?: return
        val base = p.nick()
        var candidate = base
        try {
            for (i in 0 until 5) {
                if (!repo.isUsernameTaken(candidate, uid)) break
                candidate = base + (0..9999).random()
            }
            val upd = JSONObject().put("username", candidate)
            var newDisplay = p.displayName
            if (p.displayName?.contains("@") == true) {
                upd.put("display_name", candidate)
                newDisplay = candidate
            }
            repo.updateProfile(uid, upd)
            me = p.copy(username = candidate, displayName = newDisplay)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    // ================================================================ ДРУЗЬЯ / ПОДПИСКИ

    var followingIds by mutableStateOf<Set<String>>(emptySet()); private set
    var followerIds by mutableStateOf<Set<String>>(emptySet()); private set

    /** Друзья — взаимная подписка. */
    val friendIds: Set<String> get() = followingIds intersect followerIds

    private suspend fun loadFriends() {
        val uid = meId ?: return
        try {
            val f = repo.followingIds(uid)
            val r = repo.followerIds(uid)
            followingIds = f
            followerIds = r
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    fun toggleFollow(userId: String) {
        val uid = meId ?: return
        if (userId == uid) return
        val on = userId !in followingIds
        followingIds = if (on) followingIds + userId else followingIds - userId
        viewModelScope.launch {
            try {
                if (on) repo.follow(uid, userId) else repo.unfollow(uid, userId)
                loadFriends()
                if (on && userId in friendIds) toast("Теперь вы друзья")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                followingIds = if (on) followingIds - userId else followingIds + userId
                toast("Ошибка подписки")
            }
        }
    }

    // ================================================================ НАВИГАЦИЯ

    var tab by mutableStateOf(Tab.HOME); private set

    fun openTab(t: Tab) {
        when (t) {
            Tab.PROFILE -> openProfile(null)
            Tab.NOTIFICATIONS -> {
                tab = t
                loadNotices()
            }
            else -> tab = t
        }
    }

    fun goHome() {
        tab = Tab.HOME
    }

    /** Переход в чужой/свой профиль из любого места (закрывает окна). */
    fun viewUserProfile(userId: String?) {
        if (userId.isNullOrEmpty()) return
        closeAllSheets()
        viewerVideos = null
        openProfile(userId)
    }

    // ================================================================ ЛЕНТА

    var feedType by mutableStateOf(FeedType.FORYOU); private set
    var feed by mutableStateOf<List<Video>>(emptyList()); private set
    var feedMessage by mutableStateOf<String?>(null); private set
    var feedLoading by mutableStateOf(false); private set
    private var feedJob: Job? = null

    var likedIds by mutableStateOf<Set<String>>(emptySet()); private set
    var savedIds by mutableStateOf<Set<String>>(emptySet()); private set
    private var likeAdjust by mutableStateOf<Map<String, Int>>(emptyMap())
    private var commentAdjust by mutableStateOf<Map<String, Int>>(emptyMap())

    fun likesOf(v: Video) = (v.likesCount + (likeAdjust[v.id] ?: 0)).coerceAtLeast(0)
    fun commentsOf(v: Video) = (v.commentsCount + (commentAdjust[v.id] ?: 0)).coerceAtLeast(0)

    fun switchFeed(type: FeedType) {
        feedType = type
        loadFeed()
    }

    fun loadFeed() {
        feedJob?.cancel()
        feedJob = viewModelScope.launch { loadFeedInternal() }
    }

    private suspend fun loadFeedInternal() {
        val uid = meId ?: return
        feedLoading = true
        feedMessage = null
        feed = emptyList()
        likeAdjust = emptyMap()
        commentAdjust = emptyMap()
        try {
            val hidden: Set<String> = try {
                repo.notInterestedIds(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptySet()
            }

            var videos: List<Video>
            if (feedType == FeedType.FOLLOWING) {
                // «Подписки» — как в TikTok: только выбранные авторы, свежие сверху.
                val ids = repo.followingIds(uid).toList()
                if (ids.isEmpty()) {
                    feedMessage = "Подпишитесь на пользователей,\nчтобы видеть их видео."
                    return
                }
                videos = repo.feedVideos(ids).filter { it.id !in hidden }
            } else {
                // «Для вас» — персональные рекомендации (SQL get_recommended_feed).
                // Если функция ещё не создана в базе — запасной вариант: свежие видео.
                videos = try {
                    repo.recommended(emptyList())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    repo.feedVideos(null).filter { it.id !in hidden }
                }
            }
            if (videos.isEmpty()) {
                feedMessage = "Видео пока нет"
                return
            }
            // Репосты друзей — одной карточкой на видео, поверх ленты.
            if (settings.friendReposts) {
                try {
                    loadFriends()
                    val reposts = repo.friendReposts(friendIds).filter { it.id !in hidden }
                    if (reposts.isNotEmpty()) {
                        val rIds = reposts.map { it.id }.toSet()
                        videos = reposts + videos.filter { it.id !in rIds }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            loadStates(videos)
            feed = videos
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast("Ошибка загрузки видео")
        } finally {
            feedLoading = false
        }
    }

    private var loadingMore = false

    /** Бесконечная лента: когда пользователь близко к концу — дозагружаем новые рекомендации. */
    fun loadMore() {
        if (feedType != FeedType.FORYOU || loadingMore || feed.isEmpty() || feedLoading) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val have = feed.map { it.id }.toSet()
                val more = repo.recommended(have).filter { it.id !in have }
                if (more.isNotEmpty() && feedType == FeedType.FORYOU) {
                    loadStates(more)
                    feed = feed + more
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                loadingMore = false
            }
        }
    }

    /** Сколько человек реально смотрел — отправляем на сервер для рекомендаций. */
    fun recordView(video: Video, watchedMs: Long, durationMs: Long) {
        val uid = meId ?: return
        if (watchedMs < 300) return
        viewModelScope.launch {
            try {
                repo.recordView(uid, video.id, watchedMs, durationMs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun loadStates(videos: List<Video>) {
        val uid = meId ?: return
        val ids = videos.map { it.id }.distinct()
        if (ids.isEmpty()) return
        try {
            likedIds = likedIds + repo.likedAmong(uid, ids)
            savedIds = savedIds + repo.savedAmong(uid, ids)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    fun toggleLike(video: Video) {
        val uid = meId ?: return
        val on = video.id !in likedIds
        val delta = if (on) 1 else -1
        likedIds = if (on) likedIds + video.id else likedIds - video.id
        likeAdjust = likeAdjust + (video.id to ((likeAdjust[video.id] ?: 0) + delta))
        viewModelScope.launch {
            try {
                repo.setLike(uid, video.id, on)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                likedIds = if (on) likedIds - video.id else likedIds + video.id
                likeAdjust = likeAdjust + (video.id to ((likeAdjust[video.id] ?: 0) - delta))
                toast("Не удалось изменить лайк")
            }
        }
    }

    fun toggleSave(video: Video) {
        val uid = meId ?: return
        val on = video.id !in savedIds
        savedIds = if (on) savedIds + video.id else savedIds - video.id
        viewModelScope.launch {
            try {
                repo.setSaved(uid, video.id, on)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                savedIds = if (on) savedIds - video.id else savedIds + video.id
                toast("Ошибка")
            }
        }
    }

    // ================================================================ КОММЕНТАРИИ

    var commentsVideoId by mutableStateOf<String?>(null); private set
    var comments by mutableStateOf<List<Comment>>(emptyList()); private set
    var commentsLoading by mutableStateOf(false); private set

    fun openComments(videoId: String) {
        shareVideo = null
        commentsVideoId = videoId
        comments = emptyList()
        viewModelScope.launch { loadComments() }
    }

    fun closeComments() {
        commentsVideoId = null
    }

    private suspend fun loadComments() {
        val id = commentsVideoId ?: return
        commentsLoading = true
        try {
            val list = repo.comments(id)
            if (commentsVideoId == id) comments = list
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            commentsLoading = false
        }
    }

    fun sendComment(text: String, onSent: () -> Unit) {
        val uid = meId ?: return
        val vid = commentsVideoId ?: return
        val t = text.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            try {
                repo.addComment(vid, uid, t)
                commentAdjust = commentAdjust + (vid to ((commentAdjust[vid] ?: 0) + 1))
                onSent()
                loadComments()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Ошибка комментария")
            }
        }
    }

    // ================================================================ ПОДЕЛИТЬСЯ / РЕПОСТ / ЖАЛОБА / УДАЛЕНИЕ

    var shareVideo by mutableStateOf<Video?>(null); private set
    var shareReposted by mutableStateOf(false); private set
    var reportVideo by mutableStateOf<Video?>(null); private set

    fun openShare(video: Video) {
        commentsVideoId = null
        shareVideo = video
        shareReposted = false
        val uid = meId ?: return
        viewModelScope.launch {
            try {
                val r = repo.isReposted(uid, video.id)
                if (shareVideo?.id == video.id) shareReposted = r
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun closeShare() {
        shareVideo = null
    }

    fun toggleRepost() {
        val uid = meId ?: return
        val v = shareVideo ?: return
        val on = !shareReposted
        viewModelScope.launch {
            try {
                repo.setRepost(uid, v.id, on)
                toast(if (on) "Видео репостнуто друзьям" else "Репост отменён")
                closeShare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Не удалось сделать репост")
            }
        }
    }

    fun copyShareLink() {
        val v = shareVideo ?: return
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("url", v.videoUrl))
            toast("Ссылка скопирована")
        } catch (e: Exception) {
            toast("Не удалось скопировать")
        }
    }

    fun shareToApp(platform: String) {
        val v = shareVideo ?: return
        if (platform == "vk") {
            openExternal("https://vk.com/share.php?url=" + Uri.encode(v.videoUrl))
            closeShare()
            return
        }
        // У Discord и MAX нет публичного веб-адреса для шаринга — копируем ссылку.
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("url", v.videoUrl))
        } catch (_: Exception) {
        }
        toast(if (platform == "discord") "Ссылка скопирована, вставьте в Discord" else "Ссылка скопирована, вставьте в MAX")
        closeShare()
    }

    /** «Не интересно»: видео пропадает из ленты и больше не возвращается, а похожие хэштеги и автор получают минус. */
    fun notInterested() {
        val v = shareVideo ?: return
        val uid = meId ?: return
        closeShare()
        feed = feed.filter { it.id != v.id }
        viewerVideos = viewerVideos?.filter { it.id != v.id }?.takeIf { it.isNotEmpty() }
        if (feed.isEmpty()) feedMessage = "Видео пока нет"
        toast("Хорошо, будем показывать меньше похожих видео")
        viewModelScope.launch {
            try {
                repo.markNotInterested(uid, v)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun whyShown() {
        toast(if (shareVideo?.repostedBy?.isNotEmpty() == true) "Это видео репостнул ваш друг" else "Показано по вашим лайкам, просмотрам и подпискам")
    }

    fun downloadCurrentVideo() {
        val v = shareVideo ?: return
        toast("Загрузка видео...")
        try {
            val ext = if (v.videoUrl.contains(".mp4")) "mp4" else "webm"
            val name = "shorts_" + System.currentTimeMillis() + "." + ext
            val req = DownloadManager.Request(Uri.parse(v.videoUrl))
                .setTitle(name)
                .setMimeType("video/$ext")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            if (Build.VERSION.SDK_INT >= 29) {
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            } else {
                req.setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_MOVIES, name)
            }
            (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            closeShare()
        } catch (e: Exception) {
            openExternal(v.videoUrl)
        }
    }

    fun openExternal(url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Не удалось открыть ссылку")
        }
    }

    fun openReport() {
        reportVideo = shareVideo
        shareVideo = null
    }

    fun closeReport() {
        reportVideo = null
    }

    suspend fun submitReport(category: String, reason: String): Boolean {
        val uid = meId ?: return false
        val v = reportVideo ?: return false
        return try {
            repo.report(v.id, uid, category, reason)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    fun deleteCurrentVideo() {
        val uid = meId ?: return
        val v = shareVideo ?: return
        if (v.userId != uid) return
        viewModelScope.launch {
            try {
                repo.deleteVideo(v, uid)
                closeShare()
                feed = feed.filter { it.id != v.id }
                profileVideos = profileVideos.filter { it.id != v.id }
                searchVideos = searchVideos.filter { it.id != v.id }
                viewerVideos = viewerVideos?.filter { it.id != v.id }?.takeIf { it.isNotEmpty() }
                if (feed.isEmpty()) feedMessage = "Видео пока нет"
                toast("Видео удалено")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Не удалось удалить видео")
            }
        }
    }

    private fun closeAllSheets() {
        commentsVideoId = null
        shareVideo = null
        reportVideo = null
    }

    // ================================================================ ПРОСМОТР СПИСКА ВИДЕО (из профиля / поиска)

    var viewerVideos by mutableStateOf<List<Video>?>(null); private set
    var viewerStart by mutableIntStateOf(0); private set
    var viewerTitle by mutableStateOf("Видео"); private set

    fun openViewer(videos: List<Video>, index: Int, title: String) {
        if (videos.isEmpty()) return
        viewerStart = index
        viewerTitle = title
        viewerVideos = videos
        viewModelScope.launch { loadStates(videos) }
    }

    fun closeViewer() {
        viewerVideos = null
    }

    // ================================================================ ПРОФИЛЬ

    var profileUserId by mutableStateOf<String?>(null); private set
    var otherProfile by mutableStateOf<Profile?>(null); private set
    var profileTab by mutableStateOf(ProfileTab.VIDEOS); private set
    var profileVideos by mutableStateOf<List<Video>>(emptyList()); private set
    var profileGridMessage by mutableStateOf<String?>(null); private set
    var friendsCount by mutableStateOf(0); private set
    private var profileJob: Job? = null
    private var gridJob: Job? = null

    val profileShown: Profile?
        get() = if (profileUserId == meId) me else otherProfile

    fun openProfile(userId: String?) {
        val id = userId ?: meId ?: return
        tab = Tab.PROFILE
        profileUserId = id
        profileTab = ProfileTab.VIDEOS
        profileJob?.cancel()
        gridJob?.cancel()
        profileJob = viewModelScope.launch {
            try {
                if (id == meId) {
                    loadMe()
                } else {
                    otherProfile = null
                    otherProfile = repo.getProfile(id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Не удалось загрузить профиль")
                return@launch
            }
            loadFriendsCount(id)
            loadProfileTabContent()
        }
    }

    private suspend fun loadFriendsCount(userId: String) {
        try {
            val following = repo.followingIds(userId)
            val followers = repo.followerIds(userId)
            friendsCount = followers.count { it in following }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            friendsCount = 0
        }
    }

    fun switchProfileTab(t: ProfileTab) {
        profileTab = t
        gridJob?.cancel()
        gridJob = viewModelScope.launch { loadProfileTabContent() }
    }

    private suspend fun loadProfileTabContent() {
        val uid = profileUserId ?: meId ?: return
        val tabAtStart = profileTab
        profileVideos = emptyList()
        profileGridMessage = "Загрузка..."
        try {
            val videos: List<Video>
            val empty: String
            when (tabAtStart) {
                ProfileTab.VIDEOS -> {
                    videos = repo.userVideos(uid); empty = "Пока нет видео"
                }
                ProfileTab.REPOSTS -> {
                    videos = repo.videosByJoin("reposts", uid); empty = "Пока нет репостов"
                }
                ProfileTab.LIKED -> {
                    videos = repo.videosByJoin("video_likes", uid); empty = "Пока нет понравившихся видео"
                }
                ProfileTab.SAVED -> {
                    videos = repo.videosByJoin("bookmarks", uid); empty = "Пока нет сохранённых видео"
                }
            }
            if (profileTab != tabAtStart) return
            profileVideos = videos
            profileGridMessage = if (videos.isEmpty()) empty else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (profileTab == tabAtStart) profileGridMessage = "Не удалось загрузить"
        }
    }

    // ---------------- редактирование профиля

    var editOpen by mutableStateOf(false); private set

    fun openEdit() {
        editOpen = true
    }

    fun closeEdit() {
        editOpen = false
    }

    suspend fun saveProfile(name: String, bio: String, avatarUri: Uri?): Boolean {
        val uid = meId ?: return false
        val current = me ?: return false
        val displayName = name.trim()
        if (displayName.isEmpty()) {
            toast("Введите имя"); return false
        }
        try {
            var avatarUrl = current.avatarUrl
            if (avatarUri != null) {
                val mime = ctx.contentResolver.getType(avatarUri) ?: "image/jpeg"
                if (!mime.startsWith("image/")) {
                    toast("Выберите изображение"); return false
                }
                val file = copyToCache(avatarUri, "avatar_upload")
                if (file.length() > 5L * 1024 * 1024) {
                    file.delete()
                    toast("Изображение слишком большое (макс. 5 МБ)"); return false
                }
                val ext = if (mime.contains("png")) "png" else "jpg"
                avatarUrl = repo.uploadFile(uid, "avatars", file, ext, mime)
                file.delete()
            }
            val upd = JSONObject()
                .put("display_name", displayName)
                .put("bio", bio.trim())
                .put("avatar_url", avatarUrl ?: JSONObject.NULL)
            repo.updateProfile(uid, upd)
            loadMe()
            toast("Профиль обновлён")
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast("Не удалось сохранить: " + (e.message ?: ""))
            return false
        }
    }

    // ================================================================ ПОИСК

    var searchQuery by mutableStateOf(""); private set
    var searchTab by mutableStateOf(SearchTab.USERS); private set
    var searchUsers by mutableStateOf<List<Profile>>(emptyList()); private set
    var searchVideos by mutableStateOf<List<Video>>(emptyList()); private set
    var searchMessage by mutableStateOf<String?>(null); private set
    private var searchJob: Job? = null

    fun onSearchInput(q: String) {
        searchQuery = q
        scheduleSearch(350)
    }

    fun switchSearchTab(t: SearchTab) {
        searchTab = t
        scheduleSearch(0)
    }

    /** Нажатие на #хэштег в видео. */
    fun openHashtagSearch(tag: String) {
        viewerVideos = null
        tab = Tab.SEARCH
        searchQuery = "#$tag"
        searchTab = SearchTab.VIDEOS
        scheduleSearch(0)
    }

    private fun scheduleSearch(delayMs: Long) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            runSearch()
        }
    }

    private suspend fun runSearch() {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            searchUsers = emptyList(); searchVideos = emptyList(); searchMessage = null
            return
        }
        // Запрос вида "#тег" ищет по видео.
        if (q.startsWith("#") && searchTab != SearchTab.VIDEOS) searchTab = SearchTab.VIDEOS
        try {
            if (searchTab == SearchTab.VIDEOS) {
                val v = repo.searchVideos(q)
                searchVideos = v
                searchMessage = if (v.isEmpty()) "Ничего не найдено" else null
            } else {
                val u = repo.searchUsers(q)
                searchUsers = u
                searchMessage = if (u.isEmpty()) "Ничего не найдено" else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            searchMessage = "Ошибка поиска"
        }
    }

    // ================================================================ УВЕДОМЛЕНИЯ

    var notices by mutableStateOf<List<Notice>>(emptyList()); private set
    var noticesLoaded by mutableStateOf(false); private set

    private fun loadNotices() {
        val uid = meId ?: return
        viewModelScope.launch {
            try {
                val list = repo.notices(uid)
                notices = list
                noticesLoaded = true
                if (list.isNotEmpty()) repo.markNoticesRead(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    // ================================================================ КАМЕРА И ПУБЛИКАЦИЯ

    var cameraOpen by mutableStateOf(false); private set
    var draft by mutableStateOf<PublishDraft?>(null); private set
    var draftCovers by mutableStateOf<List<File>>(emptyList()); private set
    var draftCover by mutableStateOf<File?>(null); private set
    var publishing by mutableStateOf(false); private set
    var publishLabel by mutableStateOf("Опубликовать"); private set

    fun openCamera() {
        cameraOpen = true
    }

    fun closeCamera() {
        cameraOpen = false
    }

    /** Видео записано камерой — переходим к экрану публикации. */
    fun onRecorded(file: File) {
        cameraOpen = false
        startDraft(file, "video/mp4")
    }

    /** Видео выбрано из галереи (до 200 МБ). */
    fun onGalleryVideo(uri: Uri) {
        viewModelScope.launch {
            try {
                val mime = ctx.contentResolver.getType(uri) ?: ""
                if (!mime.startsWith("video/")) {
                    toast("Выберите видеофайл"); return@launch
                }
                val size = querySize(uri)
                if (size > 200L * 1024 * 1024) {
                    toast("Файл слишком большой (макс. 200 МБ)"); return@launch
                }
                val ext = when {
                    mime.contains("quicktime") -> "mov"
                    mime.contains("webm") -> "webm"
                    else -> "mp4"
                }
                val file = copyToCache(uri, "draft_${System.currentTimeMillis()}.$ext")
                cameraOpen = false
                startDraft(file, mime)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Не удалось открыть видео")
            }
        }
    }

    private fun querySize(uri: Uri): Long {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.SIZE)
                    if (i >= 0 && !c.isNull(i)) return c.getLong(i)
                }
            }
        } catch (_: Exception) {
        }
        return 0L
    }

    private fun startDraft(file: File, mime: String) {
        draft = PublishDraft(file, mime)
        draftCover = null
        draftCovers = emptyList()
        publishing = false
        publishLabel = "Опубликовать"
        viewModelScope.launch { generateCovers(file) }
    }

    /** 6 кадров для выбора обложки (как в оригинале). Первый — по умолчанию. */
    private suspend fun generateCovers(video: File) {
        val frames = withContext(Dispatchers.IO) {
            val out = ArrayList<File>()
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(video.absolutePath)
                val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.takeIf { it > 0 } ?: 1000L
                val count = 6
                for (i in 0 until count) {
                    val timeMs = durationMs / count * i + durationMs / (count * 2)
                    try {
                        var bmp = mmr.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: continue
                        if (bmp.height > 720) {
                            bmp = Bitmap.createScaledBitmap(bmp, bmp.width * 720 / bmp.height, 720, true)
                        }
                        val f = File(ctx.cacheDir, "cover_${System.currentTimeMillis()}_$i.jpg")
                        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                        out.add(f)
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    mmr.release()
                } catch (_: Exception) {
                }
            }
            out
        }
        if (draft?.videoFile == video) {
            draftCovers = frames
            if (draftCover == null && frames.isNotEmpty()) draftCover = frames[0]
        }
    }

    fun pickCoverFrame(file: File) {
        draftCover = file
    }

    fun setCustomCover(uri: Uri) {
        viewModelScope.launch {
            try {
                val mime = ctx.contentResolver.getType(uri) ?: ""
                if (!mime.startsWith("image/")) {
                    toast("Выберите изображение"); return@launch
                }
                val ext = if (mime.contains("png")) "png" else "jpg"
                draftCover = copyToCache(uri, "cover_custom_${System.currentTimeMillis()}.$ext")
                toast("Обложка обновлена")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Не удалось открыть изображение")
            }
        }
    }

    /** «Отмена»: выбросить черновик и вернуться к камере (как в оригинале). */
    fun discardDraft() {
        clearDraftFiles()
        cameraOpen = true
    }

    private fun clearDraftFiles() {
        try {
            draft?.videoFile?.delete()
            draftCovers.forEach { it.delete() }
            draftCover?.delete()
        } catch (_: Exception) {
        }
        draft = null
        draftCovers = emptyList()
        draftCover = null
        publishing = false
        publishLabel = "Опубликовать"
    }

    fun publish(title: String, caption: String, hashtags: List<String>) {
        val uid = meId ?: return
        val d = draft ?: run {
            toast("Видео отсутствует"); return
        }
        if (publishing) return
        if (settings.wifiOnlyUpload && !isUnmeteredNetwork()) {
            toast("Включён режим «только Wi-Fi» — подключитесь к Wi-Fi")
            return
        }
        publishing = true
        viewModelScope.launch {
            var compressedFile: File? = null
            try {
                publishLabel = "Загрузка..."
                toast("Загрузка видео...")
                val ext = when {
                    d.mime.contains("mp4") -> "mp4"
                    d.mime.contains("quicktime") -> "mov"
                    d.mime.contains("webm") -> "webm"
                    else -> d.videoFile.extension.ifEmpty { "mp4" }
                }
                var upFile = d.videoFile
                var upExt = ext
                var upMime = d.mime
                if (settings.compressVideo) {
                    publishLabel = "Сжатие..."
                    toast("Сжимаю видео, чтобы оно занимало меньше места...")
                    val packed = VideoCompressor.compress(ctx, d.videoFile)
                    if (packed != null) {
                        compressedFile = packed
                        upFile = packed; upExt = "mp4"; upMime = "video/mp4"
                    }
                    publishLabel = "Загрузка..."
                }
                val videoUrl = repo.uploadVideo(uid, upFile, upExt, upMime)

                // Обложка: своя или выбранный кадр. Ошибка обложки публикацию не ломает.
                var thumbUrl: String? = null
                val cover = draftCover
                if (cover != null) {
                    try {
                        publishLabel = "Обложка..."
                        val cExt = if (cover.extension.equals("png", true)) "png" else "jpg"
                        val cType = if (cExt == "png") "image/png" else "image/jpeg"
                        thumbUrl = repo.uploadFile(uid, "covers", cover, cExt, cType)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
                publishLabel = "Публикация..."
                repo.insertVideo(uid, videoUrl, thumbUrl, title.trim(), caption.trim(), hashtags)
                toast("Видео опубликовано!")
                clearDraftFiles()
                tab = Tab.HOME
                loadFeed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Ошибка загрузки: " + (e.message ?: ""))
            } finally {
                compressedFile?.delete()
                publishing = false
                publishLabel = "Опубликовать"
            }
        }
    }

    // ================================================================ НАСТРОЙКИ

    var settingsOpen by mutableStateOf(false); private set

    fun openSettings() {
        settingsOpen = true
    }

    fun closeSettings() {
        settingsOpen = false
    }

    fun changePassword(newPassword: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                api.changePassword(newPassword)
                toast("Пароль изменён")
                onDone(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Не удалось сменить пароль: " + (e.message ?: ""))
                onDone(false)
            }
        }
    }

    /** Стирает историю просмотров и «Не интересно» — рекомендации начинаются с нуля. */
    fun resetRecommendations() {
        val uid = meId ?: return
        viewModelScope.launch {
            try {
                repo.resetRecommendations(uid)
                toast("Рекомендации сброшены")
                loadFeed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Не удалось сбросить: " + (e.message ?: ""))
            }
        }
    }

    fun cacheSizeBytes(): Long {
        fun size(f: File): Long = if (f.isFile) f.length() else (f.listFiles()?.sumOf { size(it) } ?: 0L)
        return try {
            size(ctx.cacheDir)
        } catch (_: Exception) {
            0L
        }
    }

    fun clearCache() {
        try {
            ctx.imageLoader.memoryCache?.clear()
            ctx.imageLoader.diskCache?.clear()
            if (draft == null) {
                ctx.cacheDir.listFiles()?.forEach { f ->
                    if (f.isFile) f.delete()
                }
            }
        } catch (_: Exception) {
        }
        toast("Кэш очищен")
    }

    /** Удаляет файлы пользователя из хранилища и сам аккаунт (SQL delete_my_account). */
    fun deleteAccount() {
        val uid = meId ?: return
        viewModelScope.launch {
            try {
                try {
                    val paths = repo.userVideos(uid).flatMap {
                        listOfNotNull(repo.storagePath(it.videoUrl), repo.storagePath(it.thumbnailUrl))
                    } + listOfNotNull(repo.storagePath(me?.avatarUrl))
                    paths.chunked(100).forEach { api.removeObjects(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                repo.deleteMyAccount()
                toast("Аккаунт удалён")
                logout()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Не удалось удалить аккаунт: " + (e.message ?: ""))
            }
        }
    }

    private fun isUnmeteredNetwork(): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    } catch (e: Exception) {
        true
    }

    // Запуск — в самом конце, чтобы все свойства класса уже были инициализированы.
    init {
        viewModelScope.launch { startSession() }
    }

    // ================================================================ утилиты

    private suspend fun copyToCache(uri: Uri, name: String): File = withContext(Dispatchers.IO) {
        val f = File(ctx.cacheDir, name)
        val input = ctx.contentResolver.openInputStream(uri) ?: throw ApiException("Не удалось открыть файл")
        input.use { src -> f.outputStream().use { dst -> src.copyTo(dst) } }
        f
    }
}
