package be.digitalia.mediasession2mqtt.mqttmediaplayer

import android.media.MediaMetadata
import android.media.Rating
import android.media.session.PlaybackState
import kotlin.text.replace

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

fun getNullableLong(key: String, value: Long): String? {
    return if (value == 0L) {
        null
    }else{
        if (key.lowercase() == "android.media.metadata.duration"){
            val minutes = value / 60000
            val seconds = (value % 60000) / 1000
            value.toString()+"\",\"duration_minsecs\":\"${String.format("%02d:%02d", minutes, seconds)}"
        }else
            value.toString()
    }
}

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
                || rating.ratingStyle == Rating.RATING_3_STARS)
                return "${rating.starRating.toBigDecimal().stripTrailingZeros().toPlainString()} / ${rating.ratingStyle}"

            if (rating.ratingStyle == Rating.RATING_HEART)
                return if(rating.hasHeart()){ "liked" } else { "unliked" }

            if (rating.ratingStyle == Rating.RATING_THUMB_UP_DOWN)
                return if(rating.isThumbUp()){ "thumb-up" } else { "thumb-down" }

            return if(rating.ratingStyle == Rating.RATING_PERCENTAGE) {
                "${rating.percentRating}%" } else { "unknown" }
        }else return "unrated"
    })
}
/**
 * Extract the current media title, or return an empty String if none is available.
 */
fun MediaMetadata?.toMediaTitle(): String {

    val defaultJSON = "{\"title\":\"\"}"

    if (this == null) {
        return defaultJSON
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

    // tidy up blank metadata or stuck media_id and replace with a blank title.
    if( (containsKey(MediaMetadata.METADATA_KEY_MEDIA_ID)  && keySet().count() == 1)
        || keySet().count() == 0) return defaultJSON

    // return blank title until we have all/most of the data. getDescription appears to be
    // updated as one of the last???
    var desc: String?  = getDescription().toString()
    if(desc.isNullOrEmpty() ||
        desc.replace("[\\s\\p{Punct}]".toRegex(), "").isEmpty())
            return "" //defaultJSON

    //sanity check description then give it a max len. At least one app publishes EPG data
    //which ends up in description.
    if (!desc.isNullOrEmpty()) {
        if (desc.isNotEmpty() && desc.isNotBlank()) {
            desc = desc.replace(", null", "")
            desc = safeString(desc)

            val MAXLEN = 80
            if (desc != null)
                desc.length.let {
                if (it > MAXLEN) desc = desc.take(MAXLEN - 3) + "..."
            }
            desc = (if (desc == "null") "" else "\"meta_description\":\"${desc}\",")
        }
    }

    var title=""
    if(getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrEmpty()){
        if(!getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).isNullOrEmpty()) {
            title = "\"title\":\"${getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)}\","
        }else
            title = desc
    }

    // remove bitmap keys
    val newSet = keySet().filter { key -> getBitmap(key) == null}.toMutableSet()

    return newSet.joinToString(prefix = "{${desc}${title}", postfix = "}") {
        key -> "\"${
                key.lowercase()
                    .substringAfterLast('.')
    //                .substringAfterLast(delimiter ="metadata_")
                    .removePrefix("metadata_key_")
            }\":\"${
                safeString(getString(key))?: 
                decodeRating(getRating(key))?: 
                getText(key)?: 
                getNullableLong(key,getLong(key))?:"" // change null to empty and handle duration
        }\""
    }
}

// escape sequences for safe JSON.. hopefully
fun safeString(value: String?): String? {
    if (value == null)
        return null
    else
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
