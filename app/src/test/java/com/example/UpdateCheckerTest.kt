package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.ui.GalleryViewModel
import com.example.util.VersionComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateCheckerTest {

    private lateinit var viewModel: GalleryViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = PreferencesManager(context)
        prefs.clear()
        prefs.autoCheckUpdates = false
        viewModel = GalleryViewModel(context)
    }

    @Test
    fun `VersionComparator does not offer older release on newer dev build`() {
        assertFalse(VersionComparator.isRemoteNewer("2.6", "2.7"))
        assertFalse(VersionComparator.isRemoteNewer("v2.6", "2.7"))
        assertFalse(VersionComparator.isRemoteNewer("V2.6.9", "2.7"))
        assertFalse(VersionComparator.isRemoteNewer("2.6", "2.7-dev"))
        assertFalse(VersionComparator.isRemoteNewer("v2.6.0", "2.7.0-beta.1"))
    }

    @Test
    fun `VersionComparator treats equivalent versions as up to date`() {
        assertFalse(VersionComparator.isRemoteNewer("2.7", "2.7"))
        assertFalse(VersionComparator.isRemoteNewer("v2.7", "2.7"))
        assertFalse(VersionComparator.isRemoteNewer("v2.7.0", "2.7"))
        assertFalse(VersionComparator.isRemoteNewer("2.7", "2.7.0"))
        assertFalse(VersionComparator.isRemoteNewer("2.7-beta.1", "2.7-beta.1"))
    }

    @Test
    fun `VersionComparator detects genuinely newer versions including multi-digit and pre-release promotion`() {
        assertTrue(VersionComparator.isRemoteNewer("v2.7.1", "2.7"))
        assertTrue(VersionComparator.isRemoteNewer("2.8", "2.7"))
        assertTrue(VersionComparator.isRemoteNewer("v2.10", "2.9"))
        assertTrue(VersionComparator.isRemoteNewer("3.0", "2.7"))
        assertTrue(VersionComparator.isRemoteNewer("v2.7", "2.7-beta.1"))
        assertTrue(VersionComparator.isRemoteNewer("2.7-beta.2", "2.7-beta.1"))
    }

    @Test
    fun `VersionComparator ignores blank or unparseable tags`() {
        assertFalse(VersionComparator.isRemoteNewer("", "2.7"))
        assertFalse(VersionComparator.isRemoteNewer(null, "2.7"))
        assertFalse(VersionComparator.isRemoteNewer("latest", "2.7"))
        assertFalse(VersionComparator.isRemoteNewer("2.8", ""))
    }

    @Test
    fun `evaluateReleaseJson returns UpToDate when latest GitHub release is 2_6 and local build is 2_7`() {
        val json = """
            {
              "tag_name": "v2.6",
              "html_url": "https://github.com/dasmaetthes/pigallery2_android/releases/tag/v2.6",
              "body": "Release 2.6 notes",
              "draft": false,
              "prerelease": false
            }
        """.trimIndent()

        val state = viewModel.evaluateReleaseJson(json, "2.7")
        assertTrue(state is GalleryViewModel.UpdateState.UpToDate)
        assertEquals("2.7", (state as GalleryViewModel.UpdateState.UpToDate).currentVersion)
    }

    @Test
    fun `evaluateReleaseJson returns NewRelease when latest GitHub release is 2_8 and local build is 2_7`() {
        val json = """
            {
              "tag_name": "v2.8",
              "html_url": "https://github.com/dasmaetthes/pigallery2_android/releases/tag/v2.8",
              "body": "Release 2.8 notes",
              "draft": false,
              "prerelease": false
            }
        """.trimIndent()

        val state = viewModel.evaluateReleaseJson(json, "2.7")
        assertTrue(state is GalleryViewModel.UpdateState.NewRelease)
        val release = state as GalleryViewModel.UpdateState.NewRelease
        assertEquals("v2.8", release.tagName)
        assertEquals("Release 2.8 notes", release.releaseNotes)
    }

    @Test
    fun `evaluateReleaseJson ignores draft and prerelease responses`() {
        val draftJson = """{"tag_name": "v2.8", "draft": true, "prerelease": false}"""
        val prereleaseJson = """{"tag_name": "v2.8", "draft": false, "prerelease": true}"""

        assertTrue(viewModel.evaluateReleaseJson(draftJson, "2.7") is GalleryViewModel.UpdateState.UpToDate)
        assertTrue(viewModel.evaluateReleaseJson(prereleaseJson, "2.7") is GalleryViewModel.UpdateState.UpToDate)
    }
}
