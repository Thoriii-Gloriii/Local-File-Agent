package com.example

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.io.File

private data class Template(val title: String, val desc: String, val icon: ImageVector, val command: String)

private val builtIn =
  listOf(
    // ── Discovery & Search ─────────────────────────────────────────────────
    Template("Show Downloads", "List everything in your Download folder", Icons.Outlined.FolderOpen, "list 'Download'"),
    Template("Storage usage", "Check how much space is used", Icons.Outlined.PieChart, "size"),
    Template("Find large files", "List files above your size threshold", Icons.Outlined.Storage, "large files"),
    Template("Find images", "Search all storage for image files", Icons.Outlined.Image, "find jpg"),
    Template("Find videos", "Search all storage for video files", Icons.Outlined.Movie, "find mp4"),
    Template("Find audio", "Search all storage for audio files", Icons.Outlined.MusicNote, "find mp3"),
    Template("Find PDFs", "Search all storage for PDF files", Icons.Outlined.Description, "find pdf"),
    Template("Find APKs", "List downloaded installer packages", Icons.Outlined.Android, "find apk"),
    // ── Organization ──────────────────────────────────────────────────────
    Template("Organize Downloads", "Sort Downloads into Images, Videos, Audio, Docs", Icons.Outlined.Download, "organize 'Download'"),
    Template("Organize storage", "Sort root storage loose files by type", Icons.Outlined.Folder, "organize 'root'"),
    Template("Clean empty folders", "Remove all empty subfolders", Icons.Outlined.DeleteSweep, "clean empty folders"),
    // ── DCIM & Camera ─────────────────────────────────────────────────────
    Template("Browse Camera", "Open your Camera folder", Icons.Outlined.CameraAlt, "list 'DCIM/Camera'"),
    Template("Find screenshots", "List all screenshots taken", Icons.Outlined.Screenshot, "list 'Pictures/Screenshots'"),
    // ── File Operations ───────────────────────────────────────────────────
    Template("Show Documents", "List your Documents folder", Icons.Outlined.Article, "list 'Documents'"),
    Template("Show Music", "List your Music folder", Icons.Outlined.LibraryMusic, "list 'Music'"),
    Template("Show Movies", "List your Movies folder", Icons.Outlined.VideoLibrary, "list 'Movies'"),
  )

@Composable
private fun TemplateCard(icon: ImageVector, title: String, desc: String, modifier: Modifier, onClick: () -> Unit) {
  Column(
    modifier
      .clip(RoundedCornerShape(14.dp))
      .background(AppCard)
      .border(0.5.dp, AppBorder, RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(14.dp)
  ) {
    Box(
      Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(AppRed.copy(alpha = 0.15f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, null, tint = AppRed)
    }
    Spacer(Modifier.height(10.dp))
    Text(title, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(2.dp))
    Text(desc, color = AppMuted, fontSize = 12.sp, lineHeight = 16.sp)
  }
}

@Composable
fun TemplatesScreen(onHelp: () -> Unit, onRun: (String) -> Unit) {
  val root = remember { Environment.getExternalStorageDirectory() }
  var pickedSaved by remember { mutableStateOf<String?>(null) }
  val saved = remember { File(root, ".FileAgentTemplates").listFiles()?.map { it.name }?.sorted() ?: emptyList() }

  LazyColumn(
    Modifier.fillMaxSize().background(AppBg),
    contentPadding = PaddingValues(bottom = 24.dp),
  ) {
    item { AppTopBar(title = "Command Catalog", onHelp = onHelp) }

    item {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
          "All Executable Commands",
          color = AppText,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
        )
        Text(
          "Tap any command to run it in the chat instantly.",
          color = AppMuted,
          fontSize = 13.sp,
          modifier = Modifier.padding(bottom = 10.dp),
        )
      }
    }

    items(builtIn.chunked(2)) { pair ->
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        pair.forEach { t ->
          TemplateCard(t.icon, t.title, t.desc, Modifier.weight(1f).fillMaxHeight()) { onRun(t.command) }
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
      }
    }

    if (saved.isNotEmpty()) {
      item {
        Text(
          "Your Saved Templates",
          color = AppText,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        )
      }
      items(saved.chunked(2)) { pair ->
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          pair.forEach { name ->
            TemplateCard(Icons.Outlined.BookmarkBorder, name, "Apply to a folder you choose", Modifier.weight(1f).fillMaxHeight()) {
              pickedSaved = name
            }
          }
          if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
      }
    }
  }

  pickedSaved?.let { name ->
    TextPromptDialog("Use '$name'", "Destination path", "Download/$name", "Apply", onConfirm = { dest ->
      pickedSaved = null
      onRun("use template '$name' at '$dest'")
    }, onDismiss = { pickedSaved = null })
  }
}
