package com.tvtools.app.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ShellTab(viewModel: ShellViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsState()
    val scroll = rememberScrollState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(ui.output) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .imePadding(),
    ) {
        Text(
            text = ui.output,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            FilledTonalButton(onClick = viewModel::ctrlC, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Close, null, Modifier.width(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("CTRL+C", fontSize = 12.sp)
            }
            FilledTonalButton(onClick = viewModel::clear, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Clear, null, Modifier.width(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear", fontSize = 12.sp)
            }
            FilledTonalButton(
                onClick = { clipboard.setText(AnnotatedString(ui.output)) },
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Copy", fontSize = 12.sp)
            }
            if (!ui.connected) {
                FilledTonalButton(onClick = viewModel::reconnect, shape = RoundedCornerShape(12.dp)) {
                    Text("Reconnect", fontSize = 12.sp)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            OutlinedTextField(
                value = ui.input,
                onValueChange = viewModel::setInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Shell command") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = viewModel::send, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
