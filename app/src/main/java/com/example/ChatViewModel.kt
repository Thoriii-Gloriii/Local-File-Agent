package com.example

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AgentBrain
import com.example.ai.AgentBrainResult
import com.example.ai.LlmState
import com.example.ai.LocalLlmManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed class PendingAction {
  data class ConfirmDelete(val path: String) : PendingAction()
  data class ConfirmWrite(val path: String, val content: String) : PendingAction()
  data class ConfirmMove(val source: String, val dest: String) : PendingAction()
  data class ConfirmCopy(val source: String, val dest: String) : PendingAction()
  data class ConfirmUseTemplate(val templateName: String, val destPath: String) : PendingAction()
  data class ConfirmOrganize(val path: String) : PendingAction()
  data class ConfirmCleanEmpty(val path: String) : PendingAction()
}

data class FileInfo(
  val name: String,
  val path: String,
  val sizeBytes: Long,
  val isDir: Boolean,
  val category: String,
)

data class ChatMessage(
  val id: String = UUID.randomUUID().toString(),
  val text: String,
  val isUser: Boolean,
  val action: PendingAction? = null,
  val imagePath: String? = null,
  val fileList: List<FileInfo>? = null,
)

const val HELP_TEXT =
  "Here is what I can do:\n" +
    "- list 'Download'\n" +
    "- read 'Download/note.txt'\n" +
    "- write 'hello' to 'Download/note.txt'\n" +
    "- delete 'Download/old.txt'\n" +
    "- move 'Download/a.txt' to 'Download/Docs/a.txt'\n" +
    "- copy 'Download/a.txt' to 'Download/b.txt'\n" +
    "- rename 'Download/a.txt' to 'b.txt'\n" +
    "- find jpg  (search names and contents)\n" +
    "- preview 'Download/photo.png'\n" +
    "- size  (storage usage)\n" +
    "- large files\n" +
    "- organize 'Download'  (sort into folders by type)\n" +
    "- clean empty folders\n" +
    "- create template 'name' from 'path'\n" +
    "- use template 'name' at 'path'"

class ChatViewModel(application: Application) : AndroidViewModel(application) {
  val llmManager = LocalLlmManager(application)
  val rootPath: String = Environment.getExternalStorageDirectory().absolutePath
  var largeFileMb: Long = 500L  // default 500 MB, updated from Settings

  private val welcome =
    ChatMessage(
      text = "Hello! 👋\nI'm your Local File Agent.\nTell me what you want to do with your files.",
      isUser = false,
    )
  private val _messages = MutableStateFlow(listOf(welcome))
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

  fun clearChat() {
    _messages.value = listOf(welcome)
  }

  fun sendMessage(text: String) {
    _messages.update { it + ChatMessage(text = text, isUser = true) }
    processCommand(text)
  }

  fun resolveFile(path: String): File =
    if (path.startsWith("/")) File(path) else File(rootPath, path)

  fun requestDelete(path: String) =
    addBotMessage("Are you sure you want to delete '$path'?", PendingAction.ConfirmDelete(path))

  fun requestMove(source: String, dest: String) =
    addBotMessage("Are you sure you want to move '$source' to '$dest'?", PendingAction.ConfirmMove(source, dest))

  fun requestCopy(source: String, dest: String) =
    addBotMessage("Are you sure you want to copy '$source' to '$dest'?", PendingAction.ConfirmCopy(source, dest))

  private fun processCommand(input: String) {
    val trimmed = input.trim()
    val lower = trimmed.lowercase()
    val quotes = Regex("['\"](.*?)['\"]").findAll(trimmed).map { it.groupValues[1] }.toList()
    val words = trimmed.split(" ")
    val lastWord = words.lastOrNull() ?: ""

    try {
      if (lower == "help" || lower.contains("what can you do")) {
        addBotMessage(HELP_TEXT)
        return
      }

      if (lower.startsWith("size") || lower.contains("storage usage")) {
        val root = File(rootPath)
        val used = root.totalSpace - root.freeSpace
        addBotMessage(
          "Internal storage: ${formatSize(used)} used of ${formatSize(root.totalSpace)} (${formatSize(root.freeSpace)} free)."
        )
        return
      }

      if (lower.startsWith("large") || lower.contains("large files") || lower.contains("big files")) {
        val thresh = largeFileMb
        addBotMessage("Looking for files over ${formatSize(thresh * 1024 * 1024)}...")
        viewModelScope.launch(Dispatchers.IO) {
          val big =
            File(rootPath)
              .walkTopDown()
              .onEnter { !it.name.startsWith(".") && it.name != "Android" }
              .filter { it.isFile && it.length() > thresh * 1024 * 1024 }
              .toList()
              .sortedByDescending { it.length() }
              .take(30)
          val infos = big.map { FileInfo(it.name, it.absolutePath, it.length(), false, fileCategory(it)) }
          if (infos.isEmpty()) {
            addBotMessage("No files over ${formatSize(thresh * 1024 * 1024)} found.")
          } else {
            addFileListMessage("Found ${infos.size} large files (threshold: ${formatSize(thresh * 1024 * 1024)}):", infos)
          }
        }
        return
      }

      if (lower.startsWith("organize") || lower.startsWith("organise")) {
        val target = quotes.firstOrNull()
        val dir =
          if (target.isNullOrBlank() || target.equals("root", true)) File(rootPath)
          else resolveFile(target)
        if (dir.isDirectory) {
          addBotMessage(
            "I'll move the loose files in '${dir.absolutePath}' into Images, Videos, Audio, Documents, Apps, and Other folders. Continue?",
            PendingAction.ConfirmOrganize(dir.absolutePath),
          )
        } else addBotMessage("Folder '${dir.absolutePath}' not found.")
        return
      }

      if (lower.contains("clean empty") || lower.contains("empty folders")) {
        addBotMessage(
          "I'll remove empty subfolders inside your storage folders (top-level folders are kept). Continue?",
          PendingAction.ConfirmCleanEmpty(rootPath),
        )
        return
      }

      if (lower.startsWith("search") || lower.startsWith("find")) {
        val query = quotes.firstOrNull() ?: words.drop(1).joinToString(" ").trim()
        if (query.isNotBlank()) {
          addBotMessage("Searching for '$query'...")
          viewModelScope.launch(Dispatchers.IO) {
            val results = performSearch(rootPath, query)
            if (results.isEmpty()) {
              addBotMessage("No results found for '$query'.")
            } else {
              val infos = results.map { FileInfo(it.name, it.absolutePath, if (it.isFile) it.length() else 0L, it.isDirectory, fileCategory(it)) }
              addFileListMessage("Found ${infos.size} result(s) for '$query':", infos)
            }
          }
          return
        }
      }

      if (lower.startsWith("preview")) {
        val target = quotes.firstOrNull() ?: words.find { it.contains("/") || it.contains(".") } ?: lastWord
        val file = resolveFile(target)
        if (file.exists() && file.isFile) {
          if (file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")) {
            addBotMessage("Previewing image ${file.name}:", imagePath = file.absolutePath)
          } else {
            try {
              addBotMessage("Preview of ${file.name}:\n${file.readText().take(2000)}")
            } catch (e: Exception) {
              addBotMessage("Error reading file: ${e.message}")
            }
          }
        } else addBotMessage("File '${file.absolutePath}' not found.")
        return
      }

      if (lower.contains("save template") || lower.contains("create template")) {
        if (quotes.size >= 2) {
          val source = resolveFile(quotes[1])
          val templateDir = File(rootPath, ".FileAgentTemplates").apply { mkdirs() }
          try {
            source.copyRecursively(File(templateDir, quotes[0]), overwrite = true)
            addBotMessage("Template '${quotes[0]}' saved.")
          } catch (e: Exception) {
            addBotMessage("Error saving template: ${e.message}")
          }
          return
        }
      }

      if (lower.contains("use template") || lower.contains("apply template") || lower.contains("from template")) {
        if (quotes.size >= 2) {
          val dest = resolveFile(quotes[1])
          val source = File(File(rootPath, ".FileAgentTemplates"), quotes[0])
          if (source.exists()) {
            addBotMessage(
              "Are you sure you want to create item at '${dest.absolutePath}' using template '${quotes[0]}'?",
              PendingAction.ConfirmUseTemplate(quotes[0], dest.absolutePath),
            )
          } else addBotMessage("Template '${quotes[0]}' not found.")
          return
        }
      }

      if (lower.startsWith("rename") && quotes.size >= 2) {
        val src = resolveFile(quotes[0])
        val dest = File(src.parentFile, quotes[1])
        requestMove(src.absolutePath, dest.absolutePath)
        return
      }

      if (lower.startsWith("copy") && quotes.size >= 2) {
        requestCopy(resolveFile(quotes[0]).absolutePath, resolveFile(quotes[1]).absolutePath)
        return
      }

      if (lower.contains("delete") || lower.contains("remove") || lower.contains("trash")) {
        val target = quotes.firstOrNull() ?: words.find { it.contains("/") || it.contains(".") } ?: lastWord
        if (target.isNotBlank() && target.length > 2) {
          requestDelete(resolveFile(target).absolutePath)
          return
        }
      }

      if (lower.contains("move") || lower.contains("arrange")) {
        if (quotes.size >= 2) {
          requestMove(resolveFile(quotes[0]).absolutePath, resolveFile(quotes[1]).absolutePath)
          return
        }
      }

      if (lower.contains("list") || lower.contains("show") || lower.contains("dir")) {
        val target = quotes.firstOrNull() ?: words.find { it.contains("/") && !it.contains("list") } ?: ""
        val dir = if (target.isBlank()) File(rootPath) else resolveFile(target)
        if (dir.exists() && dir.isDirectory) {
          val files = dir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
          val infos = files.map { FileInfo(it.name, it.absolutePath, if (it.isFile) it.length() else 0L, it.isDirectory, fileCategory(it)) }
          val totalSize = infos.filter { !it.isDir }.sumOf { it.sizeBytes }
          val dirLabel = dir.name.takeIf { it.isNotEmpty() && it != "0" } ?: "Internal Storage"
          addFileListMessage("$dirLabel — ${infos.size} items, ${formatSize(totalSize)}", infos)
        } else addBotMessage("Directory '${dir.absolutePath}' not found.")
        return
      }

      if (lower.contains("read") || lower.contains("cat ")) {
        val target = quotes.firstOrNull() ?: words.find { it.contains("/") || it.contains(".") } ?: lastWord
        val file = resolveFile(target)
        if (file.exists() && file.isFile) {
          try {
            addBotMessage("Content of ${file.name}:\n${file.readText().take(2000)}")
          } catch (e: Exception) {
            addBotMessage("Error reading file: ${e.message}")
          }
        } else addBotMessage("File '${file.absolutePath}' not found.")
        return
      }

      if (lower.contains("write") || lower.contains("create") || lower.contains("edit")) {
        if (quotes.size >= 2) {
          val file = resolveFile(quotes[1])
          addBotMessage(
            "Are you sure you want to write to '${file.absolutePath}'?",
            PendingAction.ConfirmWrite(file.absolutePath, quotes[0]),
          )
          return
        }
      }

      // ── On-Device Offline Local LLM Flow ──────────────────────────────
      when (llmManager.state.value) {
        LlmState.READY -> {
          addBotMessage("Thinking (offline AI)...")
          viewModelScope.launch(Dispatchers.IO) {
            try {
              val root = File(rootPath)
              val storageInfo = "${formatSize(root.totalSpace - root.freeSpace)} used of ${formatSize(root.totalSpace)}"
              val prompt = AgentBrain.buildPrompt(trimmed, storageInfo)
              val response = llmManager.generateResponse(prompt)
              val result = AgentBrain.parseOutput(response, rootPath)

              withContext(Dispatchers.Main) {
                _messages.update { list -> list.filterNot { it.text == "Thinking (offline AI)..." } }
                when (result) {
                  is AgentBrainResult.ActionPlan -> {
                    addBotMessage(result.explanation, result.action)
                  }
                  is AgentBrainResult.DirectAction -> {
                    addBotMessage(result.explanation)
                    processCommand(result.command)
                  }
                  is AgentBrainResult.Conversational -> {
                    addBotMessage(result.text)
                  }
                }
              }
            } catch (e: Exception) {
              withContext(Dispatchers.Main) {
                _messages.update { list -> list.filterNot { it.text == "Thinking (offline AI)..." } }
                addBotMessage("Offline AI inference error: ${e.localizedMessage ?: "Unknown error"}")
              }
            }
          }
        }
        LlmState.DOWNLOADING -> {
          val pct = (llmManager.downloadProgress.value * 100).toInt()
          addBotMessage("⏳ On-device AI model is currently downloading ($pct%). Type 'help' for built-in commands.")
        }
        LlmState.INITIALIZING -> {
          addBotMessage("⚙️ On-device AI is loading into memory. Try again in a moment, or type 'help'.")
        }
        LlmState.NOT_DOWNLOADED, LlmState.ERROR -> {
          addBotMessage(
            "🧠 **Offline AI Brain is not installed yet.**\n" +
              "To chat with me in natural language, download or load the offline model in Settings ⚙️.\n" +
              "Meanwhile, you can use built-in commands (type 'help' or tap Templates)."
          )
        }
      }
    } catch (e: Exception) {
      addBotMessage("Failed to parse or execute command: ${e.message}")
    }
  }

  fun executeAction(action: PendingAction) {
    when (action) {
      is PendingAction.ConfirmDelete -> {
        try {
          val file = File(action.path)
          addBotMessage(
            if (!file.exists()) "File no longer exists: ${action.path}"
            else if (file.deleteRecursively()) "Deleted: ${action.path}"
            else "Failed to delete: ${action.path}"
          )
        } catch (e: Exception) {
          addBotMessage("Error deleting: ${e.message}")
        }
      }
      is PendingAction.ConfirmWrite -> {
        try {
          val file = File(action.path)
          file.parentFile?.mkdirs()
          file.writeText(action.content)
          addBotMessage("Written to: ${action.path}")
        } catch (e: Exception) {
          addBotMessage("Failed to write: ${e.message}")
        }
      }
      is PendingAction.ConfirmMove -> {
        try {
          val source = File(action.source)
          val dest = File(action.dest)
          dest.parentFile?.mkdirs()
          if (!source.renameTo(dest)) {
            source.copyRecursively(dest, overwrite = true)
            source.deleteRecursively()
          }
          addBotMessage("Moved to: ${action.dest}")
        } catch (e: Exception) {
          addBotMessage("Failed to move: ${e.message}")
        }
      }
      is PendingAction.ConfirmCopy -> {
        try {
          val dest = File(action.dest)
          dest.parentFile?.mkdirs()
          val ok = File(action.source).copyRecursively(dest, overwrite = false)
          addBotMessage(if (ok) "Copied to: ${action.dest}" else "Copy finished with errors (some items may already exist).")
        } catch (e: Exception) {
          addBotMessage("Failed to copy: ${e.message}")
        }
      }
      is PendingAction.ConfirmUseTemplate -> {
        try {
          val source = File(File(rootPath, ".FileAgentTemplates"), action.templateName)
          val dest = File(action.destPath)
          if (source.isDirectory) dest.mkdirs() else dest.parentFile?.mkdirs()
          source.copyRecursively(dest, overwrite = true)
          addBotMessage("Created ${action.destPath} from template '${action.templateName}'.")
        } catch (e: Exception) {
          addBotMessage("Failed to apply template: ${e.message}")
        }
      }
      is PendingAction.ConfirmOrganize -> {
        try {
          val dir = File(action.path)
          var moved = 0
          dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.forEach { f ->
            val target = File(dir, fileCategory(f)).apply { mkdirs() }
            var dest = File(target, f.name)
            var n = 1
            while (dest.exists()) {
              val ext = if (f.extension.isEmpty()) "" else ".${f.extension}"
              dest = File(target, "${f.nameWithoutExtension} ($n)$ext")
              n++
            }
            if (f.renameTo(dest)) moved++
          }
          addBotMessage("Organized $moved files in ${dir.absolutePath}.")
        } catch (e: Exception) {
          addBotMessage("Failed to organize: ${e.message}")
        }
      }
      is PendingAction.ConfirmCleanEmpty -> {
        try {
          val root = File(action.path)
          var removed = 0
          root.walkBottomUp().forEach { d ->
            if (
              d.isDirectory &&
                d.absolutePath != root.absolutePath &&
                d.parentFile?.absolutePath != root.absolutePath &&
                !d.name.startsWith(".") &&
                !d.absolutePath.contains("/Android/") &&
                d.list()?.isEmpty() == true &&
                d.delete()
            ) removed++
          }
          addBotMessage("Removed $removed empty folders.")
        } catch (e: Exception) {
          addBotMessage("Failed to clean folders: ${e.message}")
        }
      }
    }
    _messages.update { list -> list.map { if (it.action == action) it.copy(action = null) else it } }
  }

  private fun addFileListMessage(text: String, files: List<FileInfo>, action: PendingAction? = null) {
    _messages.update { it + ChatMessage(text = text, isUser = false, action = action, fileList = files) }
  }

  private fun addBotMessage(text: String, action: PendingAction? = null, imagePath: String? = null) {
    _messages.update { it + ChatMessage(text = text, isUser = false, action = action, imagePath = imagePath) }
  }

  private fun performSearch(dirPath: String, query: String): List<File> {
    val results = mutableListOf<File>()
    val dir = File(dirPath)
    if (!dir.exists()) return results
    try {
      dir
        .walkTopDown()
        .onEnter { !it.name.startsWith(".") && it.name != "Android" }
        .filter {
          val nameMatch = it.name.contains(query, ignoreCase = true)
          val extMatch = it.isFile && it.extension.contains(query, ignoreCase = true)
          val contentMatch =
            it.isFile &&
              it.length() < 1024 * 1024 &&
              try {
                it.readText().contains(query, ignoreCase = true)
              } catch (e: Exception) {
                false
              }
          nameMatch || extMatch || contentMatch
        }
        .take(20)
        .toCollection(results)
    } catch (e: Exception) {}
    return results
  }
}
