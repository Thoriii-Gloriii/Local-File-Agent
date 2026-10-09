package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )
    setContent {
      val prefs = getSharedPreferences("lfa", Context.MODE_PRIVATE)
      var theme by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }
      MyApplicationTheme(theme = theme) {
        Surface(modifier = Modifier.fillMaxSize(), color = AppBg) {
          AppRoot(onThemeChange = { theme = it })
        }
      }
    }
  }
}

@Composable
fun AppRoot(onThemeChange: (String) -> Unit = {}) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("lfa", Context.MODE_PRIVATE) }
  var stage by rememberSaveable { mutableIntStateOf(0) }
  when (stage) {
    0 -> SplashScreen { stage = if (prefs.getBoolean("onboarded", false)) 2 else 1 }
    1 ->
      OnboardingScreen {
        prefs.edit().putBoolean("onboarded", true).apply()
        stage = 2
      }
    else -> PermissionWrapper { MainScaffold(onThemeChange = onThemeChange) }
  }
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
  LaunchedEffect(Unit) {
    delay(1600)
    onDone()
  }
  Column(
    Modifier.fillMaxSize().background(AppBg).padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Image(painterResource(R.drawable.logo_icon), null, Modifier.size(150.dp))
    Spacer(Modifier.height(12.dp))
    Wordmark(big = true)
    Spacer(Modifier.height(80.dp))
    Text("Your files. Just a chat away.", color = AppMuted, fontSize = 14.sp)
    Spacer(Modifier.height(14.dp))
    LinearProgressIndicator(
      modifier = Modifier.fillMaxWidth(0.6f),
      color = AppRed,
      trackColor = AppBorder,
    )
  }
}

@Composable
fun OnboardingScreen(onStart: () -> Unit) {
  Column(
    Modifier.fillMaxSize().background(AppBg).padding(horizontal = 28.dp, vertical = 40.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.weight(1f))
    Image(painterResource(R.drawable.logo_icon), null, Modifier.size(130.dp))
    Spacer(Modifier.height(8.dp))
    Wordmark(big = true)
    Spacer(Modifier.height(48.dp))
    Text(
      "Manage Your Files\nwith Natural Language",
      color = AppText,
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(
      "Just type what you want to do — search, move, delete, rename and more.\nNo more endless menus.",
      color = AppMuted,
      fontSize = 14.sp,
      lineHeight = 21.sp,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.weight(1f))
    Button(
      onClick = onStart,
      modifier = Modifier.fillMaxWidth().height(54.dp),
      shape = RoundedCornerShape(28.dp),
      colors = ButtonDefaults.buttonColors(containerColor = AppRed, contentColor = Color.White),
    ) {
      Text("Get Started", fontWeight = FontWeight.Bold)
      Spacer(Modifier.width(8.dp))
      Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
    }
    Spacer(Modifier.height(16.dp))
    Text("Built with Kotlin • Jetpack Compose • Material 3", color = AppMuted, fontSize = 11.sp)
  }
}

@Composable
fun PermissionWrapper(content: @Composable () -> Unit) {
  val context = LocalContext.current
  var hasPermission by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
      else true
    )
  }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        hasPermission = Environment.isExternalStorageManager()
      }
    }

  if (hasPermission) {
    content()
    return
  }

  Box(Modifier.fillMaxSize().background(AppBg).padding(24.dp), contentAlignment = Alignment.Center) {
    Column(
      Modifier.fillMaxWidth()
        .background(AppSurface, RoundedCornerShape(20.dp))
        .border(0.5.dp, AppBorder, RoundedCornerShape(20.dp))
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(Icons.Outlined.Shield, null, tint = AppRed, modifier = Modifier.size(52.dp))
      Spacer(Modifier.height(12.dp))
      Text("Storage Permission", color = AppText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(8.dp))
      Text(
        "Local File Agent needs access to your files to help you manage them.",
        color = AppMuted,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(16.dp))
      listOf("Read your files", "Create and delete files", "Access storage folders").forEach {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Check, null, tint = AppRed, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(10.dp))
          Text(it, color = AppText, fontSize = 14.sp)
        }
      }
      Spacer(Modifier.height(20.dp))
      Button(
        onClick = {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
              launcher.launch(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                  data = Uri.parse("package:${context.packageName}")
                }
              )
            } catch (e: Exception) {
              launcher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
          }
        },
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppRed, contentColor = Color.White),
      ) {
        Text("Allow", fontWeight = FontWeight.Bold)
      }
      TextButton(onClick = { (context as? Activity)?.finish() }) { Text("Not now", color = AppMuted) }
    }
  }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs =
  listOf(
    Tab("Chat", Icons.Outlined.ChatBubbleOutline),
    Tab("Files", Icons.Outlined.Folder),
    Tab("Templates", Icons.Outlined.GridView),
    Tab("Settings", Icons.Outlined.Settings),
  )

@Composable
fun MainScaffold(onThemeChange: (String) -> Unit = {}) {
  val vm: ChatViewModel = viewModel()
  val context = LocalContext.current
  val prefs   = remember { context.getSharedPreferences("lfa", Context.MODE_PRIVATE) }

  // Init large file threshold from saved prefs
  LaunchedEffect(Unit) {
    vm.largeFileMb = prefs.getInt("large_file_threshold_mb", 500).toLong()
  }

  var tab by rememberSaveable { mutableIntStateOf(0) }
  var showHelp by rememberSaveable { mutableStateOf(false) }

  BackHandler(enabled = showHelp) { showHelp = false }

  if (showHelp) {
    HelpScreen { showHelp = false }
    return
  }

  Scaffold(
    containerColor = AppBg,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      NavigationBar(containerColor = AppSurface, contentColor = AppText) {
        tabs.forEachIndexed { i, t ->
          NavigationBarItem(
            selected = tab == i,
            onClick = { tab = i },
            icon = { Icon(t.icon, t.label) },
            label = { Text(t.label, fontSize = 11.sp) },
            modifier =
              Modifier.drawBehind {
                if (tab == i) {
                  drawLine(
                    AppRed,
                    Offset(size.width * 0.3f, 0f),
                    Offset(size.width * 0.7f, 0f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                  )
                }
              },
            colors =
              NavigationBarItemDefaults.colors(
                selectedIconColor = AppRed,
                selectedTextColor = AppRed,
                unselectedIconColor = AppMuted,
                unselectedTextColor = AppMuted,
                indicatorColor = Color.Transparent,
              ),
          )
        }
      }
    },
  ) { inner ->
    Box(Modifier.padding(inner).consumeWindowInsets(inner).imePadding().fillMaxSize().background(AppBg)) {
      when (tab) {
        0 -> ChatScreen(vm, onHelp = { showHelp = true })
        1 -> FilesScreen(vm, onHelp = { showHelp = true }, onGoChat = { tab = 0 })
        2 ->
          TemplatesScreen(
            onHelp = { showHelp = true },
            onRun = {
              vm.sendMessage(it)
              tab = 0
            },
          )
        else -> SettingsScreen(vm, onHelp = { showHelp = true }, onThemeChange = onThemeChange)
      }
    }
  }
}
