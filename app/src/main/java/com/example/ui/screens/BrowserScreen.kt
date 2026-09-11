package com.example.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.MyraaApplication
import com.example.ui.theme.MyraaDeepVoid
import com.example.ui.theme.MyraaNeonCyan
import com.example.ui.theme.MyraaSurface
import com.example.ui.theme.MyraaSurfaceElevated

@Composable
fun BrowserScreen(
    app: MyraaApplication,
    onBack: () -> Unit
) {
    val browser = app.browserAgent
    val tabs by browser.tabs.collectAsState()
    val activeTabId by browser.activeTabId.collectAsState()
    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.firstOrNull()

    var urlInput by remember(activeTab?.url) { mutableStateOf(activeTab?.url ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MyraaDeepVoid)
    ) {
        // Navigation Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MyraaSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Myraa",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = { browser.goBack() },
                enabled = activeTab?.canGoBack == true
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Browser Back",
                    tint = if (activeTab?.canGoBack == true) Color.White else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = { browser.goForward() },
                enabled = activeTab?.canGoForward == true
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Browser Forward",
                    tint = if (activeTab?.canGoForward == true) Color.White else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("browser_url_input"),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MyraaSurfaceElevated,
                    unfocusedContainerColor = MyraaSurfaceElevated,
                    focusedBorderColor = MyraaNeonCyan,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                trailingIcon = {
                    IconButton(onClick = { browser.openUrl(urlInput) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Go",
                            tint = MyraaNeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { browser.openUrl(urlInput) })
            )

            IconButton(onClick = { activeTab?.let { browser.openUrl(it.url) } }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reload",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Tab list strip
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MyraaSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs) { tab ->
                val isSelected = tab.id == activeTabId
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MyraaSurfaceElevated else Color.Transparent)
                        .clickable { browser.selectTab(tab.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tab.title.take(16),
                        fontSize = 12.sp,
                        color = if (isSelected) MyraaNeonCyan else Color(0xFFA5A1C0),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close tab",
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { browser.closeTab(tab.id) }
                    )
                }
            }

            item {
                IconButton(
                    onClick = { browser.createNewTab() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Tab",
                        tint = MyraaNeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Web Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (activeTab != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        val webView = browser.getOrCreateWebView(activeTab.id)
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        webView
                    },
                    update = {
                        // View managed by BrowserAgent
                    }
                )
            }
        }

        // Media Quick Action Bar (for video controls via voice or tap)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MyraaSurface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MyraaSurfaceElevated),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    IconButton(onClick = { browser.executeMediaControl("toggle") }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play/Pause Video", tint = MyraaNeonCyan)
                    }
                    IconButton(onClick = { browser.executeMediaControl("forward") }) {
                        Icon(Icons.Default.FastForward, contentDescription = "Skip 10s", tint = Color.White)
                    }
                    IconButton(onClick = { browser.executeMediaControl("mute") }) {
                        Icon(Icons.Default.VolumeMute, contentDescription = "Mute Video", tint = Color.White)
                    }
                }
            }
            Text(
                text = "Voice Agent Active",
                color = Color(0xFFA5A1C0),
                fontSize = 11.sp
            )
        }
    }
}
