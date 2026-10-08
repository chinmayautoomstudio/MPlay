package com.autoomstudio.mp3studio.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.model.Album
import com.autoomstudio.mp3studio.data.model.Artist
import com.autoomstudio.mp3studio.data.model.Song

@Composable
fun ArtistDetailScreen(
    artist: Artist,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlay: (songs: List<Song>, start: Song) -> Unit,
    onShuffle: (songs: List<Song>) -> Unit,
    actions: SongActions,
    selection: SongSelection,
    modifier: Modifier = Modifier,
) {
    val songs = artist.songs

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 8.dp)) {
        item(key = "back") { DetailBackButton(onBack = onBack) }
        item(key = "header") {
            DetailHeader(
                title = artist.name,
                subtitle = songAndAlbumCountText(songs.size, artist.albums.size),
                onPlay = { onPlay(songs, songs.first()) },
                onShuffle = { onShuffle(songs) },
            ) {
                ArtistInitial(name = artist.name, size = 160.dp)
            }
        }
        if (artist.albums.isNotEmpty()) {
            item(key = "albums_title") { SectionTitle(stringResource(R.string.tab_albums)) }
            item(key = "albums") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = artist.albums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onAlbumClick(album) },
                            modifier = Modifier.width(150.dp),
                        )
                    }
                }
            }
            item(key = "songs_title") { SectionTitle(stringResource(R.string.tab_songs)) }
        }
        songItems(
            songs = songs,
            currentSongId = currentSongId,
            isPlaying = isPlaying,
            onSongClick = { onPlay(songs, it) },
            actions = actions,
            selection = selection,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}
