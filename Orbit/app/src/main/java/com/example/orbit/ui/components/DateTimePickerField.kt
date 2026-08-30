package com.example.orbit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.orbit.ui.util.combineDateAndTime
import com.example.orbit.ui.util.formatEventDateTime


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerField(
    value: Long?,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {

        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (value == null) "Pick start date & time"
                else formatEventDateTime(value)
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    // date
    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = value)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDateMillis = dateState.selectedDateMillis
                        showDatePicker = false
                    },
                ) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    // time
    val dateMillis = pendingDateMillis
    if (dateMillis != null) {
        val timeState = rememberTimePickerState(is24Hour = true)

        AlertDialog(
            onDismissRequest = { pendingDateMillis = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(
                            combineDateAndTime(dateMillis, timeState.hour, timeState.minute)
                        )
                        pendingDateMillis = null
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDateMillis = null }) { Text("Cancel") }
            },
            title = { Text("Start time") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TimePicker(state = timeState)
                }
            },
        )
    }
}
