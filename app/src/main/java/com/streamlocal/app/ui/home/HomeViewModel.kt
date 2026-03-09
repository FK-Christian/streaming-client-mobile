package com.streamlocal.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamlocal.app.StreamLocalApp
import com.streamlocal.app.data.model.MediaFile
import com.streamlocal.app.data.preferences.AppPreferences
import com.streamlocal.app.data.repository.MediaRepository
import com.streamlocal.app.data.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Onglet sélectionné dans l'écran d'accueil. */
enum class HomeTab { VIDEOS, PHOTOS }

/**
 * État de l'écran d'accueil.
 *
 * @property selectedTab   Onglet actuellement affiché.
 * @property videos        Liste complète des vidéos chargées depuis le serveur.
 * @property photos        Liste complète des photos chargées depuis le serveur.
 * @property isLoading     true pendant le chargement initial ou le refresh.
 * @property errorMessage  Message d'erreur affiché à la place de la liste.
 * @property searchQuery   Texte de la barre de recherche.
 * @property serverUrl     URL du serveur (chargée des prefs, non modifiable ici).
 * @property trustAllCerts Préférence SSL (transmise aux appels réseau).
 * @property isLoggedOut   true quand la déconnexion a été demandée (déclenche la navigation).
 */
data class HomeUiState(
    val selectedTab:   HomeTab         = HomeTab.VIDEOS,
    val videos:        List<MediaFile> = emptyList(),
    val photos:        List<MediaFile> = emptyList(),
    val isLoading:     Boolean         = false,
    val errorMessage:  String?         = null,
    val searchQuery:   String          = "",
    val serverUrl:     String          = "",
    val trustAllCerts: Boolean         = true,
    val isLoggedOut:   Boolean         = false,
    val isUploading:   Boolean         = false,
    val uploadMessage: String?         = null,
    val uploadError:   String?         = null
)

/**
 * ViewModel de l'écran d'accueil.
 *
 * Responsabilités :
 * - Charger les listes de vidéos/photos depuis le serveur au démarrage.
 * - Gérer la sélection d'onglet (avec rechargement si nécessaire).
 * - Filtrer les résultats selon la recherche en temps réel (côté client).
 * - Déclencher la déconnexion (effacement des cookies + signal de navigation).
 */
class HomeViewModel : ViewModel() {

    private val repository  = MediaRepository()
    private val preferences = AppPreferences(StreamLocalApp.instance)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Charge les préférences puis déclenche le chargement de l'onglet actif
        viewModelScope.launch {
            val url      = preferences.serverUrlFlow.first() ?: ""
            val trustAll = preferences.trustAllCertsFlow.first()
            _uiState.value = _uiState.value.copy(serverUrl = url, trustAllCerts = trustAll)
            loadCurrentTab()
        }
    }

    /** Change l'onglet actif et recharge les données si pas encore disponibles. */
    fun onTabSelected(tab: HomeTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab, searchQuery = "")
        viewModelScope.launch { loadCurrentTab() }
    }

    /** Met à jour la requête de recherche (le filtrage est appliqué dans [getFilteredVideos] / [getFilteredPhotos]). */
    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    /** Force le rechargement de l'onglet courant (pull-to-refresh ou bouton refresh). */
    fun refresh() {
        viewModelScope.launch { loadCurrentTab() }
    }

    /** Charge les données de l'onglet actuellement sélectionné. */
    private suspend fun loadCurrentTab() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        when (_uiState.value.selectedTab) {
            HomeTab.VIDEOS -> loadVideos()
            HomeTab.PHOTOS -> loadPhotos()
        }
    }

    private suspend fun loadVideos() {
        when (val result = repository.getVideos()) {
            is Result.Success -> _uiState.value = _uiState.value.copy(
                videos    = result.data,
                isLoading = false
            )
            is Result.Error -> _uiState.value = _uiState.value.copy(
                errorMessage = result.message,
                isLoading    = false
            )
        }
    }

    private suspend fun loadPhotos() {
        when (val result = repository.getPhotos()) {
            is Result.Success -> _uiState.value = _uiState.value.copy(
                photos    = result.data,
                isLoading = false
            )
            is Result.Error -> _uiState.value = _uiState.value.copy(
                errorMessage = result.message,
                isLoading    = false
            )
        }
    }

    /**
     * Déconnecte l'utilisateur :
     * 1. Appelle `/api/v1/logout` pour invalider la session côté serveur.
     * 2. Efface les cookies locaux via [NetworkClient.clearCookies].
     * 3. Signale la navigation via [HomeUiState.isLoggedOut].
     */
    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.value = _uiState.value.copy(isLoggedOut = true)
        }
    }

    /**
     * Réinitialise [HomeUiState.isLoggedOut] après que la navigation a été déclenchée,
     * évitant une double navigation sur recomposition.
     */
    fun resetLoggedOut() {
        _uiState.value = _uiState.value.copy(isLoggedOut = false)
    }

    /**
     * Envoie un fichier image ou vidéo au serveur, puis rafraîchit la liste.
     *
     * @param context Contexte Android (application context recommandé).
     * @param uri     URI du fichier sélectionné via le sélecteur système.
     */
    fun uploadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploading   = true,
                uploadMessage = null,
                uploadError   = null
            )
            when (val result = repository.uploadFile(context, uri)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUploading   = false,
                        uploadMessage = result.data
                    )
                    loadCurrentTab()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        uploadError = result.message
                    )
                }
            }
        }
    }

    /** Efface les messages d'état de l'upload. */
    fun clearUploadStatus() {
        _uiState.value = _uiState.value.copy(uploadMessage = null, uploadError = null)
    }

    /**
     * Retourne la liste des vidéos filtrée selon [HomeUiState.searchQuery].
     * Le filtrage est insensible à la casse et cherche dans le nom du fichier.
     */
    fun getFilteredVideos(): List<MediaFile> {
        val query = _uiState.value.searchQuery.trim().lowercase()
        return if (query.isBlank()) _uiState.value.videos
        else _uiState.value.videos.filter { it.name.lowercase().contains(query) }
    }

    /**
     * Retourne la liste des photos filtrée selon [HomeUiState.searchQuery].
     * Même logique que [getFilteredVideos].
     */
    fun getFilteredPhotos(): List<MediaFile> {
        val query = _uiState.value.searchQuery.trim().lowercase()
        return if (query.isBlank()) _uiState.value.photos
        else _uiState.value.photos.filter { it.name.lowercase().contains(query) }
    }
}
