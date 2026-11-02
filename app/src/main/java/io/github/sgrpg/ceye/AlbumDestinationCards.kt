package io.github.sgrpg.ceye

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.floor

internal data class AlbumDestination(val album: Album, val coverUri: String?)

/** Reuses the first visible image in the app's current photo ordering. */
@Composable
internal fun rememberAlbumDestinations(albums: List<Album>, photos: List<Photo>): List<AlbumDestination> =
    remember(albums, photos) {
        val covers = mutableMapOf<String, String>()
        photos.asSequence().filter { it.status !in setOf("trashed", "unavailable") }.forEach {
            covers.putIfAbsent(Album(it.volume, it.path).key, it.uri)
        }
        albums.map { AlbumDestination(it, covers[it.key]) }
    }

/** A single complete row. Overflow albums remain accessible through All albums. */
@Composable
internal fun AlbumDestinationStrip(
    destinations: List<AlbumDestination>,
    enabled: Boolean,
    onSelect: (Album) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val slots = floor((maxWidth.value + 12f) / (92f + 12f)).toInt().coerceAtLeast(1)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(slots) { index ->
                val destination = destinations.getOrNull(index)
                if (destination == null) Spacer(Modifier.weight(1f))
                else key(destination.album.key) {
                    AlbumDestinationCard(destination, enabled, { onSelect(destination.album) }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Same cover cards as the strip, in a vertically scrolling expanded grid. */
@Composable
internal fun AlbumDestinationGrid(
    destinations: List<AlbumDestination>,
    enabled: Boolean,
    onSelect: (Album) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(92.dp),
        modifier = modifier.fillMaxWidth().heightIn(max = 480.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(destinations, key = { it.album.key }) { destination ->
            AlbumDestinationCard(destination, enabled, { onSelect(destination.album) })
        }
    }
}

@Composable
private fun AlbumDestinationCard(
    destination: AlbumDestination,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(20))
            .background(Color(0xfff0f2f6))
            .clickable(enabled = enabled, onClickLabel = "归入此相册", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "选择相册 ${destination.album.name}" }
    ) {
        if (destination.coverUri != null) {
            AsyncImage(destination.coverUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(CeYeIcons.Folder, null, Modifier.align(Alignment.Center).size(28.dp), tint = Color(0xff69717e))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Transparent,
            0.4f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.78f)
        )))
        Text(
            destination.album.name,
            Modifier.align(Alignment.BottomStart).padding(9.dp),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
