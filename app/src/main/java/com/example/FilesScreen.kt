package com.example

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun categoryStyle(f: File): Pair<ImageVector, Color> =
  if (f.isDirectory) Icons.Filled.Folder to Color(0xFFF5A623)
  else
    when (fileCategory(f)) {
      "Images" -> Icons.Filled.Image to Color(0xFF9B6BFF)
      "Videos" -> Icons.Filled.Movie to Color(0xFF3DA5FF)
      "Audio" -> Icons.Filled.MusicNote to Color(0xFFFF4D4D)
      "Documents" -> Icons.Filled.Description to Color(0xFFF5A623)
      "Apps" -> Icons.Filled.Android to Color(0xFFFF6B4D)
      else -> Icons.Filled.InsertDriveFile to Color(0xFF8E8E96)
    }

private fun dateOf(f: File) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(f.lastModified()))

@Composable
fun FilesScreen(vm: ChatViewModel, onHelp: () -> Unit, onGoChat: () -> Unit) {
  val root = remember { Environment.getExternalStorageDirectory() }
  var currentPath by rememberSaveable { mutableStateOf(root.absolutePath) }
  var selected by rememberSaveable { mutableStateOf<String?>(null) }
  var showNewFolder by remember { mutableStateOf(false) }
  var refresh by remember { mutableIntStateOf(0) }

  BackHandler(enabled = selected != null || currentPath != root.absolutePath) {
    if (selected != null) selected = null else File(currentPath).parent?.let { currentPath = it }
  }

  val sel = selected
  if (sel != null) {
    FileDetails(File(sel), vm, onBack = { selected = null }, onGoChat = onGoChat)
    return
  }

  val dir = File(currentPath)
  val entries =
    remember(currentPath, refresh) {
      (dir.listFiles()?.toList() ?: emptyList())
        .filter { !it.name.startsWith(".") }
        .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

  Column(Modifier.fillMaxSize().background(AppBg)) {
    AppTopBar(onHelp = onHelp)
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(
          if (currentPath == root.absolutePath) "Internal Storage" else dir.name,
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = AppText,
        )
        Text(currentPath, fontSize = 12.sp, color = AppMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      IconButton(onClick = { showNewFolder = true }) {
        Icon(Icons.Outlined.CreateNewFolder, "New folder", tint = AppRed)
      }
    }
    val totalSpace = root.totalSpace
    val freeSpace = root.freeSpace
    val usedSpace = (totalSpace - freeSpace).coerceAtLeast(0L)
    val usedFraction = if (totalSpace > 0) (usedSpace.toFloat() / totalSpace).coerceIn(0f, 1f) else 0f

    // ── Storage Space Analysis Card ───────────────────────────────────────
    Column(
      Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(AppCard)
        .border(0.5.dp, AppBorder, RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Outlined.PieChart, null, tint = AppRed, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Internal Storage", color = AppText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
          "${formatSize(usedSpace)} / ${formatSize(totalSpace)} (${(usedFraction * 100).toInt()}%)",
          color = AppMuted,
          fontSize = 12.sp,
        )
      }
      Spacer(Modifier.height(6.dp))
      LinearProgressIndicator(
        progress = { usedFraction },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = AppRed,
        trackColor = AppBorder,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        "${formatSize(freeSpace)} free",
        color = AppMuted,
        fontSize = 11.sp,
        modifier = Modifier.align(Alignment.End),
      )
    }

    Spacer(Modifier.height(6.dp))
    Box(
      Modifier.weight(1f)
        .padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(AppSurface)
        .border(0.5.dp, AppBorder, RoundedCornerShape(14.dp))
    ) {
      if (entries.isEmpty()) {
        Text("This folder is empty.", color = AppMuted, modifier = Modifier.padding(20.dp))
      }
      LazyColumn {
        items(entries, key = { it.absolutePath }) { f ->
          val (icon, tint) = categoryStyle(f)
          Row(
            Modifier.fillMaxWidth()
              .clickable { if (f.isDirectory) currentPath = f.absolutePath else selected = f.absolutePath }
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(icon, null, tint = tint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              Text(f.name, color = AppText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(
                if (f.isDirectory) "${f.list()?.size ?: 0} items"
                else "${formatSize(f.length())} • ${dateOf(f)}",
                color = AppMuted,
                fontSize = 12.sp,
              )
            }
            if (f.isDirectory) Icon(Icons.Filled.ChevronRight, null, tint = AppMuted)
          }
          HorizontalDivider(color = AppBorder.copy(alpha = 0.5f))
        }
      }
    }
    Spacer(Modifier.height(12.dp))
  }

  if (showNewFolder) {
    TextPromptDialog("New folder", "Folder name", "", "Create", onConfirm = { name ->
      File(dir, name).mkdirs()
      refresh++
      showNewFolder = false
    }, onDismiss = { showNewFolder = false })
  }
}

private fun openOrShare(context: Context, file: File, share: Boolean) {
  try {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
    val intent =
      if (share)
        Intent(Intent.ACTION_SEND).apply {
          type = mime
          putExtra(Intent.EXTRA_STREAM, uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      else
        Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(uri, mime)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(if (share) Intent.createChooser(intent, null) else intent)
  } catch (e: Exception) {
    Toast.makeText(context, "Couldn't open this file", Toast.LENGTH_SHORT).show()
  }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, danger: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val tint = if (danger) AppRed else AppText
  Column(
    modifier
      .clip(RoundedCornerShape(12.dp))
      .background(AppCard)
      .border(0.5.dp, if (danger) AppRed.copy(alpha = 0.5f) else AppBorder, RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(icon, label, tint = tint)
    Spacer(Modifier.height(6.dp))
    Text(label, color = tint, fontSize = 13.sp)
  }
}

@Composable
private fun FileDetails(file: File, vm: ChatViewModel, onBack: () -> Unit, onGoChat: () -> Unit) {
  val context = LocalContext.current
  var dialog by remember { mutableStateOf<String?>(null) }
  val bitmap =
    remember(file.path) {
      runCatching {
          val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          BitmapFactory.decodeFile(file.path, bounds)
          var s = 1
          while (bounds.outWidth / s > 1200) s *= 2
          BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
            ?.asImageBitmap()
        }
        .getOrNull()
    }

  Column(Modifier.fillMaxSize().background(AppBg).statusBarsPadding().verticalScroll(rememberScrollState())) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppText) }
      Text("File Details", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = AppText)
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
      if (bitmap != null) {
        Image(
          bitmap,
          file.name,
          Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)),
          contentScale = ContentScale.Crop,
        )
      } else {
        val (icon, tint) = categoryStyle(file)
        Box(
          Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(icon, null, tint = tint, modifier = Modifier.size(56.dp))
        }
      }
      Spacer(Modifier.height(14.dp))
      Text(file.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppText)
      Text(
        "${formatSize(file.length())} • ${fileCategory(file).removeSuffix("s")} • ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(file.lastModified()))}",
        fontSize = 13.sp,
        color = AppMuted,
      )
      Spacer(Modifier.height(16.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile(Icons.Outlined.OpenInNew, "Open", false, Modifier.weight(1f)) { openOrShare(context, file, false) }
        ActionTile(Icons.Outlined.Share, "Share", false, Modifier.weight(1f)) { openOrShare(context, file, true) }
        ActionTile(Icons.Outlined.Edit, "Rename", false, Modifier.weight(1f)) { dialog = "rename" }
      }
      Spacer(Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile(Icons.Outlined.DriveFileMove, "Move", false, Modifier.weight(1f)) { dialog = "move" }
        ActionTile(Icons.Outlined.ContentCopy, "Copy", false, Modifier.weight(1f)) { dialog = "copy" }
        ActionTile(Icons.Outlined.Delete, "Delete", true, Modifier.weight(1f)) {
          vm.requestDelete(file.absolutePath)
          onGoChat()
        }
      }
      Spacer(Modifier.height(16.dp))
      Row(
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(AppCard)
          .border(0.5.dp, AppBorder, RoundedCornerShape(12.dp))
          .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Filled.Folder, null, tint = Color(0xFFF5A623))
        Spacer(Modifier.width(10.dp))
        Column {
          Text("Location", color = AppMuted, fontSize = 12.sp)
          Text(file.parent ?: "", color = AppText, fontSize = 13.sp)
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }

  when (dialog) {
    "rename" ->
      TextPromptDialog("Rename", "New name", file.name, "Rename", onConfirm = { name ->
        vm.requestMove(file.absolutePath, File(file.parentFile, name).absolutePath)
        dialog = null
        onGoChat()
      }, onDismiss = { dialog = null })
    "move",
    "copy" ->
      TextPromptDialog(
        if (dialog == "move") "Move to folder" else "Copy to folder",
        "Destination folder",
        file.parent ?: "",
        if (dialog == "move") "Move" else "Copy",
        onConfirm = { folder ->
          val dest = File(vm.resolveFile(folder), file.name).absolutePath
          if (dialog == "move") vm.requestMove(file.absolutePath, dest) else vm.requestCopy(file.absolutePath, dest)
          dialog = null
          onGoChat()
        },
        onDismiss = { dialog = null },
      )
  }
}
