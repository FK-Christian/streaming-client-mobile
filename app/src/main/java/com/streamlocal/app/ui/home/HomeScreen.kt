package com.streamlocal.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamlocal.app.data.model.MediaFile
import com.streamlocal.app.ui.theme.Background
import com.streamlocal.app.ui.theme.CardBorder
import com.streamlocal.app.ui.theme.Divider
import com.streamlocal.app.ui.theme.Error
import com.streamlocal.app.ui.theme.OnBackground
import com.streamlocal.app.ui.theme.Primary
import com.streamlocal.app.ui.theme.Surface
import com.streamlocal.app.ui.theme.SurfaceVariant
import com.streamlocal.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (Int) -> Unit,
    onPhotoClick: (Int) -> Unit,
    onLogout:     () -> Unit,
    viewModel:    HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            viewModel.resetLoggedOut()
            onLogout()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text  = "StreamLocal",
                style = MaterialTheme.typography.titleLarge,
                color = Primary
            )
            Row {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(
                        imageVector        = Icons.Default.Refresh,
                        contentDescription = "Rafraîchir",
                        tint               = TextSecondary
                    )
                }
                IconButton(onClick = { viewModel.logout() }) {
                    Icon(
                        imageVector        = Icons.Default.Logout,
                        contentDescription = "Déconnexion",
                        tint               = TextSecondary
                    )
                }
            }
        }

        // Search bar
        OutlinedTextField(
            value         = uiState.searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            placeholder   = { Text("Rechercher…", color = TextSecondary) },
            leadingIcon   = {
                Icon(
                    imageVector        = Icons.Default.Search,
                    contentDescription = null,
                    tint               = TextSecondary
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            colors     = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Primary,
                unfocusedBorderColor = CardBorder,
                focusedTextColor     = OnBackground,
                unfocusedTextColor   = OnBackground,
                cursorColor          = Primary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Tab row
        val tabIndex = if (uiState.selectedTab == HomeTab.VIDEOS) 0 else 1

        TabRow(
            selectedTabIndex = tabIndex,
            containerColor   = Background,
            contentColor     = Primary,
            indicator        = { tabPositions ->
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[tabIndex]),
                    color    = Primary
                )
            },
            divider = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Divider)
                )
            }
        ) {
            Tab(
                selected = tabIndex == 0,
                onClick  = { viewModel.onTabSelected(HomeTab.VIDEOS) },
                text     = {
                    Row(
                        verticalAlignment      = Alignment.CenterVertically,
                        horizontalArrangement  = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Movie,
                            contentDescription = null,
                            modifier           = Modifier.size(18.dp)
                        )
                        Text("Vidéos")
                    }
                },
                selectedContentColor   = Primary,
                unselectedContentColor = TextSecondary
            )
            Tab(
                selected = tabIndex == 1,
                onClick  = { viewModel.onTabSelected(HomeTab.PHOTOS) },
                text     = {
                    Row(
                        verticalAlignment      = Alignment.CenterVertically,
                        horizontalArrangement  = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Image,
                            contentDescription = null,
                            modifier           = Modifier.size(18.dp)
                        )
                        Text("Photos")
                    }
                },
                selectedContentColor   = Primary,
                unselectedContentColor = TextSecondary
            )
        }

        // Content
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh    = { viewModel.refresh() },
            modifier     = Modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoading && (
                    (uiState.selectedTab == HomeTab.VIDEOS && uiState.videos.isEmpty()) ||
                    (uiState.selectedTab == HomeTab.PHOTOS && uiState.photos.isEmpty())
                ) -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                }

                uiState.errorMessage != null -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text  = uiState.errorMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Error
                            )
                        }
                    }
                }

                uiState.selectedTab == HomeTab.VIDEOS -> {
                    val filtered = viewModel.getFilteredVideos()
                    if (filtered.isEmpty()) {
                        EmptyState(message = "Aucune vidéo trouvée")
                    } else {
                        MediaList(
                            files      = filtered,
                            isVideo    = true,
                            onItemClick = onVideoClick
                        )
                    }
                }

                else -> {
                    val filtered = viewModel.getFilteredPhotos()
                    if (filtered.isEmpty()) {
                        EmptyState(message = "Aucune photo trouvée")
                    } else {
                        MediaList(
                            files      = filtered,
                            isVideo    = false,
                            onItemClick = onPhotoClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaList(
    files:       List<MediaFile>,
    isVideo:     Boolean,
    onItemClick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(files) { index, file ->
            MediaFileItem(
                file    = file,
                isVideo = isVideo,
                onClick = { onItemClick(index) }
            )
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun MediaFileItem(
    file:    MediaFile,
    isVideo: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // File type icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = if (isVideo) Icons.Default.Movie else Icons.Default.Image,
                contentDescription = null,
                tint               = Primary,
                modifier           = Modifier.size(24.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = file.name,
                style    = MaterialTheme.typography.bodyMedium,
                color    = OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text  = file.ext.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Primary
                )
                Text(
                    text  = file.formattedSize(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}
