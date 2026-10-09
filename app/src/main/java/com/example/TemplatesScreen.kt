package com.example

import android.os.Environment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Template(val title: String, val desc: String, val icon: ImageVector, val command: String)

private val builtIn =
  listOf(
    Template("Organize Downloads", "Sort files into Images, Videos, Audio, Documents", Icons.Outlined.Download, "organize 'Download'"),
    Template("Organize storage", "Sort loose files in the main folder by type", Icons.Outlined.Folder, "organize 'root'"),
    Template("Find large files", "List the biggest files over 50 MB", Icons.Outlined.Storage, "large files"),
    Template("Clean empty folders", "Remove empty subfolders", Icons.Outlined.DeleteSweep, "clean empty folders"),
    Template("Find images", "Search storage for JPG files", Icons.Outlined.Image, "find jpg"),
    Template("Show downloads", "List what's in your Download folder", Icons.Outlined.FolderOpen, "list 'Download'"),
  )

private val categoryColors =
  linkedMapOf(
    "Apps" to AppRed,
    "Images" to Color(0xFFFF7A3D),
    "Videos" to Color(0xFFFFA63D),
    "Audio" to Color(0xFFFFD23D),
    "Documents" to Color(0xFF5B8DEF),
    "Other" to Color(0xFF7A8CA8),
  )

private fun scanCategories(root: File): Map<String, Long> {
  val totals = linkedMapOf<String, Long>()
  categoryColors.keys.forEach { totals[it] = 0L }
  var visited = 0
  try {
    root
      .walkTopDown()
      .onEnter { !it.name.startsWith(".") && it.name != "Android" }
      .forEach {
        if (it.isFile) {
          val c = fileCategory(it)
          totals[c] = (totals[c] ?: 0L) + it.length()
          visited++
          if (visited > 300_000) return totals
        }
      }
  } catch (e: Exception) {}
  return totals
}

@Composable
fun StorageRing(fraction: Float, modifier: Modifier = Modifier.size(190.dp)) {
  val anim by animateFloatAsState(fraction, tween(900), label = "ring")
  Box(modifier, contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      val stroke = 18.dp.toPx()
      val topLeft = Offset(stroke / 2, stroke / 2)
      val arcSize = Size(size.width - stroke, size.height - stroke)
      drawArc(AppBorder, 0f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
      drawArc(
        AppRed,
        -90f,
        360f * anim,
        false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text("${(anim * 100).roundToInt()}%", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = AppText)
      Text("Used", fontSize = 14.sp, color = AppMuted)
    }
  }
}

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
  val total = root.totalSpace
  val used = total - root.freeSpace
  val fraction = if (total > 0) used.toFloat() / total else 0f
  var cats by remember { mutableStateOf<Map<String, Long>?>(null) }
  var pickedSaved by remember { mutableStateOf<String?>(null) }
  val saved = remember { File(root, ".FileAgentTemplates").listFiles()?.map { it.name }?.sorted() ?: emptyList() }

  LaunchedEffect(Unit) { cats = withContext(Dispatchers.IO) { scanCategories(root) } }

  LazyColumn(
    Modifier.fillMaxSize().background(AppBg),
    contentPadding = PaddingValues(bottom = 24.dp),
  ) {
    item { AppTopBar(title = "Templates", onHelp = onHelp) }
    item {
      Column(
        Modifier.fillMaxWidth()
          .padding(horizontal = 16.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(AppSurface)
          .border(0.5.dp, AppBorder, RoundedCornerShape(16.dp))
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        StorageRing(fraction)
        Spacer(Modifier.height(12.dp))
        Text("${formatSize(used)} / ${formatSize(total)}", color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text("Internal Storage", color = AppMuted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        categoryColors.forEach { (name, color) ->
          Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Text(name, color = AppText, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(cats?.get(name)?.let { formatSize(it) } ?: "Scanning…", color = AppMuted, fontSize = 13.sp)
          }
        }
      }
    }
    item {
      Text(
        "Templates",
        color = AppText,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp),
      )
      Text(
        "Tap a template to run it in the chat.",
        color = AppMuted,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
      )
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
          "Your saved templates",
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
