package com.streamlocal.app.data.api

import com.streamlocal.app.data.model.AuthRequest
import com.streamlocal.app.data.model.AuthResponse
import com.streamlocal.app.data.model.MediaFile
import com.streamlocal.app.data.model.PhotoInfo
import com.streamlocal.app.data.model.UploadResponse
import com.streamlocal.app.data.model.VideoInfo
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Interface Retrofit définissant tous les endpoints de l'API REST StreamLocal.
 *
 * Les URLs sont relatives à la baseUrl configurée dans [NetworkClient].
 * Toutes les fonctions sont suspendantes pour une utilisation dans des coroutines.
 *
 * Authentification : basée sur les cookies de session Flask.
 * Le cookie est automatiquement géré par le [CookieJar] de [NetworkClient].
 */
interface StreamApi {

    /**
     * Authentifie l'utilisateur avec son code PIN.
     *
     * POST /api/v1/auth
     * Body : { "code": "1234" }
     * Retour 200 : { "ok": true/false, "msg": "..." }
     * Retour 401 : code invalide
     * Retour 429 : trop de tentatives, header Retry-After indique le délai
     */
    @POST("api/v1/auth")
    suspend fun authenticate(@Body request: AuthRequest): Response<AuthResponse>

    /**
     * Invalide la session côté serveur.
     *
     * GET /api/v1/logout
     * Les cookies locaux sont effacés séparément via [NetworkClient.clearCookies].
     */
    @GET("api/v1/logout")
    suspend fun logout(): Response<Unit>

    /**
     * Récupère la liste de toutes les vidéos disponibles.
     *
     * GET /api/v1/files/videos
     * Retour : tableau JSON de [MediaFile]
     * Nécessite une session authentifiée (cookie Flask).
     */
    @GET("api/v1/files/videos")
    suspend fun getVideos(): Response<List<MediaFile>>

    /**
     * Récupère la liste de toutes les photos disponibles.
     *
     * GET /api/v1/files/photos
     * Retour : tableau JSON de [MediaFile]
     * Nécessite une session authentifiée (cookie Flask).
     */
    @GET("api/v1/files/photos")
    suspend fun getPhotos(): Response<List<MediaFile>>

    /**
     * Récupère les métadonnées détaillées d'une vidéo.
     *
     * GET /api/v1/info/video/{path}
     * Le paramètre [path] est encodé pour supporter les sous-dossiers (slashes inclus).
     * Retour : [VideoInfo] avec durée, résolution, codec, débit.
     */
    @GET("api/v1/info/video/{path}")
    suspend fun getVideoInfo(@Path("path", encoded = true) path: String): Response<VideoInfo>

    /**
     * Récupère les métadonnées détaillées d'une photo.
     *
     * GET /api/v1/info/photo/{path}
     * Le paramètre [path] est encodé pour supporter les sous-dossiers.
     * Retour : [PhotoInfo] avec dimensions en pixels.
     */
    @GET("api/v1/info/photo/{path}")
    suspend fun getPhotoInfo(@Path("path", encoded = true) path: String): Response<PhotoInfo>

    /**
     * Envoie une vidéo au serveur.
     *
     * POST /api/v1/upload/video
     * Corps : multipart/form-data avec le champ "file".
     * Retour 200 : { "ok": true, "filename": "...", "dest": "..." }
     * Retour 400 : fichier manquant ou extension non autorisée.
     */
    @Multipart
    @POST("api/v1/upload/video")
    suspend fun uploadVideo(@Part file: MultipartBody.Part): Response<UploadResponse>

    /**
     * Envoie une photo au serveur.
     *
     * POST /api/v1/upload/photo
     * Corps : multipart/form-data avec le champ "file".
     * Retour 200 : { "ok": true, "filename": "...", "dest": "..." }
     * Retour 400 : fichier manquant ou extension non autorisée.
     */
    @Multipart
    @POST("api/v1/upload/photo")
    suspend fun uploadPhoto(@Part file: MultipartBody.Part): Response<UploadResponse>
}
