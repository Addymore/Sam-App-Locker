package app.locker.plus
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
/** Synthetic UI harness, excluded from release/debug APKs. */
class CredentialHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName.endsWith(".testbed"))
        val kind = CredentialKind.valueOf(intent.getStringExtra("kind")!!)
        val expected = when (kind) { CredentialKind.PIN -> "246813"; CredentialKind.PATTERN -> "01258"; CredentialKind.PASSWORD -> "Kigali words 123!" }
        val appearance = Appearance(
            savedEnum(intent.getStringExtra("style"), VisualStyle.MATERIAL),
            savedEnum(intent.getStringExtra("mode"), ColorMode.SYSTEM),
            savedEnum(intent.getStringExtra("backdrop"), Backdrop.PLAIN), false)
        setContent { LockerTheme(appearance = appearance) {
            var accepted by remember { mutableStateOf(false) }
            if (accepted) Text("Synthetic credential accepted", Modifier.safeDrawingPadding())
            else AuthPanel("Synthetic test", "No private credentials", credentialKind = kind,
                autoPinLength = if (kind == CredentialKind.PIN) 6 else 0,
                onBiometric = {}, onPin = { value, done -> accepted = value.concatToString() == expected; value.fill('\u0000'); done() })
        } }
    }
}
