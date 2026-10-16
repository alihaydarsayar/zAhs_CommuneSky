package com.alihaydarsayar.communesky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alihaydarsayar.communesky.ui.common.GlassSegmented
import com.alihaydarsayar.communesky.ui.common.touchTarget
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Küçük görünen öğelerin dokunma kutusu en az 44 dp olur; görünen şekil küçük kalır. */
@RunWith(AndroidJUnit4::class)
class TouchTargetTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun smallItemGetsA44dpBoxButStaysSmall() {
        var clicks = 0
        rule.setContent {
            Box(Modifier.testTag("box").clickable { clicks++ }.touchTarget()) {
                Box(Modifier.testTag("visual").size(30.dp).background(Color.Red))
            }
        }
        rule.onNodeWithTag("box").assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
        rule.onNodeWithTag("visual", useUnmergedTree = true).assertWidthIsEqualTo(30.dp)
        rule.onNodeWithTag("box").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun segmentedOptionsAreAtLeast44dpTall() {
        rule.setContent { GlassSegmented(listOf("°C", "°F"), 0, {}, Modifier.testTag("segmented")) }
        // Çerçeve 44 dp'lik seçenek kutusu + 2×2 dp boşluk = 48 dp (eskisiyle aynı).
        rule.onNodeWithTag("segmented").assertHeightIsAtLeast(48.dp)
    }
}
