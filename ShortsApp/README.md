# Shorts — нативное Android-приложение (Kotlin + Jetpack Compose)

Полный порт бывшего HTML/WebView-приложения. WebView и `index.html` удалены.

## Что внутри
- **UI:** Jetpack Compose (Material3), тёмная тема, акцент `#ff0050`
- **Видео:** Media3 ExoPlayer, вертикальный пейджер, тап — пауза, двойной тап — лайк
- **Камера:** CameraX, запись mp4 до 3 минут, смена камеры, загрузка из галереи (до 200 МБ)
- **Бэкенд:** тот же Supabase (Auth, PostgREST, Storage) — через OkHttp, без JS-библиотеки
- **Функции:** вход/регистрация, лента «Для вас»/«Подписки», репосты друзей, лайки, закладки,
  подписки и друзья, комментарии, поиск людей/видео/#хэштегов, входящие, профиль (4 вкладки),
  редактирование профиля и аватара, публикация с обложкой и хэштегами, жалобы, скачивание, удаление

## Структура (`app/src/main/java/com/example/shorts`)
`Api.kt` (Supabase REST) · `Repo.kt` (запросы) · `AppViewModel.kt` (состояние и логика) ·
`FeedScreen.kt` · `Screens.kt` · `Sheets.kt` · `CameraScreen.kt` · `PublishScreen.kt` · `MainActivity.kt`

## Сборка
Загрузите папку на GitHub — Actions соберёт `app-debug.apk` (раздел Artifacts).
Локально: Android Studio → Open → Run.
