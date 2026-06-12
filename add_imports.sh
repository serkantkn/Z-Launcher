sed -i '' -e '1a\
import android.annotation.SuppressLint\
import android.content.Context\
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection\
import androidx.compose.ui.input.nestedscroll.nestedScroll\
import androidx.compose.ui.platform.LocalContext\
import androidx.compose.runtime.mutableStateOf\
import androidx.compose.runtime.setValue\
' app/src/main/java/com/serkantkn/zunelauncher/ui/screens/LauncherScreen.kt
