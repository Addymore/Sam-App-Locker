package app.locker.plus

/** Cancellation is not consent to switch authentication methods. */
fun credentialVisible(biometricAvailable: Boolean, explicitlySelected: Boolean, biometricActive: Boolean) =
    !biometricActive && (!biometricAvailable || explicitlySelected)

enum class VisualStyle(val label: String) { MATERIAL("Material 3"), ONE_UI("One UI 9 inspired"), COLOR_OS("ColorOS 17 inspired") }
enum class ColorMode(val label: String) { SYSTEM("Follow phone"), LIGHT("Light"), DARK("Dark") }
enum class Backdrop(val label: String) { PLAIN("Plain"), AURORA("Aurora"), OCEAN("Ocean"), SUNSET("Sunset") }
data class Appearance(val style: VisualStyle = VisualStyle.MATERIAL, val mode: ColorMode = ColorMode.SYSTEM,
    val background: Backdrop = Backdrop.PLAIN, val dynamic: Boolean = true)

inline fun <reified T : Enum<T>> savedEnum(value: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback
