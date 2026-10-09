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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun SettingsScreen(vm: ChatViewModel, onHelp: () -> Unit) {
  val context = LocalContext.current
  var showAbout by remember { mutableStateOf(false) }

  Column(Modifier.fillMaxSize().background(AppBg).verticalScroll(rememberScrollState())) {
    AppTopBar(title = "Settings", onHelp = onHelp)
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
      SettingsRow(Icons.Outlined.Palette, "Appearance", "Dark theme, red accent", null)
      HorizontalDivider(color = AppBorder.copy(alpha = 0.5f))
      SettingsRow(Icons.Outlined.DeleteOutline, "Clear chat history", "Start a fresh conversation") {
        vm.clearChat()
        Toast.makeText(context, "Chat cleared", Toast.LENGTH_SHORT).show()
      }
      HorizontalDivider(color = AppBorder.copy(alpha = 0.5f))
      SettingsRow(Icons.Outlined.Info, "About", "Version ${BuildConfig.VERSION_NAME}") { showAbout = true }
    }
    Spacer(Modifier.height(18.dp))
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
    Triple("large files", "Find files over 50 MB", Icons.Outlined.Storage),
    Triple("organize [folder]", "Sort files by type", Icons.Outlined.Folder),
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
