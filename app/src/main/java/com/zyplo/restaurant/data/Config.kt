package com.zyplo.restaurant.data

object Config {
    const val PORTAL_URL = "https://zyplo.in/restaurant-login"
    const val SITE_ORIGIN = "https://zyplo.in"
    const val SUPABASE_URL = "https://itjjcscyqqxkeipkccgl.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml0ampjc2N5cXF4a2VpcGtjY2dsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTQwOTg4MDEsImV4cCI6MjA2OTY3NDgwMX0.ZUpcDDVxUiZ3uNgF0Mbb3HjqhY9yc_B2xWeUARDk4Yc"
    const val LIVE_POLL_MS = 10_000L

    val trustedHosts = setOf(
        "zyplo.in",
        "www.zyplo.in",
        "lovable.dev",
        "lovable.app",
        "lovableproject.com",
        "supabase.co",
        "supabase.in",
        "firebaseapp.com",
        "web.app",
        "googleapis.com",
        "gstatic.com",
        "google.com",
        "google.co.in",
        "gvt1.com",
        "firebaseio.com",
        "firebasestorage.app"
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

    fun shouldOpenExternally(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("tel:") ||
            lower.startsWith("mailto:") ||
            lower.startsWith("sms:") ||
            lower.startsWith("geo:") ||
            lower.startsWith("intent:") ||
            lower.startsWith("market:") ||
            lower.startsWith("whatsapp:") ||
            lower.contains("://wa.me") ||
            lower.contains("api.whatsapp.com") ||
            lower.contains("play.google.com")
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
