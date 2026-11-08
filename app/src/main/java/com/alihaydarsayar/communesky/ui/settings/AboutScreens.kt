package com.alihaydarsayar.communesky.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.model.about.DataSources
import com.alihaydarsayar.communesky.model.about.OpenSourceItem
import com.alihaydarsayar.communesky.model.about.OpenSourceLicenses
import com.alihaydarsayar.communesky.model.about.PolicyBlock
import com.alihaydarsayar.communesky.model.about.PolicyText
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.common.ScreenTopBar
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import com.alihaydarsayar.communesky.ui.today.GlassPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kaynak kodu ve web sayfaları; yalnız kullanıcı dokununca tarayıcıda açılır, uygulama kendisi ağa çıkmaz. */
object AboutLinks {
    const val SOURCE = "https://github.com/alihaydarsayar/zAhs_CommuneSky"
    const val LICENSE = "https://www.gnu.org/licenses/gpl-3.0.html"
    const val PRIVACY = "https://alihaydarsayar.com/communesky/privacy/"

    /** Bağlantıyı tarayıcıda açar; açacak uygulama yoksa sessizce geçer (çökmez). */
    fun open(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun version(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    }.getOrDefault("?")
}

/** Ayarlar'daki "Hakkında" kartı: sürüm, uygulamanın lisansı ve kaynak kodu, lisanslar, veri kaynakları, gizlilik. */
@Composable
fun AboutCard(onOpenLicenses: () -> Unit, onOpenDataSources: () -> Unit, onOpenPrivacy: () -> Unit) {
    val context = LocalContext.current
    val version = remember { AboutLinks.version(context) }
    GlassCard(Modifier.fillMaxWidth(), title = stringResource(R.string.settings_about)) {
        Text(stringResource(R.string.about_version, version), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.about_license), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        LinkRow(stringResource(R.string.about_source), AboutLinks.SOURCE.removePrefix("https://")) { AboutLinks.open(context, AboutLinks.SOURCE) }
        LinkRow(stringResource(R.string.about_open_source), stringResource(R.string.about_open_source_hint), onClick = onOpenLicenses)
        LinkRow(stringResource(R.string.about_data_sources), stringResource(R.string.about_data_sources_hint), onClick = onOpenDataSources)
        LinkRow(stringResource(R.string.about_privacy), stringResource(R.string.about_privacy_hint), onClick = onOpenPrivacy)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun AboutPage(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title, onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** Açık kaynak lisansları: derleme sırasında üretilen listeden (res/raw/aboutlibraries.json); ağ yok. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val items by produceState<List<OpenSourceItem>>(emptyList(), resources) {
        value = withContext(Dispatchers.IO) {
            runCatching { resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() } }
                .map(OpenSourceLicenses::parse).getOrDefault(emptyList())
        }
    }
    val (assets, libraries) = items.partition { it.isAsset }
    AboutPage(stringResource(R.string.about_open_source), onBack) {
        item { IntroHint(stringResource(R.string.licenses_intro)) }
        if (assets.isNotEmpty()) item { SectionTitle(stringResource(R.string.licenses_assets)) }
        items(assets, key = { it.id }) { LicenseItem(it) }
        if (libraries.isNotEmpty()) item { SectionTitle(stringResource(R.string.licenses_libraries, libraries.size)) }
        items(libraries, key = { it.id }) { LicenseItem(it) }
    }
}

@Composable
private fun LicenseItem(item: OpenSourceItem) {
    val context = LocalContext.current
    var open by rememberSaveable(item.id) { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }) {
        Text(item.name, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
        val meta = listOfNotNull(item.version?.takeIf { !item.isAsset }, item.licenses.joinToString(", ") { it.name }.takeIf { it.isNotEmpty() })
        Text(meta.joinToString(" · "), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        if (item.isAsset) item.version?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
        item.description?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        if (open) {
            if (item.authors.isNotEmpty()) Hint(item.authors.joinToString(", "))
            item.website?.let { url -> Spacer(Modifier.height(8.dp)); GlassPill(stringResource(R.string.about_open_web)) { AboutLinks.open(context, url) } }
            for (license in item.licenses) {
                Text(license.name, color = TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                license.content?.let { Text(it.replace("<br />", ""), color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                license.url?.let { url -> Spacer(Modifier.height(6.dp)); GlassPill(stringResource(R.string.about_open_license)) { AboutLinks.open(context, url) } }
            }
        } else {
            Text(stringResource(R.string.licenses_tap), color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Veri kaynakları: her biri için ne için kullanıldığı, lisansı ya da kullanım koşulu, atıf metni ve bağlantılar. */
@Composable
fun DataSourcesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sources = remember { DataSources.all(java.time.Year.now().value) }
    AboutPage(stringResource(R.string.about_data_sources), onBack) {
        item { IntroHint(stringResource(R.string.sources_intro)) }
        items(sources, key = { it.key }) { source ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(source.name, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(sourceUse(source.key)), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Field(stringResource(R.string.sources_license), source.license)
                Field(stringResource(R.string.sources_attribution), source.attribution)
                Field(stringResource(R.string.sources_terms), stringResource(sourceTerms(source.key)))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill(stringResource(R.string.about_open_web)) { AboutLinks.open(context, source.link) }
                    GlassPill(stringResource(R.string.sources_open_terms)) { AboutLinks.open(context, source.terms) }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Text(label, color = TextPrimary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Text(value, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
}

private fun sourceUse(key: String): Int = when (key) {
    "open_meteo" -> R.string.source_open_meteo_use
    "ecmwf" -> R.string.source_ecmwf_use
    "meteo_france" -> R.string.source_meteo_france_use
    "met_office" -> R.string.source_met_office_use
    "cams" -> R.string.source_cams_use
    "mgm" -> R.string.source_mgm_use
    "noaa" -> R.string.source_noaa_use
    "osm" -> R.string.source_osm_use
    else -> R.string.source_geonames_use
}

private fun sourceTerms(key: String): Int = when (key) {
    "open_meteo" -> R.string.source_open_meteo_terms
    "ecmwf" -> R.string.source_ecmwf_terms
    "meteo_france" -> R.string.source_meteo_france_terms
    "met_office" -> R.string.source_met_office_terms
    "cams" -> R.string.source_cams_terms
    "mgm" -> R.string.source_mgm_terms
    "noaa" -> R.string.source_noaa_terms
    "osm" -> R.string.source_osm_terms
    else -> R.string.source_geonames_terms
}

/** Gizlilik politikası: uygulamaya gömülü tam metin (dile göre res/raw), ağdan çekilmez; site sayfasıyla aynıdır. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val blocks by produceState<List<PolicyBlock>>(emptyList(), resources) {
        value = withContext(Dispatchers.IO) {
            runCatching { resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() } }
                .map(PolicyText::parse).getOrDefault(emptyList())
        }
    }
    AboutPage(stringResource(R.string.about_privacy), onBack) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                blocks.forEach { block ->
                    when (block) {
                        is PolicyBlock.Title -> Text(block.text, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                        is PolicyBlock.Section -> Text(block.text, color = TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
                        is PolicyBlock.Paragraph -> RichText(block.text, Modifier.padding(top = 8.dp))
                        is PolicyBlock.Bullet -> Row(Modifier.padding(top = 6.dp)) {
                            Text("•  ", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            RichText(block.text)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                GlassPill(stringResource(R.string.about_privacy_web)) { AboutLinks.open(context, AboutLinks.PRIVACY) }
            }
        }
    }
}

@Composable
private fun RichText(text: String, modifier: Modifier = Modifier) {
    val styled = remember(text) {
        buildAnnotatedString {
            PolicyText.spans(text).forEach { (part, bold) ->
                if (bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = TextPrimary)) { append(part) } else append(part)
            }
        }
    }
    Text(styled, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(com.alihaydarsayar.communesky.ui.common.currentLocale()), color = TextSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun IntroHint(text: String) {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

@Composable
private fun ColumnScope.Hint(text: String) {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
}
