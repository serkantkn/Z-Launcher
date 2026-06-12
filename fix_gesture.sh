sed -i '' 's/if (available.y > 0) {/if (available.y > 0 \&\& navState.currentHub == null) {/g' app/src/main/java/com/serkantkn/zunelauncher/ui/screens/LauncherScreen.kt
