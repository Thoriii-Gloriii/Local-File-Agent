package com.example.ai

import com.example.PendingAction
import org.json.JSONObject
import java.io.File

sealed class AgentBrainResult {
  data class ActionPlan(val explanation: String, val action: PendingAction) : AgentBrainResult()
  data class DirectAction(val explanation: String, val command: String) : AgentBrainResult()
  data class Conversational(val text: String) : AgentBrainResult()
}

object AgentBrain {

  fun buildPrompt(userPrompt: String, storageInfo: String): String {
    return """
You are Local File Agent, an intelligent offline assistant for managing files on this Android smartphone.
All operations run strictly on-device.

Current Storage: $storageInfo

Available tools:
- organize(folder: string) -> Group loose files into Images, Videos, Audio, Documents, Apps
- clean_empty() -> Delete empty subfolders
- large_files() -> List biggest files
- size() -> Check total storage breakdown
- list(path: string) -> List directory contents
- search(query: string) -> Find files by query
- read(file: string) -> Read content of a file
- delete(file: string) -> Delete a file or folder
- move(source: string, dest: string) -> Move a file or folder
- copy(source: string, dest: string) -> Copy a file or folder

User request: "$userPrompt"

If the user wants to perform an action, respond in this exact format:
Explanation: <brief 1-sentence friendly confirmation>
Action: {"tool": "<tool_name>", "args": {<arguments>}}

Examples:
- "organize my downloads" ->
Explanation: I will organize the loose files in your Download folder by type.
Action: {"tool": "organize", "args": {"folder": "Download"}}

- "clean up empty folders" ->
Explanation: I will scan and remove all empty subfolders across your storage.
Action: {"tool": "clean_empty", "args": {}}

- "find all pdf files" ->
Explanation: Searching for PDF documents across your device.
Action: {"tool": "search", "args": {"query": "pdf"}}

If the user is just asking a question or greeting you, respond with just your answer without an Action line.
""".trimIndent()
  }

  fun parseOutput(rawOutput: String, rootPath: String): AgentBrainResult {
    val trimmed = rawOutput.trim()

    val explanationMatch = Regex("Explanation:\\s*(.*?)(?=\\nAction:|$)", RegexOption.DOT_MATCHES_ALL).find(trimmed)
    val actionMatch = Regex("Action:\\s*(\\{.*?\\})", RegexOption.DOT_MATCHES_ALL).find(trimmed)

    val explanation = explanationMatch?.groupValues?.get(1)?.trim() ?: ""
    val actionJsonStr = actionMatch?.groupValues?.get(1)?.trim()

    if (actionJsonStr != null) {
      try {
        val json = JSONObject(actionJsonStr)
        val tool = json.optString("tool").lowercase()
        val args = json.optJSONObject("args") ?: JSONObject()

        fun resolve(path: String): String =
          if (path.startsWith("/")) path else File(rootPath, path).absolutePath

        when (tool) {
          "organize" -> {
            val folder = args.optString("folder", "Download")
            val target = resolve(folder)
            return AgentBrainResult.ActionPlan(
              explanation.ifEmpty { "I will organize the files in '$folder' into sorted folders." },
              PendingAction.ConfirmOrganize(target),
            )
          }
          "clean_empty" -> {
            return AgentBrainResult.ActionPlan(
              explanation.ifEmpty { "I will clean up empty subfolders in your storage." },
              PendingAction.ConfirmCleanEmpty(rootPath),
            )
          }
          "delete" -> {
            val file = args.optString("file")
            if (file.isNotBlank()) {
              return AgentBrainResult.ActionPlan(
                explanation.ifEmpty { "Are you sure you want to delete '$file'?" },
                PendingAction.ConfirmDelete(resolve(file)),
              )
            }
          }
          "move" -> {
            val source = args.optString("source")
            val dest = args.optString("dest")
            if (source.isNotBlank() && dest.isNotBlank()) {
              return AgentBrainResult.ActionPlan(
                explanation.ifEmpty { "Move '$source' to '$dest'?" },
                PendingAction.ConfirmMove(resolve(source), resolve(dest)),
              )
            }
          }
          "copy" -> {
            val source = args.optString("source")
            val dest = args.optString("dest")
            if (source.isNotBlank() && dest.isNotBlank()) {
              return AgentBrainResult.ActionPlan(
                explanation.ifEmpty { "Copy '$source' to '$dest'?" },
                PendingAction.ConfirmCopy(resolve(source), resolve(dest)),
              )
            }
          }
          "write" -> {
            val file = args.optString("file")
            val content = args.optString("content")
            if (file.isNotBlank()) {
              return AgentBrainResult.ActionPlan(
                explanation.ifEmpty { "Write to '$file'?" },
                PendingAction.ConfirmWrite(resolve(file), content),
              )
            }
          }
          "large_files" -> {
            return AgentBrainResult.DirectAction(
              explanation.ifEmpty { "Scanning for large files..." },
              "large files",
            )
          }
          "size" -> {
            return AgentBrainResult.DirectAction(
              explanation.ifEmpty { "Checking storage capacity..." },
              "size",
            )
          }
          "list" -> {
            val path = args.optString("path", "Download")
            return AgentBrainResult.DirectAction(
              explanation.ifEmpty { "Listing contents of '$path'..." },
              "list '$path'",
            )
          }
          "search" -> {
            val query = args.optString("query")
            if (query.isNotBlank()) {
              return AgentBrainResult.DirectAction(
                explanation.ifEmpty { "Searching for '$query'..." },
                "search '$query'",
              )
            }
          }
          "read" -> {
            val file = args.optString("file")
            if (file.isNotBlank()) {
              return AgentBrainResult.DirectAction(
                explanation.ifEmpty { "Reading '$file'..." },
                "read '$file'",
              )
            }
          }
        }
      } catch (e: Exception) {
        // Fall back to plain conversational text
      }
    }

    val cleanText = trimmed
      .replace(Regex("Explanation:\\s*"), "")
      .replace(Regex("Action:\\s*\\{.*?\\}"), "")
      .trim()

    return AgentBrainResult.Conversational(cleanText.ifEmpty { rawOutput })
  }
}
