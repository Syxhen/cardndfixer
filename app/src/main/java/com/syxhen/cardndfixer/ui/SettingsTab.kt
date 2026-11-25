package com.syxhen.cardndfixer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syxhen.cardndfixer.R
import com.syxhen.cardndfixer.data.UpdateChannel
import com.syxhen.cardndfixer.updater.AvailableUpdate
import java.io.File

@Composable
internal fun SettingsTab(
    uiState: MainUiState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onSelectUpdateChannel: (UpdateChannel) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: (AvailableUpdate) -> Unit,
    onCancelUpdateDownload: () -> Unit,
    onInstallUpdate: (File) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "about_developer_card") {
            AboutDeveloperCard(
                appVersionLabel = uiState.appVersionLabel,
                onOpenGitHubProfile = { onOpenUrl(GitHubLinks.DEVELOPER_PROFILE_URL) },
                onReportBug = {
                    onOpenUrl(GitHubLinks.buildDeviceBugReportUrl(uiState.appVersionLabel))
                },
                onOpenPullRequests = { onOpenUrl(GitHubLinks.PULL_REQUESTS_URL) },
            )
        }

        item(key = "updates_card") {
            UpdatesCard(
                selectedChannel = uiState.settings.updateChannel,
                appVersionLabel = uiState.appVersionLabel,
                updateState = uiState.updateState,
                onSelectChannel = onSelectUpdateChannel,
                onCheckForUpdates = onCheckForUpdates,
                onDownloadUpdate = onDownloadUpdate,
                onCancelDownload = onCancelUpdateDownload,
                onInstallUpdate = onInstallUpdate,
                onOpenReleaseUrl = onOpenUrl,
            )
        }

        item(key = "settings_footer") {
            Text(
                text = "Car DND Fixer v${uiState.appVersionLabel} • GPL-3.0 Open Source",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
            )
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AboutDeveloperCard(
    appVersionLabel: String,
    onOpenGitHubProfile: () -> Unit,
    onReportBug: () -> Unit,
    onOpenPullRequests: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.avatar_syxhen),
                    contentDescription = "Syxhen profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = GitHubLinks.DEVELOPER_NAME,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                text = "Developer",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = GitHubLinks.DEVELOPER_BIO,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Car DND Fixer v$appVersionLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            FilledTonalButton(
                onClick = onOpenGitHubProfile,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Syxhen on GitHub")
            }

            Row(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onReportBug,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 320.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bug_report),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Report Bug", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onOpenPullRequests,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 320.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_code),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pull Requests", maxLines = 1)
                }
            }

            Text(
                text = "Bug reports pre-fill your app version, Android version, and device model—no personal data is included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
