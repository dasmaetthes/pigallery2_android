package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ApiCameraData
import com.example.data.ApiFace
import com.example.data.ApiFaceBox
import com.example.data.ApiGPSData
import com.example.data.ApiMedia
import com.example.data.ApiMediaMetadata
import com.example.data.ApiSize
import com.example.data.GalleryRepository
import com.example.data.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomAndLocalizationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: GalleryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GalleryRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `room cache persists and restores complete metadata fields`() = runBlocking {
        val originalMedia = ApiMedia(
            id = 42,
            name = "IMG_2025.jpg",
            parentPath = "Trips/Alps",
            metadata = ApiMediaMetadata(
                size = ApiSize(width = 4032, height = 3024),
                creationDate = 1715000000000L,
                creationDateOffset = "+02:00",
                cameraData = ApiCameraData(
                    ISO = 100,
                    make = "Canon",
                    model = "EOS R5",
                    fStop = 2.8,
                    exposure = 0.004,
                    focalLength = 50.0,
                    lens = "RF 50mm F1.2L"
                ),
                keywords = listOf("mountains", "hiking", "sunset"),
                faces = listOf(
                    ApiFace(
                        name = "Alice",
                        box = ApiFaceBox(width = 220, height = 240, left = 500, top = 300)
                    ),
                    ApiFace(
                        name = null,
                        box = ApiFaceBox(width = 180, height = 190, left = 900, top = 320)
                    )
                ),
                duration = 12.5,
                gps = ApiGPSData(latitude = 47.376887, longitude = 8.541694, altitude = 408.0),
                fileSize = 5_432_100L,
                isLivePhoto = true,
                liveVideoPath = "Trips/Alps/IMG_2025.mov"
            )
        )

        repository.saveDirectory("Trips/Alps", listOf(originalMedia))

        val loadedList = repository.getDirectory("Trips/Alps")
        assertNotNull(loadedList)
        assertEquals(1, loadedList!!.size)

        val restored = loadedList[0]
        assertEquals(42, restored.id)
        assertEquals("IMG_2025.jpg", restored.name)
        assertEquals("Trips/Alps", restored.parentPath)

        val meta = restored.metadata
        assertNotNull(meta)
        assertEquals(4032, meta!!.size?.width)
        assertEquals(3024, meta.size?.height)
        assertEquals(1715000000000L, meta.creationDate)
        assertEquals("+02:00", meta.creationDateOffset)
        assertEquals(5_432_100L, meta.fileSize)
        assertEquals(12.5, meta.duration!!, 0.0001)
        assertEquals(true, meta.isLivePhoto)
        assertEquals("Trips/Alps/IMG_2025.mov", meta.liveVideoPath)

        assertEquals(47.376887, meta.gps!!.latitude, 0.000001)
        assertEquals(8.541694, meta.gps!!.longitude, 0.000001)
        assertEquals(408.0, meta.gps!!.altitude!!, 0.01)

        val cam = meta.cameraData!!
        assertEquals(100, cam.ISO)
        assertEquals("Canon", cam.make)
        assertEquals("EOS R5", cam.model)
        assertEquals(2.8, cam.fStop!!, 0.001)
        assertEquals(0.004, cam.exposure!!, 0.0001)
        assertEquals(50.0, cam.focalLength!!, 0.01)
        assertEquals("RF 50mm F1.2L", cam.lens)

        assertEquals(listOf("mountains", "hiking", "sunset"), meta.keywords)
        assertEquals(2, meta.faces?.size)
        assertEquals("Alice", meta.faces!![0].name)
        assertEquals(ApiFaceBox(220, 240, 500, 300), meta.faces!![0].box)
        assertNull(meta.faces!![1].name)
        assertEquals(ApiFaceBox(180, 190, 900, 320), meta.faces!![1].box)
    }

    @Test
    fun `room cache saveAllMedia and clearAllCache work properly`() = runBlocking {
        val media = ApiMedia(
            id = 99,
            name = "city.jpg",
            parentPath = "City",
            metadata = ApiMediaMetadata(
                gps = ApiGPSData(latitude = 52.52, longitude = 13.405),
                keywords = listOf("berlin")
            )
        )
        repository.saveAllMedia(listOf(media))

        val all = repository.getAllMedia()
        assertNotNull(all)
        assertEquals(1, all!!.size)
        assertEquals(52.52, all[0].metadata?.gps?.latitude!!, 0.001)
        assertEquals(listOf("berlin"), all[0].metadata?.keywords)

        repository.clearAllCache()
        assertNull(repository.getAllMedia())
    }

    @Test
    fun `localization provides consistent English and German resources`() {
        val enConfig = Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val enContext = context.createConfigurationContext(enConfig)

        val deConfig = Configuration(context.resources.configuration).apply {
            setLocale(Locale.GERMAN)
        }
        val deContext = context.createConfigurationContext(deConfig)

        assertEquals("Start Slideshow", enContext.getString(R.string.slideshow_start))
        assertEquals("Diashow starten", deContext.getString(R.string.slideshow_start))

        assertEquals("Delete Album", enContext.getString(R.string.delete_album))
        assertEquals("Album löschen", deContext.getString(R.string.delete_album))

        assertEquals("Settings", enContext.getString(R.string.settings))
        assertEquals("Einstellungen", deContext.getString(R.string.settings))

        assertEquals("Map View", enContext.getString(R.string.map_view))
        assertEquals("Kartenansicht", deContext.getString(R.string.map_view))

        assertEquals("Loading map…", enContext.getString(R.string.loading_map))
        assertEquals("Karte wird geladen…", deContext.getString(R.string.loading_map))

        assertEquals(
            "Failed to load map. Check your internet connection.",
            enContext.getString(R.string.map_load_error)
        )
        assertEquals(
            "Karte konnte nicht geladen werden. Bitte Internetverbindung prüfen.",
            deContext.getString(R.string.map_load_error)
        )

        assertEquals("GPS Coordinates", enContext.getString(R.string.meta_gps_coordinates))
        assertEquals("GPS-Koordinaten", deContext.getString(R.string.meta_gps_coordinates))
    }
}
