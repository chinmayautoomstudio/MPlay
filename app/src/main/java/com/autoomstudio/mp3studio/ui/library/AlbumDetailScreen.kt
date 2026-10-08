package com.autoomstudio.mp3studio.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.model.Album
import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.ui.components.ArtworkImage

@Composable
fun AlbumDetailScreen(
    album: Album,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlay: (songs: List<Song>, start: Song) -> Unit,
    onShuffle: (songs: List<Song>) -> Unit,
    actions: SongActions,
    selection: SongSelection,
    modifier: Modifier = Modifier,
) {
    val songs = album.songs

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 8.dp)) {
        item(key = "back") { DetailBackButton(onBack = onBack) }
        item(key = "header") {
            DetailHeader(
                title = album.title,
                subtitle = stringResource(R.string.count_separator, album.artist, songCountText(songs.size)),
                onPlay = { onPlay(songs, songs.first()) },
                onShuffle = { onShuffle(songs) },
            ) {
                ArtworkImage(
                    uri = album.artUri,
                    contentDescription = stringResource(R.string.album_art_description, album.title),
                    cornerRadius = 20.dp,
                    prominent = true,
                    requestSizePx = 600,
                    modifier = Modifier.size(200.dp),
                )
            }
        }
        songItems(
            songs = songs,
            currentSongId = currentSongId,
            isPlaying = isPlaying,
            onSongClick = { onPlay(songs, it) },
            actions = actions,
            showTrackNumbers = true,
            selection = selection,
        )
    }
}
