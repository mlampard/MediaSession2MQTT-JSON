package be.digitalia.mediasession2mqtt.mqttmediaplayer

import android.media.MediaMetadata
import android.media.Rating
import android.media.session.PlaybackState
//import android.util.Base64
//import android.graphics.Bitmap
//import java.io.ByteArrayOutputStream

/**
 * Convert the MediaSession state to a simplified MQTT state.
 * Unsupported transient state values will return null and must not be reported.
 * Note: the buffering state is voluntarily ignored and not considered equal to playing
 * because some applications pre-buffer playback even before the user requests playing the content.
 */
fun PlaybackState?.toMQTTPlaybackStateOrNull(): MQTTPlaybackState? {
    if (this == null) {
        return null
    }
    return when (state) {
        PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR -> MQTTPlaybackState.idle
        PlaybackState.STATE_PLAYING -> MQTTPlaybackState.playing
        PlaybackState.STATE_PAUSED -> MQTTPlaybackState.paused
        else -> null
    }
}

fun getNullableLong(value: Long): String? {
    return if (value == 0L) {
        null
    }else{
        value.toString()
    }
}

/*
fun imageToBase64(bitmap: Bitmap?): String? {
    return (if (bitmap==null ) {
        null
    }else {
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 70, stream)
            val byteArray = stream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)
        }finally {
            null
        }
    }) as String?
}
*/

fun decodeRating(rating: Rating?): String? {
    return (if (rating == null){
        null
    }else{
        if (rating.isRated) {
            if (rating.ratingStyle == Rating.RATING_NONE) {
                return "not_rated"
            }
            if (   rating.ratingStyle == Rating.RATING_5_STARS
                || rating.ratingStyle == Rating.RATING_4_STARS
                || rating.ratingStyle == Rating.RATING_3_STARS) {
                   return "${rating.starRating} / ${rating.ratingStyle}"
            }
            if (rating.ratingStyle == Rating.RATING_HEART) {
                if(rating.hasHeart()){return "liked"} else {return "unliked"}
            }
            if (rating.ratingStyle == Rating.RATING_THUMB_UP_DOWN) {
                if(rating.isThumbUp()){return "thumb-up"} else {return "thumb-down"}
            }
            if(rating.ratingStyle == Rating.RATING_PERCENTAGE) {
                return "${rating.percentRating}%"
            } else {
                return "unknown"
            }
        }else {
            return "unrated"
        }
    })
}
/**
 * Extract the current media title, or return an empty String if none is available.
 */
fun MediaMetadata?.toMediaTitle(): String {
    if (this == null) {
        return ""
    }
    // return a formatted json string of all available MediaMetadata strings for current mediaPlayer
    // chop known prefixes, leaving app-specific ones. Which strings are output is dependent
    // on the app. In HomeAssistant these will be attributes of the media_title sensor like so:
    // A music player:
    // artist: Some Artist
    // duration: "220810"
    // user_rating: "Rating:style=2 rating=unrated"
    // art: ""
    // album_art_uri: https://some-url.com/foo.jpg
    // com.fooplayer.metadata.track_id: fooin://some_track_id/
    // media_id: some_track_id
    // album: Some Greatest Hits from Some Artist
    // title: Just in my head
    // album_artist: Some Artist and their friends
    //
    // A video app output media_title might have attributes something like:
    // artist: A sock wearer
    // duration: "4548484"
    // art: ""
    // album: ""
    // title: My shoes are too big
    //
    //or a TV app might provide:
    // title: Bon Voyage Mr Smart-e-Pants S1 Ep1 - Twenty Days on a Desert Island
    // subtitle: 23 - No Drama Channel, 8:32 pm - 9:32 pm
    // description: >-
    //   Fred finds himself stranded on a desert island with a can of beer and a banana.
    // icon_uri: https://sometv.someplace.com/program/544786.jpg

    var desc: String?  = getDescription().toString()
    desc = desc?.replace(", null","")

    val MAXLEN = 80
    desc?.length?.let {
        if (it > MAXLEN) {
            desc = desc.take(MAXLEN - 3) + "..."
        }
    }

    return keySet().toSortedSet().joinToString(prefix = "{\"meta_description\":\"${desc}\",", postfix = "}") {
        key -> "\"${
                key.lowercase()
                    .substringAfterLast('.')
                    .removePrefix("display_")
                    .removePrefix("metadata_key_")
            }\":\"${
                getString(key)?: 
                decodeRating(getRating(key))?: 
                getText(key)?:
                //imageToBase64(bitmap=getBitmap(key))?: // hide bitmap objects for now
                getNullableLong(getLong(key))?:"" // change null to empty
        }\""
    }
}