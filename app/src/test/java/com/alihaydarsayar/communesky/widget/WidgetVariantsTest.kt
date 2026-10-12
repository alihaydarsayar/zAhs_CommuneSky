package com.alihaydarsayar.communesky.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Dikey ve yatay çizimlerden hangisinin hangi ölçüyle yerleşeceği. */
class WidgetVariantsTest {

    private fun size(width: Int, height: Int) = DpSize(width.dp, height.dp)

    @Test
    fun `a launcher whose landscape size has the smaller area gets the portrait layout in both drawings`() {
        // 4×2 widget: dikey 341×183, yatay 428×140 (alanı daha küçük). Hiçbir çizim "sığmazsa" ana ekran yatayı seçer.
        val bounds = WidgetBounds(portrait = size(341, 183), landscape = size(428, 140))
        assertEquals(size(341, 183), WidgetVariants.layoutSize(size(341, 183), bounds, devicePortrait = true))
        assertEquals(size(341, 183), WidgetVariants.layoutSize(size(428, 140), bounds, devicePortrait = true))
        // Ölçü listesi bir dp farklı bildirilse de aynı çizim tanınır.
        assertEquals(size(341, 183), WidgetVariants.layoutSize(size(429, 141), bounds, devicePortrait = true))
        // Telefon yatayken her çizim kendi ölçüsüyle kalır.
        assertEquals(size(428, 140), WidgetVariants.layoutSize(size(428, 140), bounds, devicePortrait = false))
        assertEquals(size(341, 183), WidgetVariants.layoutSize(size(341, 183), bounds, devicePortrait = false))
    }

    @Test
    fun `a launcher whose portrait size has the smaller area is left alone`() {
        // Pixel: dikey 374×210, yatay 850×109. Sığmayan durumda ana ekran zaten dikeyi seçer.
        val bounds = WidgetBounds(portrait = size(374, 210), landscape = size(850, 109))
        assertEquals(size(850, 109), WidgetVariants.layoutSize(size(850, 109), bounds, devicePortrait = true))
        assertEquals(size(374, 210), WidgetVariants.layoutSize(size(374, 210), bounds, devicePortrait = true))
    }

    @Test
    fun `on phones the home screen counts as portrait even while the phone is held sideways`() {
        // Telefon (en kısa kenar 600 dp'den küçük): yöne bakılmaz.
        assertEquals(true, WidgetVariants.treatAsPortrait(smallestScreenWidthDp = 411, landscapeNow = false))
        assertEquals(true, WidgetVariants.treatAsPortrait(smallestScreenWidthDp = 411, landscapeNow = true))
        assertEquals(true, WidgetVariants.treatAsPortrait(smallestScreenWidthDp = 599, landscapeNow = true))
        // Tablet: çizim anındaki yön geçerlidir.
        assertEquals(true, WidgetVariants.treatAsPortrait(smallestScreenWidthDp = 600, landscapeNow = false))
        assertEquals(false, WidgetVariants.treatAsPortrait(smallestScreenWidthDp = 600, landscapeNow = true))
        assertEquals(false, WidgetVariants.treatAsPortrait(smallestScreenWidthDp = 800, landscapeNow = true))

        // Güncelleme video izlerken (telefon yatay) gelirse de yatay çizim dikey ölçüyle yerleşir.
        val bounds = WidgetBounds(portrait = size(341, 183), landscape = size(428, 140))
        val phoneSideways = WidgetVariants.treatAsPortrait(411, landscapeNow = true)
        assertEquals(size(341, 183), WidgetVariants.layoutSize(size(428, 140), bounds, phoneSideways))
        // Tablet yatayken yatay çizim kendi ölçüsüyle kalır.
        val tabletSideways = WidgetVariants.treatAsPortrait(800, landscapeNow = true)
        assertEquals(size(428, 140), WidgetVariants.layoutSize(size(428, 140), bounds, tabletSideways))
    }

    @Test
    fun `missing or identical sizes change nothing`() {
        assertEquals(size(300, 150), WidgetVariants.layoutSize(size(300, 150), WidgetBounds(null, null), devicePortrait = true))
        assertEquals(size(300, 150), WidgetVariants.layoutSize(size(300, 150), WidgetBounds(size(250, 110), null), devicePortrait = true))
        val same = WidgetBounds(portrait = size(250, 110), landscape = size(251, 110))
        assertEquals(size(251, 110), WidgetVariants.layoutSize(size(251, 110), same, devicePortrait = true))
    }
}
