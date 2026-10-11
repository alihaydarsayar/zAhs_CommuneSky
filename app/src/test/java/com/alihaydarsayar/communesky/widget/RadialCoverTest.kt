package com.alihaydarsayar.communesky.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Işınsal saatin parçalı şablonu: üst üste çizilen boyalı maskeler tek resimle aynı sonucu verir. */
class RadialCoverTest {

    /** Katmanları alttan üste bindirir: her durak renginin katkısı ve geriye kalan saydamlık. */
    private fun composite(alphas: IntArray): Pair<FloatArray, Float> {
        val share = FloatArray(alphas.size)
        var through = 1f
        for (i in alphas.indices.reversed()) {
            share[i] = through * alphas[i] / 255f
            through *= 1f - alphas[i] / 255f
        }
        return share to through
    }

    @Test
    fun `stacked layers give each stop its share and keep the hole coverage`() {
        val alphas = IntArray(3)
        for (coverage in listOf(0, 1, 37, 128, 200, 254, 255)) for (step in 0..20) {
            val weights = RadialCover.weights(step / 20f, 3)
            RadialCover.layerAlphas(coverage, weights, alphas)
            val (share, through) = composite(alphas)
            val k = coverage / 255f
            // Toplam örtme deliğin örtme oranıyla, her rengin katkısı payıyla aynı (8 bitlik yuvarlama payıyla).
            assertEquals(1f - k, through, 1.5f / 255f)
            for (i in weights.indices) assertTrue("k=$coverage t=${step / 20f} renk $i", abs(share[i] - k * weights[i]) <= 1.5f / 255f)
        }
    }

    @Test
    fun `a fully covered pixel is opaque and a hole stays empty`() {
        val alphas = IntArray(3)
        RadialCover.layerAlphas(255, RadialCover.weights(0.3f, 3), alphas)
        assertEquals(0f, composite(alphas).second, 0f)
        RadialCover.layerAlphas(0, RadialCover.weights(0.3f, 3), alphas)
        assertEquals(listOf(0, 0, 0), alphas.toList())
    }

    @Test
    fun `the split cover is far smaller than the single image`() {
        // 340×150 dp, yoğunluk 2,625: tek resim 892×394×4 bayt.
        val single = 892L * 394 * 4
        val split = RadialCover.estimateBytes(892, 394, 63, stops = 3)
        assertTrue(split * 2 < single)
    }
}
