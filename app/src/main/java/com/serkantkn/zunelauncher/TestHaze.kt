import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeTint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun test() {
    val tint = HazeTint(Color.Black)
    val style = HazeStyle(tint = tint, blurRadius = 15.dp)
}
