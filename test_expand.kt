import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("WrongConstant")
fun expandNotificationPanel(context: Context) {
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val expand = statusBarManager.getMethod("expandNotificationsPanel")
        expand.invoke(statusBarService)
    } catch (e: Exception) {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expand = statusBarManager.getMethod("expand")
            expand.invoke(statusBarService)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
