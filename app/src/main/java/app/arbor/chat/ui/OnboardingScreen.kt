package app.arbor.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.arbor.chat.R

internal fun shouldShowProviderOnboarding(
    catalogReady: Boolean,
    hasConfiguredProvider: Boolean,
    dismissedForSession: Boolean,
): Boolean = catalogReady && !hasConfiguredProvider && !dismissedForSession

@Composable
internal fun ArborStartupScreen() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun OnboardingScreen(
    onOpenProviderSetup: () -> Unit,
    onExplore: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val haptics = rememberArborHaptics()
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (step == 0) {
            Spacer(Modifier.size(24.dp))
            Image(
                painter = painterResource(R.drawable.ic_arbor_mark),
                contentDescription = "Arbor",
                modifier = Modifier.size(96.dp),
            )
            Text(
                "Welcome to Arbor",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "A private, native workspace for AI chat and agent work.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    OnboardingValueRow(
                        Icons.Outlined.Lock,
                        "Private by design",
                        "Chats and credentials stay on this device.",
                    )
                    OnboardingValueRow(
                        Icons.Outlined.Cloud,
                        "Your providers",
                        "Connect directly to ChatGPT, an API, or a local server.",
                    )
                    OnboardingValueRow(
                        Icons.Outlined.Code,
                        "Tools when you need them",
                        "Search, files, Python, and optional Linux workspaces.",
                    )
                }
            }
            Button(
                onClick = {
                    haptics.confirm()
                    step = 1
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Get started")
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    haptics.selection()
                    step = 0
                }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                }
                Text(
                    "Connect a model",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                "Arbor needs one usable provider before it can send a message. Pick the connection style that fits you in Provider setup.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    OnboardingValueRow(
                        Icons.Outlined.AccountCircle,
                        "ChatGPT account",
                        "Sign in without pasting an API key.",
                    )
                    OnboardingValueRow(
                        Icons.Outlined.Cloud,
                        "API provider",
                        "Use OpenAI, Anthropic, Gemini, DeepSeek, or another compatible endpoint.",
                    )
                    OnboardingValueRow(
                        Icons.Outlined.Storage,
                        "Local server",
                        "Connect to Ollama, llama.cpp, or LM Studio on this device.",
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Lock, null)
                    Text(
                        "API keys are encrypted with Android Keystore and sent only to the provider you choose.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Button(
                onClick = {
                    haptics.confirm()
                    onOpenProviderSetup()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open provider setup")
            }
            OutlinedButton(
                onClick = {
                    haptics.selection()
                    onExplore()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Explore without connecting")
            }
        }
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun OnboardingValueRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp))
                }
            }
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
