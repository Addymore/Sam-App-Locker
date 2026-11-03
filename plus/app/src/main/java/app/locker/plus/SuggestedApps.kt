package app.locker.plus

/** Offline suggestions from installed launcher apps only. Never enables protection automatically. */
fun suggestedCategory(pkg: String): String? = when (pkg) {
    "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
    "com.facebook.orca", "com.discord", "com.viber.voip" -> "Private conversations"
    "com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms" -> "Messages & codes"
    "com.sec.android.gallery3d", "com.google.android.apps.photos", "com.android.gallery3d" -> "Personal photos"
    "com.instagram.android", "com.facebook.katana", "com.zhiliaoapp.musically",
    "com.snapchat.android", "com.twitter.android", "com.reddit.frontpage" -> "Social media"
    "com.samsung.android.app.notes", "com.google.android.keep", "com.notion.id" -> "Private notes"
    "com.google.android.gm", "com.microsoft.office.outlook", "com.samsung.android.email.provider" -> "Email"
    "com.sec.android.app.myfiles", "com.google.android.apps.nbu.files", "com.google.android.apps.docs" -> "Personal files"
    "com.samsung.android.spay", "com.google.android.apps.walletnfcrel", "com.paypal.android.p2pmobile" -> "Wallet & finance"
    else -> null
}

fun appCategoryBadge(pkg: String, system: Boolean): String {
    suggestedCategory(pkg)?.let { return it }
    val lower = pkg.lowercase()
    return when {
        "bank" in lower || "pay" in lower || "wallet" in lower || "finance" in lower -> "Wallet & finance"
        "camera" in lower || "gallery" in lower || "photo" in lower || "video" in lower -> "Media"
        "browser" in lower || "chrome" in lower || "sbrowser" in lower || "firefox" in lower -> "Browser"
        "mail" in lower || "chat" in lower || "messag" in lower || "social" in lower -> "Communication"
        system -> "System app"
        else -> "Downloaded app"
    }
}
