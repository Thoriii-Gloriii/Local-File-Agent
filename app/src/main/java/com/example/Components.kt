package com.example

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.io.File
import java.util.Locale

fun formatSize(bytes: Long): String {
  if (bytes < 1024) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
  val mb = kb / 1024.0
  if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
  return String.format(Locale.US, "%.1f GB", mb / 1024.0)
}

fun fileCategory(file: File): String =
  when (file.extension.lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> "Images"
    "mp4", "mkv", "mov", "avi", "webm", "3gp" -> "Videos"
    "mp3", "wav", "m4a", "aac", "flac", "ogg" -> "Audio"
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf" -> "Documents"
    "apk" -> "Apps"
    else -> "Other"
  }

@Composable
fun Wordmark(big: Boolean) {
  val main = if (big) 40.sp else 18.sp
  val sub = if (big) 14.sp else 8.sp
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row {
      Text("Local ", fontSize = main, fontWeight = FontWeight.ExtraBold, color = AppText)
      Text("File", fontSize = main, fontWeight = FontWeight.ExtraBold, color = AppRed)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.width(if (big) 36.dp else 14.dp).height(2.dp).background(AppRed))
      Text(
        " AGENT ",
        fontSize = sub,
        letterSpacing = if (big) 8.sp else 3.sp,
        fontWeight = FontWeight.Bold,
        color = AppText,
      )
      Box(Modifier.width(if (big) 36.dp else 14.dp).height(2.dp).background(AppRed))
    }
  }
}

@Composable
fun AppTopBar(title: String? = null, onHelp: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (title == null) {
      Image(painterResource(R.drawable.logo_icon), null, Modifier.size(40.dp))
      Spacer(Modifier.width(8.dp))
      Wordmark(big = false)
    } else {
      Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppText)
    }
    Spacer(Modifier.weight(1f))
    IconButton(onClick = onHelp) { Icon(Icons.Default.Info, "Command help", tint = AppMuted) }
  }
}

@Composable
fun TextPromptDialog(
  title: String,
  label: String,
  initial: String,
  confirmText: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var text by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = AppCard,
    title = { Text(title, color = AppText) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) {
        Text(confirmText, color = AppRed)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AppMuted) } },
  )
}
