package app.locker.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;
public class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(40,120,40,40);
        root.setBackgroundColor(getPackageName().endsWith("red") ? Color.rgb(230,40,50) : Color.rgb(30,80,230));
        TextView label = new TextView(this); label.setText("SYNTHETIC PRIVATE CONTENT\n"+getPackageName()); label.setTextSize(26); label.setTextColor(Color.WHITE); root.addView(label);
        EditText input = new EditText(this); input.setHint("Synthetic keyboard test"); input.setTextColor(Color.WHITE); root.addView(input);
        setContentView(root);
    }
}
