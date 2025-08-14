package be.digitalia.mediasession2mqtt.mqttmediaplayer

import android.media.MediaMetadata
import android.media.session.PlaybackState

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
    // duration: ""
    // user_rating: ""
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
    // duration: ""
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

    return keySet().joinToString(prefix = "{", postfix = "}") {
        key -> "\"${
                key.lowercase()
                .removePrefix("android.media.metadata.display_")
                .removePrefix("android.media.metadata.")
                //.removePrefix("com.audible.application.mediacommon.")
        }\":\"${
                getString(key)?: 
                getRating(key)?: 
                getText(key)?:
                getBitmap(key)?:
                getLong(key)?:"" // change null to empty
        }\""
    }
}