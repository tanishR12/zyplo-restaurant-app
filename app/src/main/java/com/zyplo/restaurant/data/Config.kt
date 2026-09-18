package com.zyplo.restaurant.data

object Config {
    const val PORTAL_URL = "https://zyplo.in/restaurant-login"
    const val SITE_ORIGIN = "https://zyplo.in"

    val trustedHosts = setOf(
        "zyplo.in",
        "www.zyplo.in",
        "lovable.dev",
        "lovable.app",
        "lovableproject.com"
    )

    fun isTrustedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return false
            trustedHosts.any { host == it || host.endsWith(".$it") }
        } catch (_: Exception) {
            false
        }
    }

    fun looksLoggedIn(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val path = android.net.Uri.parse(url).path?.lowercase() ?: return false
        if (!isTrustedUrl(url)) return false
        return path.contains("restaurant") &&
            !path.contains("restaurant-login") &&
            !path.contains("login") &&
            !path.contains("register")
    }
}
