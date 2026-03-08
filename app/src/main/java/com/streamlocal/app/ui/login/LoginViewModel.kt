package com.streamlocal.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamlocal.app.StreamLocalApp
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
 * État de l'écran de connexion.
 *
 * @property pin             Code PIN en cours de saisie.
 * @property isLoading       true pendant la requête d'authentification.
 * @property errorMessage    Message d'erreur à afficher sous le champ PIN.
 * @property shakeError      true pendant 600ms lors d'une erreur (déclenche l'animation de secousse).
 * @property isAuthenticated true quand l'authentification a réussi (déclenche la navigation).
 * @property isRateLimited   true quand le serveur retourne HTTP 429.
 * @property rateLimitCountdown Secondes restantes avant de pouvoir réessayer.
 * @property serverUrl       URL du serveur affichée en bas de l'écran (informatif).
 */
data class LoginUiState(
    val pin:                String  = "",
    val isLoading:          Boolean = false,
    val errorMessage:       String? = null,
    val shakeError:         Boolean = false,
    val isAuthenticated:    Boolean = false,
    val isRateLimited:      Boolean = false,
    val rateLimitCountdown: Int     = 0,
    val serverUrl:          String  = ""
)

/**
 * ViewModel de l'écran de connexion.
 *
 * Gère :
 * - La saisie du code PIN (validation avant envoi)
 * - L'appel à l'API d'authentification
 * - L'animation de secousse en cas d'erreur ([LoginUiState.shakeError])
 * - Le rate limiting HTTP 429 avec un décompte en temps réel
 */
class LoginViewModel : ViewModel() {

    private val repository  = MediaRepository()
    private val preferences = AppPreferences(StreamLocalApp.instance)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** Job du décompte de rate-limit. Annulé si le ViewModel est détruit. */
    private var countdownJob: Job? = null

    init {
        // Charge l'URL du serveur pour l'afficher sur l'écran (informatif uniquement)
        viewModelScope.launch {
            val url = preferences.serverUrlFlow.first() ?: ""
            _uiState.value = _uiState.value.copy(serverUrl = url)
        }
    }

    /**
     * Appelé à chaque frappe dans le champ PIN.
     * Ignoré si l'utilisateur est en rate-limit (saisie bloquée).
     */
    fun onPinChange(value: String) {
        if (_uiState.value.isRateLimited) return
        _uiState.value = _uiState.value.copy(
            pin          = value,
            errorMessage = null,
            shakeError   = false
        )
    }

    /**
     * Déclenche la tentative d'authentification avec le PIN actuel.
     *
     * Guards : PIN vide, requête en cours, ou rate-limit actif → aucune action.
     */
    fun login() {
        val pin = _uiState.value.pin.trim()
        if (pin.isBlank() || _uiState.value.isLoading || _uiState.value.isRateLimited) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading    = true,
                errorMessage = null,
                shakeError   = false
            )

            when (val result = repository.authenticate(pin)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading       = false,
                        isAuthenticated = true   // déclenche la navigation dans le Screen
                    )
                }
                is Result.Error -> {
                    if (result.code == 429) {
                        // Extrait le nombre de secondes depuis le message d'erreur
                        val seconds = Regex("\\d+").find(result.message)?.value?.toIntOrNull() ?: 60
                        startRateLimitCountdown(seconds)
                        _uiState.value = _uiState.value.copy(
                            isLoading          = false,
                            errorMessage       = result.message,
                            shakeError         = true,
                            isRateLimited      = true,
                            rateLimitCountdown = seconds
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading    = false,
                            errorMessage = result.message,
                            shakeError   = true,
                            pin          = ""  // vide le champ pour forcer une nouvelle saisie
                        )
                    }
                    // Désactive le flag de secousse après la durée de l'animation (600ms)
                    delay(600)
                    _uiState.value = _uiState.value.copy(shakeError = false)
                }
            }
        }
    }

    /**
     * Lance un décompte en temps réel du temps restant avant de pouvoir réessayer.
     *
     * Met à jour [LoginUiState.rateLimitCountdown] et [LoginUiState.errorMessage]
     * chaque seconde. Déverrouille la saisie quand le décompte atteint 0.
     *
     * @param seconds Nombre initial de secondes à attendre (extrait du message serveur).
     */
    private fun startRateLimitCountdown(seconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.value = _uiState.value.copy(
                    rateLimitCountdown = remaining,
                    errorMessage       = "Trop de tentatives. Réessayez dans ${remaining}s"
                )
            }
            // Déverrouillage : efface l'état de rate-limit
            _uiState.value = _uiState.value.copy(
                isRateLimited      = false,
                rateLimitCountdown = 0,
                errorMessage       = null
            )
        }
    }

    /**
     * Réinitialise le flag [LoginUiState.isAuthenticated] après que la navigation
     * a été déclenchée, évitant une double navigation sur recomposition.
     */
    fun resetAuthenticated() {
        _uiState.value = _uiState.value.copy(isAuthenticated = false)
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
