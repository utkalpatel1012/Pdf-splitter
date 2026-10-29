package com.example

import com.example.model.ChapterItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ExampleUnitTest {
    @Test
    fun chapterItem_pageCountCalculation() {
        val chapter = ChapterItem(
            id = UUID.randomUUID().toString(),
            index = 1,
            title = "Introduction to Quantum Physics",
            startPage = 5,
            endPage = 14
        )
        // 14 - 5 + 1 = 10 pages
        assertEquals(10, chapter.pageCount)
    }

    @Test
    fun chapterItem_singlePageChapter() {
        val chapter = ChapterItem(
            id = UUID.randomUUID().toString(),
            index = 2,
            title = "Summary and Conclusions",
            startPage = 20,
            endPage = 20
        )
        assertEquals(1, chapter.pageCount)
    }

    @Test
    fun chapterItem_invalidRangeZeroPages() {
        val chapter = ChapterItem(
            id = UUID.randomUUID().toString(),
            index = 3,
            title = "Invalid Range",
            startPage = 25,
            endPage = 20
        )
        assertEquals(0, chapter.pageCount)
    }
}
