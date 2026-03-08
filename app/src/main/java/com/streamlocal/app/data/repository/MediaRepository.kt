package com.streamlocal.app.data.repository

import com.streamlocal.app.data.api.NetworkClient
import com.streamlocal.app.data.model.AuthRequest
import com.streamlocal.app.data.model.MediaFile

/**
 * Wrapper de résultat typé pour toutes les opérations du repository.
 *
 * Évite l'utilisation des exceptions comme flux de contrôle et force
 * les appelants à gérer explicitement les cas succès/erreur.
 */
sealed class Result<out T> {
    /** Opération réussie avec les données [data]. */
    data class Success<T>(val data: T) : Result<T>()

    /**
     * Opération échouée.
     * @param message Message d'erreur lisible par l'utilisateur (en français).
     * @param code    Code HTTP associé, -1 si l'erreur est réseau/locale.
     */
    data class Error(val message: String, val code: Int = -1) : Result<Nothing>()
}

/**
 * Couche d'accès aux données : fait le pont entre les ViewModels et l'API REST.
 *
 * Toutes les méthodes sont suspendantes et doivent être appelées depuis une coroutine.
 * Les exceptions réseau sont capturées et converties en [Result.Error].
 */
class MediaRepository {

    /** Accès à l'API Retrofit via le singleton NetworkClient. */
    private val api get() = NetworkClient.api

    // ── Authentification ──────────────────────────────────────────────────────

    /**
     * Authentifie l'utilisateur avec son code PIN.
     *
     * Codes HTTP gérés :
     * - 200 + ok=true  → succès
     * - 200 + ok=false → code invalide
     * - 401            → non autorisé
     * - 429            → trop de tentatives, retourne le délai via l'en-tête Retry-After
     *
     * @param code Code PIN saisi par l'utilisateur.
     */
    suspend fun authenticate(code: String): Result<Boolean> {
        return try {
            val response = api.authenticate(AuthRequest(code))
            when (response.code()) {
                200 -> {
                    val body = response.body()
                    if (body?.ok == true) Result.Success(true)
                    else Result.Error(body?.msg ?: "Code invalide", 401)
                }
                401 -> Result.Error(response.body()?.msg ?: "Code invalide", 401)
                429 -> {
                    // Le serveur indique combien de secondes attendre via Retry-After
                    val retryAfter = response.headers()["Retry-After"]?.toIntOrNull() ?: 60
                    Result.Error("Trop de tentatives. Réessayez dans ${retryAfter}s", 429)
                }
                else -> Result.Error("Erreur serveur: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur de connexion")
        }
    }

    /**
     * Déconnecte l'utilisateur côté serveur et efface les cookies locaux.
     *
     * Les cookies sont effacés même si la requête réseau échoue,
     * garantissant un état propre côté client.
     */
    suspend fun logout(): Result<Unit> {
        return try {
            api.logout()
            NetworkClient.clearCookies()
            Result.Success(Unit)
        } catch (e: Exception) {
            // On efface les cookies quoi qu'il arrive
            NetworkClient.clearCookies()
            Result.Success(Unit)
        }
    }

    // ── Fichiers médias ───────────────────────────────────────────────────────

    /**
     * Récupère la liste de toutes les vidéos disponibles sur le serveur.
     *
     * @return Liste de [MediaFile] ou [Result.Error] en cas d'échec réseau/serveur.
     */
    suspend fun getVideos(): Result<List<MediaFile>> {
        return try {
            val response = api.getVideos()
            if (response.isSuccessful) {
                Result.Success(response.body() ?: emptyList())
            } else {
                Result.Error("Erreur: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur de connexion")
        }
    }

    /**
     * Récupère la liste de toutes les photos disponibles sur le serveur.
     *
     * @return Liste de [MediaFile] ou [Result.Error] en cas d'échec réseau/serveur.
     */
    suspend fun getPhotos(): Result<List<MediaFile>> {
        return try {
            val response = api.getPhotos()
            if (response.isSuccessful) {
                Result.Success(response.body() ?: emptyList())
            } else {
                Result.Error("Erreur: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur de connexion")
        }
    }

    // ── Construction des URLs de streaming ───────────────────────────────────

    /**
     * Construit l'URL de streaming complète pour une vidéo.
     *
     * @param baseUrl URL de base du serveur (ex: "http://192.168.1.10:5000")
     * @param path    Chemin relatif du fichier retourné par l'API (ex: "films/video.mp4")
     * @return URL complète utilisable par ExoPlayer.
     */
    fun buildVideoStreamUrl(baseUrl: String, path: String): String {
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
        return "$normalizedBase/api/v1/stream/video/$path"
    }

    /**
     * Construit l'URL d'accès complète pour une photo.
     *
     * @param baseUrl URL de base du serveur.
     * @param path    Chemin relatif du fichier retourné par l'API.
     * @return URL complète utilisable par Coil.
     */
    fun buildPhotoStreamUrl(baseUrl: String, path: String): String {
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
        return "$normalizedBase/api/v1/stream/photo/$path"
    }
}
