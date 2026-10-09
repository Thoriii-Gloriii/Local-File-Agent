package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.LlmState
import com.example.ui.theme.*

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)?) {
  Row(
    Modifier.fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(horizontal = 14.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(AppCard),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, null, tint = AppText)
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(title, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
      Text(subtitle, color = AppMuted, fontSize = 12.sp)
    }
    if (onClick != null) Icon(Icons.Filled.ChevronRight, null, tint = AppMuted)
  }
}

private fun formatThresholdLabel(mb: Int): String =
  if (mb < 1024) "$mb MB" else String.format("%.1f GB", mb / 1024f)

// Stepped values: 250 MB → 10 GB
private val thresholdSteps = listOf(250, 500, 750, 1024, 2048, 3072, 4096, 5120, 7680, 10240)

@Composable
fun SettingsScreen(vm: ChatViewModel, onHelp: () -> Unit, onThemeChange: (String) -> Unit = {}) {
  val context = LocalContext.current
  val prefs   = remember { context.getSharedPreferences("lfa", android.content.Context.MODE_PRIVATE) }

  var showAbout       by remember { mutableStateOf(false) }
  var showThemePicker by remember { mutableStateOf(false) }

  // ── Large file threshold ──────────────────────────────────────────────────
  val savedMb    = remember { prefs.getInt("large_file_threshold_mb", 500) }
  val savedIndex = remember { thresholdSteps.indexOfFirst { it >= savedMb }.coerceAtLeast(0) }
  var sliderPos  by remember { mutableFloatStateOf(savedIndex.toFloat()) }
  val currentMb  = thresholdSteps[sliderPos.toInt().coerceIn(0, thresholdSteps.lastIndex)]

  // ── Theme ─────────────────────────────────────────────────────────────────
  val savedTheme  = remember { prefs.getString("theme_mode", "dark") ?: "dark" }
  var activeTheme by remember { mutableStateOf(savedTheme) }
  val themeLabel  = when (activeTheme) { "light" -> "Light Mode ☀️" ; "system" -> "System Default ⚙️" ; else -> "Dark Mode 🌙" }

  Column(Modifier.fillMaxSize().background(AppBg).verticalScroll(rememberScrollState())) {
    AppTopBar(title = "Settings", onHelp = onHelp)

    // ── General section ───────────────────────────────────────────────────
    Column(
      Modifier.padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(AppSurface)
        .border(0.5.dp, AppBorder, RoundedCornerShape(14.dp))
    ) {
      SettingsRow(Icons.Outlined.Lock, "Permissions", "Manage all files access") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          try {
            context.startActivity(
              Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
              }
            )
          } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
          }
        }
      }
      HorizontalDivider(color = AppBorder.copy(alpha = 0.5f))
      SettingsRow(Icons.Outlined.Palette, "Appearance", themeLabel) { showThemePicker = true }
      HorizontalDivider(color = AppBorder.copy(alpha = 0.5f))
      SettingsRow(Icons.Outlined.DeleteOutline, "Clear chat history", "Start a fresh conversation") {
        vm.clearChat()
        Toast.makeText(context, "Chat cleared", Toast.LENGTH_SHORT).show()
      }
      HorizontalDivider(color = AppBorder.copy(alpha = 0.5f))
      SettingsRow(Icons.Outlined.Info, "About", "Version ${BuildConfig.VERSION_NAME}") { showAbout = true }
    }

    Spacer(Modifier.height(18.dp))

    // ── Local AI Brain (Offline LLM) Section ─────────────────────────────
    val llmState by vm.llmManager.state.collectAsState()
    val downloadProgress by vm.llmManager.downloadProgress.collectAsState()
    val statusMsg by vm.llmManager.statusMessage.collectAsState()

    Column(
      Modifier.padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(AppSurface)
        .border(0.5.dp, AppBorder, RoundedCornerShape(14.dp))
        .padding(16.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(AppCard),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Outlined.Psychology, null, tint = AppRed)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text("Local Offline AI Brain", color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
          Text(statusMsg, color = AppMuted, fontSize = 12.sp)
        }
        val badgeColor = when (llmState) {
          LlmState.READY -> Color(0xFF22C55E)
          LlmState.DOWNLOADING, LlmState.INITIALIZING -> Color(0xFFF59E0B)
          LlmState.ERROR -> AppRed
          LlmState.NOT_DOWNLOADED -> AppMuted
        }
        val badgeText = when (llmState) {
          LlmState.READY -> "Ready"
          LlmState.DOWNLOADING -> "${(downloadProgress * 100).toInt()}%"
          LlmState.INITIALIZING -> "Loading"
          LlmState.ERROR -> "Error"
          LlmState.NOT_DOWNLOADED -> "Offline"
        }
        Box(
          Modifier.clip(RoundedCornerShape(6.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(badgeText, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
      }

      if (llmState == LlmState.DOWNLOADING) {
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
          progress = { downloadProgress },
          modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
          color = AppRed,
          trackColor = AppBorder,
        )
      }

      Spacer(Modifier.height(12.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (llmState) {
          LlmState.NOT_DOWNLOADED, LlmState.ERROR -> {
            Button(
              onClick = { vm.llmManager.startDownload() },
              colors = ButtonDefaults.buttonColors(containerColor = AppRed, contentColor = Color.White),
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(10.dp),
            ) {
              Icon(Icons.Outlined.CloudDownload, null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Download Model (~850 MB)", fontSize = 12.sp)
            }
          }
          LlmState.READY -> {
            OutlinedButton(
              onClick = { vm.llmManager.deleteModel() },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = AppRed),
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(10.dp),
            ) {
              Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Delete Model", fontSize = 12.sp)
            }
          }
          LlmState.DOWNLOADING, LlmState.INITIALIZING -> {
            Text("Model is currently preparing. Please keep app open.", color = AppMuted, fontSize = 12.sp)
          }
        }
      }
    }

    Spacer(Modifier.height(18.dp))

    // ── Large File Threshold section ──────────────────────────────────────
    Column(
      Modifier.padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(AppSurface)
        .border(0.5.dp, AppBorder, RoundedCornerShape(14.dp))
        .padding(16.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(AppCard),
          contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Storage, null, tint = AppText) }
        Spacer(Modifier.width(12.dp))
        Column {
          Text("Large File Threshold", color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
          Text("Files above this size are flagged", color = AppMuted, fontSize = 12.sp)
        }
      }
      Spacer(Modifier.height(12.dp))
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("250 MB", color = AppMuted, fontSize = 11.sp)
        Text(
          formatThresholdLabel(currentMb),
          color = AppRed, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        )
        Text("10 GB", color = AppMuted, fontSize = 11.sp)
      }
      Slider(
        value = sliderPos,
        onValueChange = { sliderPos = it },
        onValueChangeFinished = {
          val mb = thresholdSteps[sliderPos.toInt().coerceIn(0, thresholdSteps.lastIndex)]
          prefs.edit().putInt("large_file_threshold_mb", mb).apply()
          vm.largeFileMb = mb.toLong()
        },
        valueRange = 0f..(thresholdSteps.lastIndex.toFloat()),
        steps = thresholdSteps.lastIndex - 1,
        colors = SliderDefaults.colors(
          thumbColor = AppRed,
          activeTrackColor = AppRed,
          inactiveTrackColor = AppBorder,
        ),
      )
    }

    Spacer(Modifier.height(18.dp))

    // ── Logo / About card ─────────────────────────────────────────────────
    Column(
      Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(AppCard)
        .border(0.5.dp, AppRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Image(painterResource(R.drawable.logo_icon), null, Modifier.size(64.dp))
      Spacer(Modifier.height(8.dp))
      Text("Local File Agent", color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
      Text("Smarter file management.\nRight from your chat.", color = AppMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
      Spacer(Modifier.height(10.dp))
      Text("Made with ♥ using Kotlin • Jetpack Compose • Material 3", color = AppMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(24.dp))
  }

  // ── About dialog ──────────────────────────────────────────────────────────
  if (showAbout) {
    AlertDialog(
      onDismissRequest = { showAbout = false },
      containerColor = AppCard,
      title = { Text("Local File Agent", color = AppText) },
      text = {
        Text(
          "Version ${BuildConfig.VERSION_NAME}\nAn offline file manager you control with plain-language commands. Nothing leaves your phone.",
          color = AppMuted,
        )
      },
      confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close", color = AppRed) } },
    )
  }

  // ── Theme picker dialog ───────────────────────────────────────────────────
  if (showThemePicker) {
    AlertDialog(
      onDismissRequest = { showThemePicker = false },
      containerColor = AppCard,
      title = { Text("Appearance", color = AppText) },
      text = {
        Column {
          listOf(
            "dark"   to "Dark Mode 🌙",
            "light"  to "Light Mode ☀️",
            "system" to "System Default ⚙️",
          ).forEach { (key, label) ->
            Row(
              Modifier.fillMaxWidth()
                .clickable {
                  activeTheme = key
                  prefs.edit().putString("theme_mode", key).apply()
                  onThemeChange(key)
                  showThemePicker = false
                }
                .padding(vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(
                selected = activeTheme == key,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = AppRed),
              )
              Spacer(Modifier.width(8.dp))
              Text(label, color = AppText, fontSize = 15.sp)
            }
          }
        }
      },
      confirmButton = {},
    )
  }
}

private val helpItems =
  listOf(
    Triple("list / show", "List files in a folder", Icons.Outlined.FormatListBulleted),
    Triple("read [file]", "Show a file's content", Icons.Outlined.Description),
    Triple("write [text] to [file]", "Create or overwrite a file", Icons.Outlined.Edit),
    Triple("delete [file]", "Delete a file or folder", Icons.Outlined.Delete),
    Triple("move [file] to [path]", "Move a file or folder", Icons.Outlined.DriveFileMove),
    Triple("copy [file] to [path]", "Copy a file or folder", Icons.Outlined.ContentCopy),
    Triple("rename [old] to [new]", "Rename a file or folder", Icons.Outlined.DriveFileRenameOutline),
    Triple("find [name]", "Search names and contents", Icons.Outlined.Search),
    Triple("preview [file]", "Preview an image or text file", Icons.Outlined.Visibility),
    Triple("size", "Show storage usage", Icons.Outlined.PieChart),
    Triple("large files", "Find files above your threshold", Icons.Outlined.Storage),
    Triple("organize [folder]", "Sort files by type", Icons.Outlined.Folder),
    Triple("clean empty folders", "Remove empty subfolders", Icons.Outlined.DeleteSweep),
    Triple("create template 'name' from 'path'", "Save a folder as template", Icons.Outlined.Bookmark),
    Triple("use template 'name' at 'path'", "Apply a saved template", Icons.Outlined.BookmarkBorder),
  )

@Composable
fun HelpScreen(onBack: () -> Unit) {
  Column(Modifier.fillMaxSize().background(AppBg).statusBarsPadding().verticalScroll(rememberScrollState())) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppText) }
      Text("Command Help", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = AppText)
    }
    Column(
      Modifier.padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(AppSurface)
        .border(0.5.dp, AppBorder, RoundedCornerShape(14.dp))
    ) {
      helpItems.forEachIndexed { i, (cmd, desc, icon) ->
        SettingsRow(icon, cmd, desc, null)
        if (i < helpItems.lastIndex) HorizontalDivider(color = AppBorder.copy(alpha = 0.5f))
      }
    }
    Spacer(Modifier.height(24.dp))
  }
}
