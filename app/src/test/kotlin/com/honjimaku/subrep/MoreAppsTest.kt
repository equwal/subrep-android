package com.honjimaku.subrep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list of the "More apps" section. */
class MoreAppsTest {

    @Test
    fun theListLeavesOutThisApp() {
        assertFalse(MORE_APPS.any { "equwal/subrep-android" in it.url })
    }

    @Test
    fun eachLinkOpensAnHttpsPage() {
        for (app in MORE_APPS) assertTrue(app.url, app.url.startsWith("https://"))
    }

    @Test
    fun theListKeepsTheOrderOfTheCatalog() {
        assertEquals(
            listOf("https://subread.space/", "https://booksimulator.com/", "https://honjimaku.com/", "https://sbmsync.com/"),
            MORE_APPS.take(4).map { it.url },
        )
        assertEquals("https://recentlywritten.com/projects.html", MORE_APPS.last().url)
        // The catalog has 15 entries. This app is the one that is not in the list.
        assertEquals(14, MORE_APPS.size)
    }
}
