package com.droiddesk.launcher

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** One language DroidDesk ships translations for. [tag] is a BCP-47 tag, [name] is written in that language. */
class Lang(val tag: String, val name: String)

/**
 * In-app language choice. The default is "" = follow the system language, so nothing changes for
 * people who never open the picker. Every language listed here has a matching res/values-xx folder
 * and an entry in res/xml/locales_config.xml.
 */
object LocaleHelper {
    val languages: List<Lang> = listOf(
        Lang("en", "English"),
        Lang("tr", "Türkçe"),
        Lang("de", "Deutsch"),
        Lang("fr", "Français"),
        Lang("es", "Español"),
        Lang("ru", "Русский"),
        Lang("ar", "العربية")
    )

    fun savedTag(ctx: Context): String =
        ctx.getSharedPreferences("droiddesk", Context.MODE_PRIVATE).getString("language", "") ?: ""

    private fun localeFor(tag: String): Locale = Locale.forLanguageTag(tag)

    /** Wraps [base] so strings come from the chosen language. With "system default" the base is returned untouched. */
    fun wrap(base: Context): Context {
        val tag = savedTag(base)
        if (tag.isEmpty()) {
            // Follow the system: make sure Locale.getDefault() (dates, sorting) is the system one again.
            try { Locale.setDefault(base.resources.configuration.locales.get(0)) } catch (e: Exception) { }
            return base
        }
        val locale = localeFor(tag)
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        return base.createConfigurationContext(cfg)
    }

    /** Same idea for a Service, whose base context cannot be replaced after it is attached. */
    fun localizedResources(ctx: Context, plain: Resources): Resources {
        val tag = savedTag(ctx)
        if (tag.isEmpty()) return plain
        val locale = localeFor(tag)
        val cfg = Configuration(plain.configuration)
        cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        return ctx.createConfigurationContext(cfg).resources
    }

    /** Name shown in settings for the current choice. */
    fun currentName(ctx: Context): String {
        val tag = savedTag(ctx)
        return languages.firstOrNull { it.tag == tag }?.name ?: ctx.getString(R.string.lang_system)
    }
}
