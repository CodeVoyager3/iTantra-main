package com.itantra.app.modelhub

/**
 * Pure helpers for matching 2-letter language codes against installed pack
 * tags. Model packs install under their full tag (`hi-IN`, `en-IN`, ...) while
 * the UI works with `SupportedLanguage.code` (`hi`, `en`, ...), so every
 * installed/not-installed check must go through [matches].
 *
 * No Android imports -- unit-testable on the host JVM.
 */
object LanguageTags {

    fun normalize(code: String): String = code.trim().lowercase()

    /**
     * True when [installedTag] satisfies the query [queryCode].
     * `"hi"` matches `"hi-IN"`; `"hi-IN"` matches `"hi-IN"`; `"hi"` does not
     * match `"gu-IN"`.
     */
    fun matches(installedTag: String, queryCode: String): Boolean {
        val q = normalize(queryCode)
        val t = normalize(installedTag)
        if (q.isEmpty() || t.isEmpty()) return false
        return t == q || t.startsWith("$q-")
    }

    /** The first installed tag satisfying [queryCode], or null when none do. */
    fun resolve(installedTags: Collection<String>, queryCode: String): String? =
        installedTags.firstOrNull { matches(it, queryCode) }
}
