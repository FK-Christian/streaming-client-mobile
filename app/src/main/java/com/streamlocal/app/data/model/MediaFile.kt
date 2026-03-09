package com.streamlocal.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Représente un fichier média (vidéo ou photo) retourné par l'API `/files/videos` ou `/files/photos`.
 *
 * @property name Nom du fichier avec extension (ex: "vacances.mp4")
 * @property path Chemin relatif sur le serveur, utilisé pour construire l'URL de streaming.
 * @property ext  Extension sans point, en minuscules (ex: "mp4", "jpg").
 * @property size Taille formatée par le serveur (ex: "3.9 Mo", "120 Ko").
 *                Stockée comme String car le serveur retourne une valeur pré-formatée.
 */
data class MediaFile(
    @SerializedName("name") val name: String,
    @SerializedName("path") val path: String,
    @SerializedName("ext")  val ext: String,
    @SerializedName("size") val size: String = ""
) {
    /** Retourne la taille telle que fournie par le serveur (déjà formatée lisiblement). */
    fun formattedSize(): String = size
}

/**
 * Corps de la requête d'authentification POST `/api/v1/auth`.
 *
 * @property code Code PIN saisi par l'utilisateur.
 */
data class AuthRequest(
    @SerializedName("code") val code: String
)

/**
 * Réponse du serveur à la requête d'authentification.
 *
 * @property ok  true si l'authentification a réussi.
 * @property msg Message d'erreur optionnel retourné par le serveur.
 */
data class AuthResponse(
    @SerializedName("ok")  val ok: Boolean,
    @SerializedName("msg") val msg: String? = null
)

/**
 * Métadonnées détaillées d'une vidéo, retournées par `/api/v1/info/video/{path}`.
 *
 * Tous les champs ont des valeurs par défaut car l'endpoint est optionnel
 * et peut retourner des données partielles.
 *
 * @property size     Taille formatée (ex: "1.2 Go").
 * @property duration Durée en secondes.
 * @property width    Largeur en pixels.
 * @property height   Hauteur en pixels.
 * @property codec    Codec vidéo (ex: "h264").
 * @property bitrate  Débit binaire formaté (ex: "4 Mb/s").
 */
data class VideoInfo(
    @SerializedName("name")      val name: String     = "",
    @SerializedName("path")      val path: String     = "",
    @SerializedName("size")      val size: String     = "",
    @SerializedName("duration")  val duration: Double = 0.0,
    @SerializedName("width")     val width: Int       = 0,
    @SerializedName("height")    val height: Int      = 0,
    @SerializedName("codec")     val codec: String    = "",
    @SerializedName("bitrate")   val bitrate: String  = ""
)

/**
 * Métadonnées détaillées d'une photo, retournées par `/api/v1/info/photo/{path}`.
 *
 * @property size   Taille formatée (ex: "4.2 Mo").
 * @property width  Largeur en pixels.
 * @property height Hauteur en pixels.
 */
data class PhotoInfo(
    @SerializedName("name")   val name: String   = "",
    @SerializedName("path")   val path: String   = "",
    @SerializedName("size")   val size: String   = "",
    @SerializedName("width")  val width: Int     = 0,
    @SerializedName("height") val height: Int    = 0
)

/**
 * Réponse du serveur après l'envoi d'un fichier via POST `/api/v1/upload`.
 *
 * @property ok  true si l'upload a réussi.
 * @property msg Message de confirmation ou d'erreur retourné par le serveur.
 */
data class UploadResponse(
    @SerializedName("ok")  val ok: Boolean,
    @SerializedName("msg") val msg: String? = null
)
