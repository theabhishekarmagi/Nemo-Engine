// Modified for Nemo-Engine by MAGI ABHISHEKAR (theabhishekarmagi), 2026-11-05.
package com.wangzan101.mika.i18n

import android.app.NotificationManager
import com.wangzan101.mika.R
import com.wangzan101.mika.task.TaskService
import com.wangzan101.mika.cap.phone.setup.PairingChannel
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.icu.util.ULocale
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** This distribution always uses English, including services and notifications. */
enum class AppLanguage(val tag: String?, val speechTag: String?) {
    System(null, null),
    SimplifiedChinese("zh-Hans", "zh-CN"),
    TraditionalChinese("zh-Hant", "zh-TW"),
    English("en", "en-US"),
    Japanese("ja", "ja-JP"),
    Korean("ko", "ko-KR"),
    Spanish("es", "es-ES"),
    Portuguese("pt", "pt-BR"),
    Russian("ru", "ru-RU"),
    Indonesian("id", "id-ID");

    /** 语言代码(不带文字体系和地区);跟随系统时为 null。 */
    val language: String? get() = tag?.let { Locale.forLanguageTag(it).language.let(::modernCode) }

    companion object {
        val available: List<AppLanguage> = listOf(English)
        @Volatile private var englishResources: Resources? = null
        private val englishLocales get() = LocaleList.forLanguageTags("en")

        @Suppress("UNUSED_PARAMETER")
        fun current(context: Context): AppLanguage = English

        /** 语言标签对应哪一项:见 [fromTag];标签里没写文字体系时,按 ICU 的常见写法补出来。 */
        fun fromTag(tag: String?): AppLanguage = fromTag(tag) { ULocale.addLikelySubtags(ULocale.forLanguageTag(it)).script }

        /**
         * 中文按文字体系分:写的或 [likelyScript] 补出的是 Hant 就是繁體中文,别的中文是简体中文;
         * 其他语言按语言代码对上一项(地区不管),对不上的是英文。以前选「中文」存下的 zh-Hans 仍是简体中文。
         */
        internal fun fromTag(tag: String?, likelyScript: (String) -> String): AppLanguage {
            if (tag.isNullOrEmpty()) return System
            val l = Locale.forLanguageTag(tag)
            val code = modernCode(l.language)
            if (code == "zh") return if (l.script.ifEmpty { likelyScript(tag) } == "Hant") TraditionalChinese else SimplifiedChinese
            return entries.firstOrNull { it.tag != null && it.language == code } ?: English
        }

        /** 安卓上 java.util.Locale 仍给几种语言用旧代码(印尼语是 in、希伯来语是 iw…),统一成现行代码再比。 */
        internal fun modernCode(language: String): String = when (language) {
            "in" -> "id"
            "iw" -> "he"
            "ji" -> "yi"
            else -> language
        }

        /** Ignore saved language choices from earlier multilingual builds. */
        @Suppress("UNUSED_PARAMETER")
        fun set(context: Context, language: AppLanguage) {
            applyDefaultLocale(context)
        }

        fun applyDefaultLocale(context: Context) {
            Locale.setDefault(Locale.ENGLISH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val manager = context.getSystemService(LocaleManager::class.java)
                if (manager.applicationLocales.toLanguageTags() != "en") {
                    manager.applicationLocales = englishLocales
                }
            }
        }

        /** Android persists channel names across updates; refresh without changing preferences. */
        fun refreshNotificationChannels(context: Context) {
            val names = mapOf(
                TaskService.CHANNEL_PROGRESS to R.string.channel_progress,
                TaskService.CHANNEL_RESULT to R.string.channel_result,
                TaskService.CHANNEL_ATTENTION to R.string.channel_attention,
                "runtime_prepare" to R.string.notif_channel_prepare,
            ) + (PairingChannel.IDS + PairingChannel.LEGACY).associateWith { R.string.phone_channel }
            val manager = context.getSystemService(NotificationManager::class.java)
            val english = resources(context)
            manager.notificationChannels.forEach { channel ->
                names[channel.id]?.let { id ->
                    channel.name = english.getString(id)
                    manager.createNotificationChannel(channel)
                }
            }
        }

        /** Explicit resources also cover Android 11/12 and background components. */
        fun wrap(base: Context): Context = base.createConfigurationContext(
            Configuration().apply { setLocales(englishLocales) },
        )

        fun resources(app: Context): Resources =
            englishResources ?: wrap(app).resources.also { englishResources = it }
    }
}
