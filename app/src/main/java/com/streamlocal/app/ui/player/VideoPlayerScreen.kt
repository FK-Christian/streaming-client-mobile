package com.streamlocal.app.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.streamlocal.app.ui.theme.Error
import com.streamlocal.app.ui.theme.Primary
import com.streamlocal.app.ui.theme.TextSecondary

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Formate des millisecondes en "M:SS" ou "H:MM:SS". */
private fun Long.toTimeString(): String {
    val s   = (this / 1000).coerceAtLeast(0)
    val h   = s / 3600
    val m   = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun VideoPlayerScreen(
    initialIndex: Int,
    onBack:       () -> Unit,
    viewModel:    VideoPlayerViewModel = viewModel()
) {
    val context  = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()

    // ── Contrôles d'affichage ──────────────────────────────────────────────────
    var showControls     by remember { mutableStateOf(true) }
    var interactionCount by remember { mutableLongStateOf(0L) }

    /** Révèle les contrôles et redémarre le timer d'auto-masquage. */
    fun reveal() { showControls = true; interactionCount++ }

    // Auto-masquage après 3.5s d'inactivité
    LaunchedEffect(interactionCount) {
        if (showControls) {
            kotlinx.coroutines.delay(3_500)
            showControls = false
        }
    }

    // ── Scrubber local ─────────────────────────────────────────────────────────
    var isDragging   by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isDragging) dragPosition else uiState.progress

    // ── Orientation / Keep screen on ──────────────────────────────────────────
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            viewModel.exoPlayer?.pause()
        }
    }

    LaunchedEffect(Unit) { viewModel.initialize(context, initialIndex) }

    // ── Racine ────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Tap anywhere (hors boutons) → toggle contrôles
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (showControls) showControls = false else reveal()
                })
            }
    ) {

        // ── Surface vidéo ExoPlayer ───────────────────────────────────────────
        if (viewModel.exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player        = viewModel.exoPlayer
                        useController = false
                        layoutParams  = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update   = { it.player = viewModel.exoPlayer },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Chargement initial ────────────────────────────────────────────────
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp)
            }
        }

        // ── Erreur ────────────────────────────────────────────────────────────
        uiState.errorMessage?.let {
            Box(
                modifier         = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Text(it, color = Error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // ── Buffering (toujours visible, même sans contrôles) ─────────────────
        if (uiState.isBuffering && !uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color       = Primary,
                    strokeWidth = 3.dp,
                    modifier    = Modifier.size(52.dp)
                )
            }
        }

        // ── Overlay de contrôles (fade in/out) ────────────────────────────────
        AnimatedVisibility(
            visible = showControls && !uiState.isLoading && uiState.errorMessage == null,
            enter   = fadeIn(tween(200)),
            exit    = fadeOut(tween(300))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // ── Barre du haut ─────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(0.85f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Retour
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour",
                                tint               = Color.White
                            )
                        }

                        // Titre
                        Text(
                            text     = uiState.currentVideo?.name ?: "",
                            style    = MaterialTheme.typography.titleMedium,
                            color    = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )

                        // Compteur
                        Text(
                            text  = "${uiState.currentIndex + 1} / ${uiState.videos.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        // Boucle
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isLooping) Primary.copy(0.18f) else Color.Transparent
                                )
                                .clickable { viewModel.toggleLoop(); reveal() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Repeat,
                                contentDescription = "Boucle",
                                tint               = if (uiState.isLooping) Primary else Color.White.copy(0.5f),
                                modifier           = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }

                // ── Zone de contrôles en bas ───────────────────────────────────
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(0.92f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    // ── Scrubber ───────────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.fillMaxWidth()
                    ) {
                        // Temps courant
                        Text(
                            text  = uiState.currentPositionMs.toTimeString(),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )

                        Slider(
                            value              = displayProgress,
                            onValueChange      = { dragPosition = it; isDragging = true; reveal() },
                            onValueChangeFinished = {
                                viewModel.seekTo((dragPosition * uiState.durationMs).toLong())
                                isDragging = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor         = Primary,
                                activeTrackColor   = Primary,
                                inactiveTrackColor = Color.White.copy(0.25f),
                                activeTickColor    = Color.Transparent,
                                inactiveTickColor  = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )

                        // Durée totale
                        Text(
                            text  = uiState.durationMs.toTimeString(),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // ── Sélecteur de vitesse ───────────────────────────────────
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        PLAYBACK_SPEEDS.forEach { (speed, label) ->
                            val active = uiState.playbackSpeed == speed
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (active) Primary.copy(0.18f) else Color.White.copy(0.06f)
                                    )
                                    .border(
                                        1.dp,
                                        if (active) Primary.copy(0.6f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setSpeed(speed); reveal() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text       = label,
                                    color      = if (active) Primary else Color.White.copy(0.55f),
                                    fontSize   = 11.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            if (speed != PLAYBACK_SPEEDS.last().first) Spacer(Modifier.width(5.dp))
                        }
                    }

                    // ── Contrôles principaux ───────────────────────────────────
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        // Vidéo précédente
                        IconButton(
                            onClick  = { viewModel.playPrevious(); reveal() },
                            enabled  = uiState.hasPrevious,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Default.SkipPrevious,
                                contentDescription = "Précédent",
                                tint               = if (uiState.hasPrevious) Color.White else Color.White.copy(0.25f),
                                modifier           = Modifier.size(28.dp)
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        // Recul 10s
                        IconButton(
                            onClick  = { viewModel.seekBackward(); reveal() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Replay10,
                                contentDescription = "-10s",
                                tint               = Color.White,
                                modifier           = Modifier.size(30.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // Lecture / Pause — bouton central ambré
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .background(Primary, CircleShape)
                                .clickable(
                                    indication            = null,
                                    interactionSource     = remember { MutableInteractionSource() }
                                ) { viewModel.togglePlayPause(); reveal() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Pause" else "Lecture",
                                tint               = Color.Black,
                                modifier           = Modifier.size(36.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // Avance 10s
                        IconButton(
                            onClick  = { viewModel.seekForward(); reveal() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Forward10,
                                contentDescription = "+10s",
                                tint               = Color.White,
                                modifier           = Modifier.size(30.dp)
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        // Vidéo suivante
                        IconButton(
                            onClick  = { viewModel.playNext(); reveal() },
                            enabled  = uiState.hasNext,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Default.SkipNext,
                                contentDescription = "Suivant",
                                tint               = if (uiState.hasNext) Color.White else Color.White.copy(0.25f),
                                modifier           = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
