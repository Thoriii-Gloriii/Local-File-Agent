package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            PermissionWrapper {
                ChatScreen()
            }
        }
      }
    }
  }
}

@Composable
fun PermissionWrapper(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true // Fallback for older versions, relying on normal manifest permissions
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPermission = Environment.isExternalStorageManager()
        }
    }

    if (hasPermission) {
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Storage Permission Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "This app requires full storage access to manage your files. Please grant 'All files access' in Settings.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${context.packageName}")
                        launcher.launch(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        launcher.launch(intent)
                    }
                }
            }) {
                Text("Grant Permission")
            }
        }
    }
}

sealed class PendingAction {
    data class ConfirmDelete(val path: String) : PendingAction()
    data class ConfirmWrite(val path: String, val content: String) : PendingAction()
    data class ConfirmMove(val source: String, val dest: String) : PendingAction()
    data class ConfirmUseTemplate(val templateName: String, val destPath: String) : PendingAction()
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val action: PendingAction? = null,
    val imagePath: String? = null
)

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "Hello! I am your offline file manager agent. I can list, read, write, move, and delete files based on simple commands. How can I help you organize your device today?\n\nExamples:\n- \"List files in Download\"\n- \"Write 'hello' to Download/test.txt\"\n- \"Delete Download/test.txt\"\n- \"Move Download/test.txt to Download/Docs/test.txt\"",
                isUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val rootPath = Environment.getExternalStorageDirectory().absolutePath

    fun sendMessage(text: String) {
        val userMsg = ChatMessage(text = text, isUser = true)
        _messages.value = _messages.value + userMsg
        processCommand(text)
    }

    private fun resolveFile(path: String): File {
        return if (path.startsWith("/")) File(path) else File(rootPath, path)
    }

    private fun processCommand(input: String) {
        val lower = input.lowercase()
        // Extract quoted strings
        val quotes = Regex("['\"](.*?)['\"]").findAll(input).map { it.groupValues[1] }.toList()
        val words = input.split(" ")
        val lastWord = words.lastOrNull() ?: ""

        try {
            // Delete Logic
            if (lower.contains("delete") || lower.contains("remove") || lower.contains("trash")) {
                val target = quotes.firstOrNull() ?: words.find { it.contains("/") || it.contains(".") } ?: lastWord
                if (target.isNotBlank() && target.length > 2) {
                    val file = resolveFile(target)
                    addBotMessage("Are you sure you want to delete '${file.absolutePath}'?", PendingAction.ConfirmDelete(file.absolutePath))
                    return
                }
            }

            // Move / Arrange Logic
            if (lower.contains("move") || lower.contains("arrange")) {
                if (quotes.size >= 2) {
                    val source = quotes[0]
                    val dest = quotes[1]
                    addBotMessage("Are you sure you want to move '${source}' to '${dest}'?", PendingAction.ConfirmMove(resolveFile(source).absolutePath, resolveFile(dest).absolutePath))
                    return
                }
            }

            // List Logic
            if (lower.contains("list") || lower.contains("show") || lower.contains("dir")) {
                val target = quotes.firstOrNull() ?: words.find { it.contains("/") && !it.contains("list") } ?: ""
                val dir = if (target.isBlank()) File(rootPath) else resolveFile(target)
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles()
                    val listStr = files?.joinToString("\n") { (if (it.isDirectory) "📁 " else "📄 ") + it.name } ?: "Empty or cannot read."
                    addBotMessage("Contents of ${dir.absolutePath}:\n$listStr")
                } else {
                    addBotMessage("Directory '${dir.absolutePath}' not found.")
                }
                return
            }

            // Read Logic
            if (lower.contains("read") || lower.contains("cat")) {
                val target = quotes.firstOrNull() ?: words.find { it.contains("/") || it.contains(".") } ?: lastWord
                val file = resolveFile(target)
                if (file.exists() && file.isFile) {
                    try {
                        val content = file.readText().take(2000) // limit output to 2k chars
                        addBotMessage("Content of ${file.name}:\n$content")
                    } catch (e: Exception) {
                        addBotMessage("Error reading file: ${e.message}")
                    }
                } else {
                    addBotMessage("File '${file.absolutePath}' not found.")
                }
                return
            }

            // Write Logic
            if (lower.contains("write") || (lower.contains("create") && !lower.contains("template")) || lower.contains("edit")) {
                if (quotes.size >= 2) {
                    val content = quotes[0]
                    val target = quotes[1]
                    val file = resolveFile(target)
                    addBotMessage("Are you sure you want to write to '${file.absolutePath}'?", PendingAction.ConfirmWrite(file.absolutePath, content))
                    return
                }
            }

            // Template Logic
            if (lower.contains("save template") || (lower.contains("create template"))) {
                if (quotes.size >= 2) {
                    val templateName = quotes[0]
                    val path = quotes[1]
                    val source = resolveFile(path)
                    val templateDir = File(rootPath, ".FileAgentTemplates")
                    templateDir.mkdirs()
                    val dest = File(templateDir, templateName)
                    try {
                        source.copyRecursively(dest, overwrite = true)
                        addBotMessage("Template '$templateName' saved successfully.")
                    } catch (e: Exception) {
                        addBotMessage("Error saving template: ${e.message}")
                    }
                    return
                }
            }

            if (lower.contains("use template") || lower.contains("apply template") || lower.contains("from template")) {
                if (quotes.size >= 2) {
                    val templateName = quotes[0]
                    val path = quotes[1]
                    val dest = resolveFile(path)
                    val templateDir = File(rootPath, ".FileAgentTemplates")
                    val source = File(templateDir, templateName)
                    if (source.exists()) {
                        addBotMessage("Are you sure you want to create item at '${dest.absolutePath}' using template '$templateName'?", PendingAction.ConfirmUseTemplate(templateName, dest.absolutePath))
                    } else {
                        addBotMessage("Template '$templateName' not found.")
                    }
                    return
                }
            }

            // Search Logic
            if (lower.startsWith("search") || lower.startsWith("find")) {
                val query = quotes.firstOrNull() ?: words.drop(1).joinToString(" ").trim()
                if (query.isNotBlank()) {
                    addBotMessage("Searching for '$query'...")
                    viewModelScope.launch(Dispatchers.IO) {
                        val results = performSearch(rootPath, query)
                        val resultStr = if (results.isEmpty()) "No results found." else results.joinToString("\n") { it.absolutePath }
                        addBotMessage("Search results:\n$resultStr")
                    }
                    return
                }
            }

            // Preview Logic
            if (lower.startsWith("preview")) {
                val target = quotes.firstOrNull() ?: words.find { it.contains("/") || it.contains(".") } ?: lastWord
                val file = resolveFile(target)
                if (file.exists() && file.isFile) {
                    val ext = file.extension.lowercase()
                    if (ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")) {
                        addBotMessage("Previewing image ${file.name}:", imagePath = file.absolutePath)
                    } else {
                        try {
                            val content = file.readText().take(2000)
                            addBotMessage("Preview of ${file.name}:\n$content")
                        } catch (e: Exception) {
                            addBotMessage("Error reading file: ${e.message}")
                        }
                    }
                } else {
                    addBotMessage("File '${file.absolutePath}' not found.")
                }
                return
            }

            addBotMessage("I didn't quite understand that. To ensure offline capabilities, I use a fast rule-based parser. Try commands like:\n- List files in 'Download'\n- Read 'Download/note.txt'\n- Delete 'Download/old_folder'\n- Write 'hello' to 'Download/note.txt'\n- Move 'Download/A.txt' to 'Download/B.txt'\n- Preview 'Download/image.png'\n- Search for 'query'\n- Create template 'name' from 'path'\n- Use template 'name' at 'path'")
        } catch (e: Exception) {
            addBotMessage("Failed to parse or execute command: ${e.message}")
        }
    }

    fun executeAction(action: PendingAction) {
        when (action) {
            is PendingAction.ConfirmDelete -> {
                val file = File(action.path)
                try {
                    if (file.exists()) {
                        val success = file.deleteRecursively()
                        if (success) {
                            addBotMessage("Successfully deleted: ${action.path}")
                        } else {
                            addBotMessage("Failed to delete: ${action.path}")
                        }
                    } else {
                        addBotMessage("File no longer exists: ${action.path}")
                    }
                } catch (e: Exception) {
                    addBotMessage("Error deleting: ${e.message}")
                }
            }
            is PendingAction.ConfirmWrite -> {
                val file = File(action.path)
                try {
                    file.parentFile?.mkdirs()
                    file.writeText(action.content)
                    addBotMessage("Successfully written to: ${action.path}")
                } catch (e: Exception) {
                    addBotMessage("Failed to write: ${e.message}")
                }
            }
            is PendingAction.ConfirmMove -> {
                val source = File(action.source)
                val dest = File(action.dest)
                try {
                    dest.parentFile?.mkdirs()
                    if (source.renameTo(dest)) {
                        addBotMessage("Successfully moved to: ${action.dest}")
                    } else {
                        // Fallback to copy+delete
                        source.copyRecursively(dest, overwrite = true)
                        source.deleteRecursively()
                        addBotMessage("Successfully moved to: ${action.dest}")
                    }
                } catch (e: Exception) {
                    addBotMessage("Failed to move: ${e.message}")
                }
            }
            is PendingAction.ConfirmUseTemplate -> {
                val templateDir = File(rootPath, ".FileAgentTemplates")
                val source = File(templateDir, action.templateName)
                val dest = File(action.destPath)
                try {
                    if (source.isDirectory) {
                        dest.mkdirs()
                        source.copyRecursively(dest, overwrite = true)
                    } else {
                        dest.parentFile?.mkdirs()
                        source.copyRecursively(dest, overwrite = true)
                    }
                    addBotMessage("Successfully created item at: ${action.destPath} from template '${action.templateName}'")
                } catch (e: Exception) {
                    addBotMessage("Failed to apply template: ${e.message}")
                }
            }
        }
        
        // Remove the action from the previous message so it can't be clicked again
        val updated = _messages.value.map {
            if (it.action == action) it.copy(action = null) else it
        }
        _messages.value = updated
    }

    private fun addBotMessage(text: String, action: PendingAction? = null, imagePath: String? = null) {
        _messages.value = _messages.value + ChatMessage(text = text, isUser = false, action = action, imagePath = imagePath)
    }

    private fun performSearch(dirPath: String, query: String): List<File> {
        val results = mutableListOf<File>()
        val dir = File(dirPath)
        if (!dir.exists()) return results
        
        try {
            dir.walkTopDown()
                .onEnter { !it.name.startsWith(".") && it.name != "Android" } // Skip hidden and Android/data to speed up
                .filter {
                    val nameMatch = it.name.contains(query, ignoreCase = true)
                    val extMatch = it.isFile && it.extension.contains(query, ignoreCase = true)
                    val contentMatch = it.isFile && it.length() < 1024 * 1024 && try { it.readText().contains(query, ignoreCase = true) } catch(e:Exception) { false }
                    nameMatch || extMatch || contentMatch
                }
                .take(20)
                .toCollection(results)
        } catch (e: Exception) {
            // Handle access exceptions
        }
        return results
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local File Agent") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        onAction = { viewModel.executeAction(it) }
                    )
                }
            }

            // Scroll to bottom when new messages arrive
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a command...") },
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        }
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    shape = RoundedCornerShape(50),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, onAction: (PendingAction) -> Unit) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .background(bgColor, shape)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                if (message.imagePath != null) {
                    val bitmap = remember(message.imagePath) {
                        BitmapFactory.decodeFile(message.imagePath)?.asImageBitmap()
                    }
                    if (bitmap != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Image preview",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Failed to load image preview", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                if (message.action != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row {
                        Button(
                            onClick = { onAction(message.action) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(
                                when (message.action) {
                                    is PendingAction.ConfirmDelete -> "Confirm Delete"
                                    is PendingAction.ConfirmWrite -> "Confirm Write"
                                    is PendingAction.ConfirmMove -> "Confirm Move"
                                    is PendingAction.ConfirmUseTemplate -> "Confirm Apply Template"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
