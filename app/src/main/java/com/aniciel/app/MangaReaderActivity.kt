package com.aniciel.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aniciel.app.ui.theme.AniCielTheme
import kotlinx.coroutines.launch
import java.io.File

/**
 * Lightweight manga / image-chapter reader (item 18).
 * Local files or http(s) page URLs. Kept small — not a full Tachiyomi reader.
 */
class MangaReaderActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_PAGES = "pages"

        fun intent(context: Context, title: String, pages: List<String>): Intent =
            Intent(context, MangaReaderActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PAGES, pages.toTypedArray())
            }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reader"
        val pages = intent.getStringArrayExtra(EXTRA_PAGES)?.toList().orEmpty()
        if (pages.isEmpty()) { finish(); return }

        setContent {
            AniCielTheme {
                Surface(color = Color.Black) {
                    val pager = rememberPagerState(pageCount = { pages.size })
                    val scope = rememberCoroutineScope()
                    Box(Modifier.fillMaxSize()) {
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                            val src = pages[page]
                            val model: Any = if (src.startsWith("http")) src else File(src)
                            AsyncImage(
                                model = model,
                                contentDescription = "Page ${page + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().background(Color.Black)
                            )
                        }
                        Row(
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .background(Color(0x99000000))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                            }
                            Text(
                                "$title  ·  ${pager.currentPage + 1}/${pages.size}",
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (pager.currentPage > 0)
                                        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                                },
                                enabled = pager.currentPage > 0
                            ) { Icon(Icons.Default.SkipPrevious, "Prev", tint = Color.White) }
                            IconButton(
                                onClick = {
                                    if (pager.currentPage < pages.lastIndex)
                                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                                },
                                enabled = pager.currentPage < pages.lastIndex
                            ) { Icon(Icons.Default.SkipNext, "Next", tint = Color.White) }
                        }
                    }
                }
            }
        }
    }
}
