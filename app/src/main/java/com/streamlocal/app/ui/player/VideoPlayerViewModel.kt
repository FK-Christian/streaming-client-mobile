@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamlocal.app.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.streamlocal.app.StreamLocalApp
import com.streamlocal.app.data.api.NetworkClient
import com.streamlocal.app.data.model.MediaFile
import com.streamlocal.app.data.preferences.AppPreferences
import com.streamlocal.app.data.repository.MediaRepository
import com.streamlocal.app.data.repository.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class VideoPlayerUiState(
    val videos:            List<MediaFile> = emptyList(),
    val currentIndex:      Int             = 0,
    val isLoading:         Boolean         = true,
    val errorMessage:      String?         = null,
    val serverUrl:         String          = "",
    val trustAllCerts:     Boolean         = true,
    // Playback state
    val isPlaying:         Boolean         = false,
    val isBuffering:       Boolean         = false,
    val isLooping:         Boolean         = false,
    val playbackSpeed:     Float           = 1f,
    val currentPositionMs: Long            = 0L,
    val durationMs:        Long            = 0L,
) {
    val currentVideo: MediaFile? get() = videos.getOrNull(currentIndex)
    val hasPrevious:  Boolean    get() = currentIndex > 0
    val hasNext:      Boolean    get() = currentIndex < videos.size - 1
    /** Progression normalisée 0f..1f pour le Slider. */
    val progress: Float get() = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f
}

/** Vitesses de lecture disponibles avec leur label d'affichage. */
val PLAYBACK_SPEEDS = listOf(0.5f to "0.5×", 0.75f to "0.75×", 1f to "1×", 1.5f to "1.5×", 2f to "2×")

class VideoPlayerViewModel : ViewModel() {

    private val repository  = MediaRepository()
    private val preferences = AppPreferences(StreamLocalApp.instance)

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    var exoPlayer: ExoPlayer? = null
        private set

    /** Job de tracking de la position (tourne toutes les 200ms quand en lecture). */
    private var positionJob: Job? = null

    fun initialize(context: Context, initialIndex: Int) {
        viewModelScope.launch {
            val url      = preferences.serverUrlFlow.first() ?: ""
            val trustAll = preferences.trustAllCertsFlow.first()
            _uiState.value = _uiState.value.copy(serverUrl = url, trustAllCerts = trustAll)

            when (val result = repository.getVideos()) {
                is Result.Success -> {
                    val videos = result.data
                    _uiState.value = _uiState.value.copy(
                        videos       = videos,
                        currentIndex = initialIndex.coerceIn(0, maxOf(0, videos.size - 1)),
                        isLoading    = false
                    )
                    setupPlayer(context)
                    playCurrentVideo()
                }
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isLoading    = false,
                    errorMessage = result.message
                )
            }
        }
    }

    private fun setupPlayer(context: Context) {
        val httpClient        = NetworkClient.buildExoPlayerClient(_uiState.value.trustAllCerts)
        val dataSourceFactory = OkHttpDataSource.Factory(httpClient)

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(dataSourceFactory))
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                        if (isPlaying) startPositionTracking() else positionJob?.cancel()
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        val duration = player.duration.coerceAtLeast(0L)
                        _uiState.value = _uiState.value.copy(
                            isBuffering = state == Player.STATE_BUFFERING,
                            durationMs  = duration
                        )
                        // Auto-avance à la fin, sauf si on est en mode boucle (géré par ExoPlayer)
                        if (state == Player.STATE_ENDED && !_uiState.value.isLooping && _uiState.value.hasNext) {
                            playNext()
                        }
                    }
                })
            }
    }

    private fun playCurrentVideo() {
        val video = _uiState.value.currentVideo ?: return
        val url   = repository.buildVideoStreamUrl(_uiState.value.serverUrl, video.path)
        exoPlayer?.apply {
            setMediaItem(MediaItem.fromUri(url))
            setPlaybackSpeed(_uiState.value.playbackSpeed)
            repeatMode    = if (_uiState.value.isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }

    /** Lance un coroutine qui met à jour la position toutes les 200ms. */
    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                delay(200)
                val player = exoPlayer ?: break
                _uiState.value = _uiState.value.copy(
                    currentPositionMs = player.currentPosition,
                    durationMs        = player.duration.coerceAtLeast(0L)
                )
            }
        }
    }

    // ── Contrôles de lecture ──────────────────────────────────────────────────

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun playNext() {
        if (!_uiState.value.hasNext) return
        _uiState.value = _uiState.value.copy(currentIndex = _uiState.value.currentIndex + 1)
        playCurrentVideo()
    }

    /** Précédent intelligent : retour au début si > 3s écoulées, sinon vidéo précédente. */
    fun playPrevious() {
        val position = exoPlayer?.currentPosition ?: 0
        if (position > 3_000L) {
            exoPlayer?.seekTo(0)
            return
        }
        if (!_uiState.value.hasPrevious) return
        _uiState.value = _uiState.value.copy(currentIndex = _uiState.value.currentIndex - 1)
        playCurrentVideo()
    }

    /** Avance rapide de [seconds] secondes (défaut 10s). */
    fun seekForward(seconds: Int = 10) {
        val player = exoPlayer ?: return
        val target = (player.currentPosition + seconds * 1000L).coerceAtMost(player.duration.coerceAtLeast(0L))
        player.seekTo(target)
        _uiState.value = _uiState.value.copy(currentPositionMs = target)
    }

    /** Retour rapide de [seconds] secondes (défaut 10s). */
    fun seekBackward(seconds: Int = 10) {
        val player = exoPlayer ?: return
        val target = (player.currentPosition - seconds * 1000L).coerceAtLeast(0L)
        player.seekTo(target)
        _uiState.value = _uiState.value.copy(currentPositionMs = target)
    }

    /** Seek vers une position précise en millisecondes (depuis le Slider). */
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(currentPositionMs = positionMs)
    }

    /** Active/désactive la répétition de la vidéo en cours. */
    fun toggleLoop() {
        val newLooping = !_uiState.value.isLooping
        _uiState.value = _uiState.value.copy(isLooping = newLooping)
        exoPlayer?.repeatMode = if (newLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** Change la vitesse de lecture parmi [PLAYBACK_SPEEDS]. */
    fun setSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        exoPlayer?.setPlaybackSpeed(speed)
    }

    override fun onCleared() {
        super.onCleared()
        positionJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
}
