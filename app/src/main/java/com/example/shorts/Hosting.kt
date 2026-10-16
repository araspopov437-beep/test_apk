package com.example.shorts

/**
 * Внешний видеохостинг (Cloudinary). Видео уходит туда, а в Supabase сохраняется только ссылка.
 *
 * Как включить:
 * 1. Зарегистрируйтесь на cloudinary.com (бесплатный тариф).
 * 2. Cloud name — на главной странице Dashboard.
 * 3. Settings → Upload → Upload presets → Add upload preset →
 *    Signing mode = «Unsigned», сохраните и скопируйте имя пресета.
 * 4. Впишите оба значения ниже. Пока они пустые — видео грузится в Supabase, как раньше.
 */
const val CLOUDINARY_CLOUD_NAME = "bgki9ek8"
const val CLOUDINARY_UPLOAD_PRESET = "shorts"

val externalHostingEnabled: Boolean
    get() = CLOUDINARY_CLOUD_NAME.isNotBlank() && CLOUDINARY_UPLOAD_PRESET.isNotBlank()
