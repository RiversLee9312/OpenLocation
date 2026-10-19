package dev.openlocation.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class LauncherIconTest {
    @Test
    fun adaptiveIconProvidesTransparentMonochromeInsideSafeRegion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon =
            context.packageManager.getApplicationIcon(context.packageName) as AdaptiveIconDrawable
        val mono = icon.monochrome
        assertNotNull("Themed launcher icon needs a monochrome layer", mono)
        val edge = (108 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        mono!!.setBounds(0, 0, edge, edge)
        mono.draw(Canvas(bitmap))
        var count = 0
        for (y in 0 until edge) for (x in 0 until edge) if (
            Color.alpha(bitmap.getPixel(x, y)) > 16
        ) {
            count++
            assertTrue(
                "Monochrome mark exceeds adaptive safe region",
                x >= edge * 21 / 108 &&
                    x < edge * 87 / 108 &&
                    y >= edge * 21 / 108 &&
                    y < edge * 87 / 108,
            )
        }
        assertTrue("Monochrome layer is empty", count > 100)
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        bitmap.recycle()
    }
}
