package com.openring.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.openring.agent.ChatLogEntry
import com.openring.agent.ExecutionLogStore
import com.openring.ui.theme.Spacing
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

private fun previewJsonObject(
    obj: JsonObject,
    maxKeys: Int = 8,
    maxValueChars: Int = 80
): String {
    if (obj.isEmpty()) return ""
    val entries = obj.entries.take(maxKeys)
    val head = entries.joinToString(", ") { (k, v) ->
        val valueStr = when (v) {
            is JsonPrimitive -> {
                val raw = v.content.replace("\n", " ").replace("\r", " ").trim()
                if (raw.length <= maxValueChars) raw else raw.take(maxValueChars) + "…"
            }

            else -> "<complex>"
        }
        "$k=$valueStr"
    }
    val suffix = if (obj.size > maxKeys) ", …(+${obj.size - maxKeys})" else ""
    return head + suffix
}

private fun previewToolResult(result: JsonObject): Pair<String?, String?> {
    val ok = (result["ok"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
    val code = (result["code"] as? JsonPrimitive)?.content
    val message = (result["message"] as? JsonPrimitive)?.content
    if (ok == true) return "成功" to null
    val msgShort = message?.take(60)
    return "失敗" to listOfNotNull(code, msgShort).joinToString(": ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val entries by ExecutionLogStore.entries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("執行 Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val payload = ExecutionLogStore.snapshotAsJsonArray().toString()
                            clipboard.setText(AnnotatedString(payload))
                            Toast.makeText(context, "已複製全部 JSON", Toast.LENGTH_SHORT).show()
                        },
                        enabled = entries.isNotEmpty()
                    ) {
                        Text("全部複製 JSON")
                    }
                }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            color = MaterialTheme.colorScheme.background
        ) {
            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "目前沒有執行 log。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Spacing.md,
                        vertical = Spacing.md
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    itemsIndexed(entries, key = { index, item ->
                        when (item) {
                            is ChatLogEntry.Text -> "text_${item.createdAtMs}_${index}"
                            is ChatLogEntry.ToolCall -> "call_${item.toolName}_${item.createdAtMs}_${index}"
                            is ChatLogEntry.ToolResult -> "result_${item.toolName}_${item.createdAtMs}_${index}"
                        }
                    }) { _, entry ->
                        LogEntryCard(
                            entry = entry,
                            onCopyEntryJson = { json ->
                                clipboard.setText(AnnotatedString(json))
                                Toast.makeText(context, "已複製 JSON", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(
    entry: ChatLogEntry,
    onCopyEntryJson: (String) -> Unit
) {
    val entryKey = remember(entry) {
        when (entry) {
            is ChatLogEntry.Text -> "text_${entry.createdAtMs}"
            is ChatLogEntry.ToolCall -> "call_${entry.toolName}_${entry.createdAtMs}"
            is ChatLogEntry.ToolResult -> "result_${entry.toolName}_${entry.createdAtMs}"
        }
    }
    var expanded by remember(entryKey) { mutableStateOf(false) }

    val headerIcon =
        when (entry) {
            is ChatLogEntry.ToolCall -> Icons.Filled.Tune
            is ChatLogEntry.ToolResult -> {
                val ok = (entry.result["ok"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
                if (ok == true) Icons.Filled.CheckCircle else Icons.Filled.Close
            }

            is ChatLogEntry.Text -> Icons.Filled.Tune
        }

    val title = when (entry) {
        is ChatLogEntry.Text -> "訊息"
        is ChatLogEntry.ToolCall -> "tool_call: ${entry.toolName}"
        is ChatLogEntry.ToolResult -> {
            val (status, detail) = previewToolResult(entry.result)
            if (detail.isNullOrBlank()) "tool_result: ${entry.toolName} (${status})"
            else "tool_result: ${entry.toolName} (${status})"
        }
    }

    val preview = when (entry) {
        is ChatLogEntry.Text -> entry.message.take(120)
        is ChatLogEntry.ToolCall -> previewJsonObject(entry.args)
        is ChatLogEntry.ToolResult -> previewJsonObject(entry.result)
    }

    val jsonToCopy = when (entry) {
        is ChatLogEntry.ToolCall -> entry.args.toString()
        is ChatLogEntry.ToolResult -> entry.result.toString()
        is ChatLogEntry.Text -> entry.toJsonElement().toString()
    }

    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(headerIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (preview.isNotBlank()) {
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TextButton(onClick = { onCopyEntryJson(jsonToCopy) }) {
                        Text("複製 JSON")
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expanded) "收合" else "展開"
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.size(Spacing.sm))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.size(Spacing.sm))
                when (entry) {
                    is ChatLogEntry.ToolCall -> {
                        JsonElementView(
                            element = entry.args,
                            depth = 0,
                            path = "log_${entryKey}_args",
                            maxDepth = 7,
                            maxChildren = 20
                        )
                    }

                    is ChatLogEntry.ToolResult -> {
                        JsonElementView(
                            element = entry.result,
                            depth = 0,
                            path = "log_${entryKey}_result",
                            maxDepth = 7,
                            maxChildren = 20
                        )
                    }

                    is ChatLogEntry.Text -> {
                        Text(
                            text = entry.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

