package app.locker.plus

/** Offline suggestions from installed launcher apps only. Never enables protection automatically. */
fun suggestedCategory(pkg: String): String? = when (pkg) {
    "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
    "com.facebook.orca" -> "Private conversations"
    "com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms" -> "Messages & codes"
    "com.sec.android.gallery3d", "com.google.android.apps.photos", "com.android.gallery3d" -> "Personal photos"
    "com.samsung.android.app.notes", "com.google.android.keep" -> "Private notes"
    "com.google.android.gm", "com.microsoft.office.outlook" -> "Email"
    "com.sec.android.app.myfiles", "com.google.android.apps.nbu.files" -> "Personal files"
    else -> null
}
