package org.umn.application.studenthub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class StudentHubUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun todayOpensSchedule() {
        compose.onNodeWithText("Selamat pagi, Nadia.").assertIsDisplayed()
        compose.onNodeWithText("KELAS BERIKUTNYA").assertIsDisplayed()
        compose.onNodeWithText("09.00–10.40").assertIsDisplayed()
        compose.onNodeWithText("Lihat jadwal").performScrollTo().performClick()
        compose.onNodeWithText("Minggu yang terarah.").assertIsDisplayed()
    }

    @Test fun addedAgendaIsSavedForSelectedDay() {
        compose.onNodeWithTag("nav-schedule").performClick()
        compose.onNodeWithText("Tambah agenda").performScrollTo().performClick()
        compose.onNodeWithText("Judul agenda").performTextInput("Diskusi proyek")
        compose.onNodeWithText("Waktu (HH.mm)").performTextInput("18.30")
        compose.onNodeWithText("Simpan").performClick()
        compose.onNodeWithText("Diskusi proyek").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Diskusi proyek").performScrollTo().assertIsDisplayed()
    }

    @Test fun academicOpensCourseAndReturns() {
        compose.onNodeWithTag("nav-academic").performClick()
        compose.onNodeWithTag("course-IF570").performScrollTo().performClick()
        compose.onNodeWithText("Dosen: Budi Santoso, M.Kom.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Semester 5").assertIsDisplayed()
    }

    @Test fun secondaryDestinationsAndEditorAreReachable() {
        val screens = listOf("Catatan" to "Ide rapi, belajar ringan.", "Keuangan" to "Saldo transaksi lokal", "Nilai" to "Setiap kemajuan berarti.", "Belajar" to "Fokus kecil, hasil besar.")
        screens.forEach { (label, heading) ->
            compose.onNodeWithTag("nav-more").performClick()
            compose.onNodeWithText(label).performClick()
            compose.onNodeWithText(heading).assertIsDisplayed()
        }
        compose.onNodeWithTag("nav-more").performClick()
        compose.onNodeWithText("Catatan").performClick()
        compose.onNodeWithText("Navigasi & Activity lifecycle").performScrollTo().performClick()
        compose.onNodeWithTag("note-title").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Ide rapi, belajar ringan.").assertIsDisplayed()
    }

    @Test fun renderEveryDesignScreen() {
        fun capture(name: String) {
            compose.waitForIdle()
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                val file = java.io.File("build/outputs/screenshots/$name.png")
                requireNotNull(file.parentFile).mkdirs()
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        capture("01-hari-ini")
        compose.onNodeWithTag("nav-schedule").performClick()
        capture("02-jadwal")
        compose.onNodeWithTag("nav-academic").performClick()
        capture("03-akademik")
        compose.onNodeWithTag("course-IF570").performScrollTo().performClick()
        capture("04-detail-mata-kuliah")
        compose.onNodeWithTag("nav-more").performClick()
        compose.onNodeWithText("Catatan").performClick()
        capture("05-catatan")
        compose.onNodeWithText("Navigasi & Activity lifecycle").performScrollTo().performClick()
        capture("06-editor-catatan")
        listOf("Keuangan" to "07-keuangan", "Nilai" to "08-pelacak-akademik", "Belajar" to "09-rekomendasi-belajar").forEach { (label, name) ->
            compose.onNodeWithTag("nav-more").performClick()
            compose.onNodeWithText(label).performClick()
            capture(name)
        }
    }
}
