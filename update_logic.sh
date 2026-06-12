# Remove logic from HomeHubScreen.kt
sed -i '' -e '/override fun onPreScroll/,/return androidx.compose.ui.geometry.Offset.Zero/d' app/src/main/java/com/serkantkn/zunelauncher/ui/screens/home/HomeHubScreen.kt
sed -i '' -e '/override fun onPostScroll/,/return androidx.compose.ui.geometry.Offset.Zero/d' app/src/main/java/com/serkantkn/zunelauncher/ui/screens/home/HomeHubScreen.kt
sed -i '' -e 's/var accumulatedOverscroll by remember { mutableStateOf(0f) }//g' app/src/main/java/com/serkantkn/zunelauncher/ui/screens/home/HomeHubScreen.kt
sed -i '' -e 's/accumulatedOverscroll = 0f//g' app/src/main/java/com/serkantkn/zunelauncher/ui/screens/home/HomeHubScreen.kt
sed -i '' -e '/fun expandNotificationPanel/,/^}/d' app/src/main/java/com/serkantkn/zunelauncher/ui/screens/home/HomeHubScreen.kt
