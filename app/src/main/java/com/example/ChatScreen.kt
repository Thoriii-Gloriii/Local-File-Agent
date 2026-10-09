package com.example

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

private val chips =
  listOf(
    "Show downloads" to "list 'Download'",
    "Find images" to "find jpg",
    "Large files" to "large files",
    "What can you do?" to "help",
  )

@Composable
fun ChatScreen(vm: ChatViewModel, onHelp: () -> Unit) {
  val messages by vm.messages.collectAsState()
  var input by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
  }

  fun send(text: String) {
    if (text.isNotBlank()) {
      vm.sendMessage(text)
      input = ""
    }
  }

  Column(Modifier.fillMaxSize().background(AppBg)) {
    AppTopBar(onHelp = onHelp)
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
      state = listState,
      contentPadding = PaddingValues(vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(messages, key = { it.id }) { msg -> MessageBubble(msg) { vm.executeAction(it) } }
    }
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(chips) { (label, command) ->
        Box(
          Modifier.clip(RoundedCornerShape(20.dp))
            .background(AppCard)
            .border(0.5.dp, AppBorder, RoundedCornerShape(20.dp))
            .clickable { send(command) }
            .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
          Text(label, color = AppText, fontSize = 13.sp)
        }
      }
    }
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.weight(1f),
        placeholder = { Text("Type a command...", color = AppMuted) },
        shape = RoundedCornerShape(28.dp),
        singleLine = true,
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedContainerColor = AppCard,
            unfocusedContainerColor = AppCard,
            focusedBorderColor = AppRed,
            unfocusedBorderColor = AppBorder,
            cursorColor = AppRed,
            focusedTextColor = AppText,
            unfocusedTextColor = AppText,
          ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send(input) }),
      )
      Spacer(Modifier.width(10.dp))
      Box(
        Modifier.size(50.dp).clip(CircleShape).background(AppRed).clickable { send(input) },
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White)
      }
    }
  }
}

@Composable
fun MessageBubble(message: ChatMessage, onAction: (PendingAction) -> Unit) {
  val isUser = message.isUser
  val shape =
    RoundedCornerShape(
      topStart = 18.dp,
      topEnd = 18.dp,
      bottomStart = if (isUser) 18.dp else 4.dp,
      bottomEnd = if (isUser) 4.dp else 18.dp,
    )
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    verticalAlignment = Alignment.Top,
  ) {
    if (!isUser) {
      Image(painterResource(R.drawable.logo_icon), null, Modifier.size(36.dp))
      Spacer(Modifier.width(8.dp))
    }
    Box(
      Modifier.widthIn(max = 300.dp)
        .background(if (isUser) AppRed else AppCard, shape)
        .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
      Column {
        Text(
          message.text,
          color = if (isUser) Color.White else AppText,
          fontSize = 15.sp,
          lineHeight = 22.sp,
        )
        if (message.imagePath != null) {
          val bitmap =
            remember(message.imagePath) {
              BitmapFactory.decodeFile(message.imagePath)?.asImageBitmap()
            }
          Spacer(Modifier.height(8.dp))
          if (bitmap != null) {
            Image(
              bitmap,
              "Image preview",
              Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(10.dp)),
              contentScale = ContentScale.Crop,
            )
          } else Text("Failed to load image preview", color = AppRed, fontSize = 12.sp)
        }
        if (message.action != null) {
          Spacer(Modifier.height(12.dp))
          Button(
            onClick = { onAction(message.action) },
            colors = ButtonDefaults.buttonColors(containerColor = AppRed, contentColor = Color.White),
          ) {
            Text(
              when (message.action) {
                is PendingAction.ConfirmDelete -> "Confirm Delete"
                is PendingAction.ConfirmWrite -> "Confirm Write"
                is PendingAction.ConfirmMove -> "Confirm Move"
                is PendingAction.ConfirmCopy -> "Confirm Copy"
                is PendingAction.ConfirmUseTemplate -> "Confirm Apply Template"
                is PendingAction.ConfirmOrganize -> "Confirm Organize"
                is PendingAction.ConfirmCleanEmpty -> "Confirm Clean Up"
              }
            )
          }
        }
      }
    }
  }
}
