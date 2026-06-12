package com.serkantkn.zunelauncher.ui.screens.clock

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockHubScreen(
    onBack: () -> Unit,
    viewModel: ClockHubViewModel = viewModel()
) {
    val alarms by viewModel.alarms.collectAsState()
    val zuneColors = LocalZuneColors.current
    
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = ZuneDimens.SpacingMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "saat",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Alarm",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
            contentPadding = PaddingValues(bottom = ZuneDimens.SpacingXl)
        ) {
            items(alarms, key = { it.id }) { alarm ->
                AlarmItem(
                    alarm = alarm,
                    onToggle = { viewModel.toggleAlarm(alarm) },
                    onDelete = { viewModel.deleteAlarm(alarm) }
                )
            }
            
            if (alarms.isEmpty()) {
                item {
                    Text(
                        text = "Henüz bir alarm kurmadınız.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = zuneColors.textMuted,
                        modifier = Modifier.padding(top = ZuneDimens.SpacingLg)
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddAlarmDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { hour, minute, label ->
                viewModel.addAlarm(Alarm(hour = hour, minute = minute, label = label))
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AlarmItem(
    alarm: Alarm,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var isDeleting by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZuneDimens.SpacingSm)
            .clickable { isDeleting = !isDeleting },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alarm.timeString,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light),
                color = if (alarm.isEnabled) MaterialTheme.colorScheme.onBackground else zuneColors.textMuted
            )
            if (alarm.label.isNotEmpty()) {
                Text(
                    text = alarm.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textMuted
                )
            }
        }

        if (isDeleting) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = zuneColors.accentColor
                )
            }
        } else {
            Switch(
                checked = alarm.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.background,
                    checkedTrackColor = zuneColors.accentColor,
                    uncheckedThumbColor = zuneColors.textMuted,
                    uncheckedTrackColor = Color.Transparent
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAlarmDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, String) -> Unit
) {
    val timePickerState = rememberTimePickerState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute, "Alarm")
            }) {
                Text("Kaydet", color = LocalZuneColors.current.accentColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = LocalZuneColors.current.textMuted)
            }
        },
        text = {
            TimePicker(state = timePickerState)
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}
