package com.alihaydarsayar.communesky.widget

import androidx.annotation.LayoutRes
import com.alihaydarsayar.communesky.R

/** Yazının gölgesi: açık yazıda koyu, koyu yazıda açık. */
enum class WShadow { None, Dark, Light }

/**
 * Widget yazıları Android'in kendi TextView ve TextClock bileşenleriyle çizilir; yazı tipi ve gölge
 * uzaktan değiştirilemediği için her birleşimin kendi küçük düzeni var (res/layout/wt_*, wc_*).
 * Anahtar: s = sistem (3 ince, 4 normal, 5 orta, 7 kalın), dg = dar kalın rakam, ol = dar ince rakam.
 */
object WidgetLayouts {

    @LayoutRes
    fun text(font: String, shadow: WShadow): Int = when (font) {
        "s3" -> when (shadow) {
            WShadow.None -> R.layout.wt_s3
            WShadow.Dark -> R.layout.wt_s3_sd
            WShadow.Light -> R.layout.wt_s3_sl
        }
        "s4" -> when (shadow) {
            WShadow.None -> R.layout.wt_s4
            WShadow.Dark -> R.layout.wt_s4_sd
            WShadow.Light -> R.layout.wt_s4_sl
        }
        "s5" -> when (shadow) {
            WShadow.None -> R.layout.wt_s5
            WShadow.Dark -> R.layout.wt_s5_sd
            WShadow.Light -> R.layout.wt_s5_sl
        }
        "s7" -> when (shadow) {
            WShadow.None -> R.layout.wt_s7
            WShadow.Dark -> R.layout.wt_s7_sd
            WShadow.Light -> R.layout.wt_s7_sl
        }
        "dg" -> when (shadow) {
            WShadow.None -> R.layout.wt_dg
            WShadow.Dark -> R.layout.wt_dg_sd
            WShadow.Light -> R.layout.wt_dg_sl
        }
        "ol" -> when (shadow) {
            WShadow.None -> R.layout.wt_ol
            WShadow.Dark -> R.layout.wt_ol_sd
            WShadow.Light -> R.layout.wt_ol_sl
        }
        else -> R.layout.wt_s4
    }

    @LayoutRes
    fun clock(font: String, shadow: WShadow, caps: Boolean): Int = if (caps) {
        when (font) {
            "s3" -> when (shadow) {
                WShadow.None -> R.layout.wc_s3_caps
                WShadow.Dark -> R.layout.wc_s3_sd_caps
                WShadow.Light -> R.layout.wc_s3_sl_caps
            }
            "s4" -> when (shadow) {
                WShadow.None -> R.layout.wc_s4_caps
                WShadow.Dark -> R.layout.wc_s4_sd_caps
                WShadow.Light -> R.layout.wc_s4_sl_caps
            }
            "s5" -> when (shadow) {
                WShadow.None -> R.layout.wc_s5_caps
                WShadow.Dark -> R.layout.wc_s5_sd_caps
                WShadow.Light -> R.layout.wc_s5_sl_caps
            }
            "s7" -> when (shadow) {
                WShadow.None -> R.layout.wc_s7_caps
                WShadow.Dark -> R.layout.wc_s7_sd_caps
                WShadow.Light -> R.layout.wc_s7_sl_caps
            }
            "dg" -> when (shadow) {
                WShadow.None -> R.layout.wc_dg_caps
                WShadow.Dark -> R.layout.wc_dg_sd_caps
                WShadow.Light -> R.layout.wc_dg_sl_caps
            }
            "ol" -> when (shadow) {
                WShadow.None -> R.layout.wc_ol_caps
                WShadow.Dark -> R.layout.wc_ol_sd_caps
                WShadow.Light -> R.layout.wc_ol_sl_caps
            }
            else -> R.layout.wc_s4_caps
        }
    } else {
        when (font) {
            "s3" -> when (shadow) {
                WShadow.None -> R.layout.wc_s3
                WShadow.Dark -> R.layout.wc_s3_sd
                WShadow.Light -> R.layout.wc_s3_sl
            }
            "s4" -> when (shadow) {
                WShadow.None -> R.layout.wc_s4
                WShadow.Dark -> R.layout.wc_s4_sd
                WShadow.Light -> R.layout.wc_s4_sl
            }
            "s5" -> when (shadow) {
                WShadow.None -> R.layout.wc_s5
                WShadow.Dark -> R.layout.wc_s5_sd
                WShadow.Light -> R.layout.wc_s5_sl
            }
            "s7" -> when (shadow) {
                WShadow.None -> R.layout.wc_s7
                WShadow.Dark -> R.layout.wc_s7_sd
                WShadow.Light -> R.layout.wc_s7_sl
            }
            "dg" -> when (shadow) {
                WShadow.None -> R.layout.wc_dg
                WShadow.Dark -> R.layout.wc_dg_sd
                WShadow.Light -> R.layout.wc_dg_sl
            }
            "ol" -> when (shadow) {
                WShadow.None -> R.layout.wc_ol
                WShadow.Dark -> R.layout.wc_ol_sd
                WShadow.Light -> R.layout.wc_ol_sl
            }
            else -> R.layout.wc_s4
        }
    }
}
