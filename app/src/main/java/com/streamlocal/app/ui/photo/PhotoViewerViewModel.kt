package com.streamlocal.app.ui.photo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamlocal.app.StreamLocalApp
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

/**
 * État de la visionneuse photo.
 *
 * @property photos             Liste complète des photos du serveur.
 * @property currentIndex       Index de la photo actuellement affichée.
 * @property isLoading          true pendant le chargement initial.
 * @property errorMessage       Erreur réseau ou serveur.
 * @property serverUrl          URL de base pour construire les URLs des photos.
 * @property trustAllCerts      Préférence SSL pour Coil.
 * @property isSlideshowPlaying true quand le diaporama automatique est en cours.
 * @property isLooping          true quand la lecture en boucle est activée.
 * @property slideshowInterval  Durée entre deux photos en secondes (2 / 4 / 8 / 15).
 * @property slideshowTick      Compteur incrémenté à chaque changement de photo.
 *                              Utilisé par le Screen pour réinitialiser l'animation de progression.
 */
data class PhotoViewerUiState(
    val photos:             List<MediaFile> = emptyList(),
    val currentIndex:       Int             = 0,
    val isLoading:          Boolean         = true,
    val errorMessage:       String?         = null,
    val serverUrl:          String          = "",
    val trustAllCerts:      Boolean         = true,
    val isSlideshowPlaying: Boolean         = false,
    val isLooping:          Boolean         = false,
    val slideshowInterval:  Int             = 4,
    val slideshowTick:      Long            = 0L
) {
    val currentPhoto: MediaFile? get() = photos.getOrNull(currentIndex)

    /** true si on peut aller en arrière (ou si la boucle est activée et il y a des photos). */
    val hasPrevious: Boolean get() = currentIndex > 0 || isLooping

    /** true si on peut aller en avant (ou si la boucle est activée et il y a des photos). */
    val hasNext: Boolean get() = currentIndex < photos.size - 1 || isLooping
}

/**
 * ViewModel de la visionneuse photo avec diaporama intégré.
 *
 * Fonctionnalités :
 * - Navigation manuelle (précédent / suivant / saut direct par index)
 * - Diaporama automatique avec [launchLoop] (coroutine qui avance toutes les N secondes)
 * - Lecture en boucle (retour à l'index 0 quand on atteint la fin)
 * - Intervalles configurables : 2s / 4s / 8s / 15s
 * - [slideshowTick] permet au Screen de synchroniser la barre de progression animée
 *
 * Le timer du diaporama est redémarré ([restartTimer]) après chaque navigation manuelle
 * pour éviter des sauts de photo trop rapides après une interaction utilisateur.
 */
class PhotoViewerViewModel : ViewModel() {

    private val repository  = MediaRepository()
    private val preferences = AppPreferences(StreamLocalApp.instance)

    private val _uiState = MutableStateFlow(PhotoViewerUiState())
    val uiState: StateFlow<PhotoViewerUiState> = _uiState.asStateFlow()

    /** Job de la boucle diaporama. Annulé quand le diaporama est mis en pause ou stoppé. */
    private var slideshowJob: Job? = null

    /**
     * Charge les préférences et la liste des photos depuis le serveur.
     * Doit être appelé une seule fois depuis un [LaunchedEffect(Unit)] dans le Screen.
     *
     * @param initialIndex Index de la photo sélectionnée dans l'écran d'accueil.
     */
    fun initialize(initialIndex: Int) {
        viewModelScope.launch {
            val url      = preferences.serverUrlFlow.first() ?: ""
            val trustAll = preferences.trustAllCertsFlow.first()
            _uiState.value = _uiState.value.copy(serverUrl = url, trustAllCerts = trustAll)

            when (val result = repository.getPhotos()) {
                is Result.Success -> {
                    val photos = result.data
                    _uiState.value = _uiState.value.copy(
                        photos       = photos,
                        currentIndex = initialIndex.coerceIn(0, maxOf(0, photos.size - 1)),
                        isLoading    = false
                    )
                }
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isLoading    = false,
                    errorMessage = result.message
                )
            }
        }
    }

    /**
     * Navigue vers la photo suivante.
     * - Si on est à la dernière photo et que la boucle est active → retour à l'index 0.
     * - Si le diaporama est actif → redémarre le timer pour éviter un saut immédiat.
     */
    fun goToNext() {
        val s = _uiState.value
        val next = when {
            s.currentIndex < s.photos.size - 1  -> s.currentIndex + 1
            s.isLooping && s.photos.isNotEmpty() -> 0
            else                                  -> return
        }
        _uiState.value = s.copy(currentIndex = next, slideshowTick = s.slideshowTick + 1)
        if (s.isSlideshowPlaying) restartTimer()
    }

    /**
     * Navigue vers la photo précédente.
     * - Si on est à la première photo et que la boucle est active → saute à la dernière.
     * - Si le diaporama est actif → redémarre le timer.
     */
    fun goToPrevious() {
        val s = _uiState.value
        val prev = when {
            s.currentIndex > 0                   -> s.currentIndex - 1
            s.isLooping && s.photos.isNotEmpty() -> s.photos.size - 1
            else                                  -> return
        }
        _uiState.value = s.copy(currentIndex = prev, slideshowTick = s.slideshowTick + 1)
        if (s.isSlideshowPlaying) restartTimer()
    }

    /**
     * Saute directement à la photo d'index [index] (utilisé par les dot indicators).
     * Si le diaporama est actif → redémarre le timer.
     */
    fun goTo(index: Int) {
        val s = _uiState.value
        if (index !in s.photos.indices) return
        _uiState.value = s.copy(currentIndex = index, slideshowTick = s.slideshowTick + 1)
        if (s.isSlideshowPlaying) restartTimer()
    }

    /** Démarre ou met en pause le diaporama automatique. */
    fun toggleSlideshow() {
        if (_uiState.value.isSlideshowPlaying) stopSlideshow() else startSlideshow()
    }

    /** Active ou désactive la lecture en boucle. */
    fun toggleLoop() {
        _uiState.value = _uiState.value.copy(isLooping = !_uiState.value.isLooping)
    }

    /**
     * Fait défiler l'intervalle du diaporama dans la séquence : 2s → 4s → 8s → 15s → 2s.
     * Si le diaporama est actif, redémarre le timer avec le nouvel intervalle.
     */
    fun cycleInterval() {
        val steps = listOf(2, 4, 8, 15)
        val idx   = steps.indexOf(_uiState.value.slideshowInterval)
        _uiState.value = _uiState.value.copy(slideshowInterval = steps[(idx + 1) % steps.size])
        if (_uiState.value.isSlideshowPlaying) restartTimer()
    }

    private fun startSlideshow() {
        _uiState.value = _uiState.value.copy(isSlideshowPlaying = true)
        launchLoop()
    }

    private fun stopSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
        _uiState.value = _uiState.value.copy(isSlideshowPlaying = false)
    }

    /** Annule le timer en cours et en repart depuis zéro (après navigation manuelle). */
    private fun restartTimer() {
        slideshowJob?.cancel()
        launchLoop()
    }

    /**
     * Lance la coroutine principale du diaporama.
     *
     * La boucle attend [slideshowInterval] secondes, puis :
     * - Avance à la photo suivante si possible.
     * - Retourne à la première photo si la boucle est activée.
     * - S'arrête automatiquement si on est à la dernière photo sans boucle.
     *
     * [slideshowTick] est incrémenté à chaque avance pour signaler au Screen
     * qu'il doit réinitialiser son [Animatable] de progression.
     */
    private fun launchLoop() {
        slideshowJob = viewModelScope.launch {
            while (true) {
                delay(_uiState.value.slideshowInterval * 1000L)
                val s = _uiState.value
                if (!s.isSlideshowPlaying) break
                when {
                    s.currentIndex < s.photos.size - 1 -> _uiState.value = s.copy(
                        currentIndex  = s.currentIndex + 1,
                        slideshowTick = s.slideshowTick + 1
                    )
                    s.isLooping -> _uiState.value = s.copy(
                        currentIndex  = 0,
                        slideshowTick = s.slideshowTick + 1
                    )
                    else -> {
                        // Fin de la galerie sans boucle : arrêt automatique
                        _uiState.value = s.copy(isSlideshowPlaying = false)
                        break
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        slideshowJob?.cancel()
    }

    /**
     * Construit l'URL complète de la photo pour Coil.
     *
     * @param photo Fichier photo dont on veut l'URL.
     * @return URL complète (ex: "http://192.168.1.10:5000/api/v1/stream/photo/albums/img.jpg")
     */
    fun getPhotoUrl(photo: MediaFile): String =
        repository.buildPhotoStreamUrl(_uiState.value.serverUrl, photo.path)
}
