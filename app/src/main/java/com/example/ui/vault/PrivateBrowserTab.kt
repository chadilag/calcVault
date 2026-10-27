package com.example.ui.vault

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.PrivateBookmarkEntity
import com.example.ui.theme.CalcAmber
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.RubyDanger
import com.example.ui.theme.VaultCyan
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PrivateBrowserTab(
    bookmarks: List<PrivateBookmarkEntity>,
    onAddBookmark: (title: String, url: String, category: String, accentHex: String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onBurnSessionToast: () -> Unit
) {
    var activeUrl by remember { mutableStateOf<String?>(null) }
    var omniboxInput by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("Incognito Start") }
    var isLoading by remember { mutableStateOf(false) }
    var blockedTrackersCount by remember { mutableIntStateOf(0) }
    var isDesktopMode by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }

    val trackerHosts = remember {
        listOf("doubleclick.net", "google-analytics.com", "googlesyndication.com", "facebook.net", "adsystem.com", "scorecardresearch.com")
    }

    fun navigateToInput(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        val resolvedUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://duckduckgo.com/?q=${ android.net.Uri.encode(trimmed) }"
        }
        omniboxInput = resolvedUrl
        activeUrl = resolvedUrl
        webViewRef?.loadUrl(resolvedUrl)
    }

    fun burnSessionNow() {
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            webViewRef?.apply {
                stopLoading()
                clearHistory()
                clearCache(true)
                clearFormData()
                loadUrl("about:blank")
            }
        } catch (_: Exception) {
        }
        activeUrl = null
        omniboxInput = ""
        pageTitle = "Incognito Start"
        blockedTrackersCount = 0
        onBurnSessionToast()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .testTag("private_browser_tab")
    ) {
        // Top Omnibox & Privacy Shield Bar
        Surface(
            color = ObsidianSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            activeUrl = null
                            omniboxInput = ""
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = "Browser Home", tint = VaultCyan)
                    }

                    OutlinedTextField(
                        value = omniboxInput,
                        onValueChange = { omniboxInput = it },
                        placeholder = { Text("Search DuckDuckGo or enter private URL...") },
                        leadingIcon = {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigateToInput(omniboxInput) }),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("browser_omnibox_input")
                    )

                    // Burn / Wipe Session Button
                    IconButton(
                        onClick = { burnSessionNow() },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RubyDanger.copy(alpha = 0.18f))
                            .testTag("browser_burn_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = "Burn session and wipe cookies",
                            tint = RubyDanger
                        )
                    }
                }

                // Privacy Telemetry & Navigation Controls Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(15.dp))
                        Text(
                            text = "ZERO-HISTORY SANDBOX • $blockedTrackersCount TRACKERS BLOCKED",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSafe
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (activeUrl != null) {
                            IconButton(
                                onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() else activeUrl = null },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { if (webViewRef?.canGoForward() == true) webViewRef?.goForward() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { webViewRef?.reload() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Reload", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(
                            onClick = {
                                isDesktopMode = !isDesktopMode
                                webViewRef?.settings?.userAgentString = if (isDesktopMode) {
                                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                                } else null
                                webViewRef?.reload()
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DesktopWindows,
                                contentDescription = "Toggle Desktop Mode",
                                tint = if (isDesktopMode) CalcAmber else Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { showAddBookmarkDialog = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Filled.BookmarkAdd, contentDescription = "Bookmark", tint = VaultCyan, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                color = VaultCyan,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Either Start Dashboard or Active Sandboxed WebView
        if (activeUrl == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ObsidianSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VaultCyan.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(VaultCyan.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Public, contentDescription = null, tint = VaultCyan)
                            }
                            Column {
                                Text("HideU Private Incognito Browser", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Text("No cookies, history, or cache ever leave this vault.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { navigateToInput("https://duckduckgo.com") },
                                colors = ButtonDefaults.buttonColors(containerColor = VaultCyan),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Launch DuckDuckGo", color = ObsidianBackground, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { burnSessionNow() },
                                colors = ButtonDefaults.buttonColors(containerColor = RubyDanger),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Burn Data", color = Color.White)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ENCRYPTED SPEED DIAL BOOKMARKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                    TextButton(onClick = { showAddBookmarkDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = VaultCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Site", color = VaultCyan)
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(bookmarks, key = { it.id }) { bm ->
                        val accent = parseHexColor(bm.accentHex)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ObsidianSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { navigateToInput(bm.url) }
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(accent.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = bm.title.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bm.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = bm.url.removePrefix("https://").removePrefix("http://"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteBookmark(bm.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = false
                        CookieManager.getInstance().setAcceptCookie(false)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                isLoading = true
                                url?.let { omniboxInput = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                pageTitle = view?.title ?: "Private Tab"
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val host = request?.url?.host?.lowercase().orEmpty()
                                if (trackerHosts.any { host.contains(it) }) {
                                    blockedTrackersCount++
                                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        webViewRef = this
                        loadUrl(activeUrl!!)
                    }
                },
                update = { view ->
                    if (activeUrl != null && view.url != activeUrl) {
                        view.loadUrl(activeUrl!!)
                    }
                }
            )
        }
    }

    if (showAddBookmarkDialog) {
        var title by remember { mutableStateOf(if (activeUrl != null) pageTitle else "") }
        var url by remember { mutableStateOf(activeUrl ?: "https://") }
        AlertDialog(
            onDismissRequest = { showAddBookmarkDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("Add Private Bookmark", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Site Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Website URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && url.isNotBlank()) {
                            onAddBookmark(title, url, "Private", "#00E5FF")
                            showAddBookmarkDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultCyan)
                ) {
                    Text("Save Bookmark", color = ObsidianBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmarkDialog = false }) { Text("Cancel") }
            }
        )
    }
}
