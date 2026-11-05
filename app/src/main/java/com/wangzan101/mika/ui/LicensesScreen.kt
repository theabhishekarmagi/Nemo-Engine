// Modified for Nemo-Engine by MAGI ABHISHEKAR (theabhishekarmagi), 2026-11-05.
package com.wangzan101.mika.ui

import com.wangzan101.mika.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.components.SubPage
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/** 许可证页上第三方组件的数据;与仓库 licenses/THIRD_PARTY_NOTICES.md 的条目一一对应(LicensesTest 核对)。 */
object Licenses {
    data class Entry(val name: String, val license: String, val note: String)

    const val GPL_SOURCE_OFFER =
        "PRoot (GPL-2.0-or-later) and talloc (LGPL-3.0-or-later) are distributed as separate programs; Mika does not link to them. " +
            "Complete source code, Mika's patches and build scripts accompany each release as mika-<version>-gpl-sources.tar.gz. " +
            "Source URLs, checksums, patches and build scripts are also available in tools/sandbox/ for rebuilding and relinking."

    const val RUNTIME_NOTE =
        "The bundled environment for Python, Node and other programs is based on Alpine Linux 3.22. Its packages and licenses are listed below. " +
            "Source code for GPL, LGPL and MPL packages, including Alpine build recipes and upstream sources, is provided in mika-<version>-gpl-sources.tar.gz."

    fun runtimePackages(json: String): List<Entry> =
        Json.parseToJsonElement(json).jsonObject.getValue("packages").jsonArray.map { e ->
            val o = e.jsonObject
            fun f(k: String) = o[k]?.jsonPrimitive?.content.orEmpty()
            Entry(f("name"), f("license"), "Version ${f("version")}. ${f("url")}")
        }

    // Source notice identifiers remain stable; the screen uses English display names.
    fun displayName(name: String): String = when (name) {
        "Kotlin 标准库" -> "Kotlin standard library"
        "AndroidX(含 Jetpack Compose)" -> "AndroidX (including Jetpack Compose)"
        "Guava ListenableFuture(由 AndroidX 库引入)" -> "Guava ListenableFuture (AndroidX dependency)"
        else -> name
    }

    val ENTRIES = listOf(
        Entry("Mika", "Apache-2.0", "Nemo-Engine distribution maintained by MAGI ABHISHEKAR (theabhishekarmagi). Modifications dated 5 November 2026. Original Mika code: Copyright 2026 wangzan101. The full license appears above."),
        Entry("PRoot", "GPL-2.0-or-later", "Version 5.4.0, with a patch to terminate child processes when PRoot exits. $GPL_SOURCE_OFFER"),
        Entry("talloc", "LGPL-3.0-or-later", "Version 2.4.3, unmodified and statically linked into PRoot. Rebuild and relink using the sources and scripts in tools/sandbox/."),
        Entry("Apache Commons Compress", "Apache-2.0", "Extracts the bundled runtime on first use. Copyright 2002-2025 The Apache Software Foundation."),
        Entry("Apache Commons Codec", "Apache-2.0", "Apache Commons Compress dependency. Copyright 2002-2025 The Apache Software Foundation."),
        Entry("Apache Commons IO", "Apache-2.0", "Apache Commons Compress dependency. Copyright 2002-2025 The Apache Software Foundation."),
        Entry("Apache Commons Lang", "Apache-2.0", "Apache Commons Compress dependency. Copyright 2001-2025 The Apache Software Foundation."),
        Entry("Kotlin 标准库", "Apache-2.0", "kotlin-stdlib 2.1.20 and JetBrains annotations 23.0.0. https://kotlinlang.org/"),
        Entry("kotlinx.coroutines", "Apache-2.0", "Version 1.9.0. https://github.com/Kotlin/kotlinx.coroutines"),
        Entry("kotlinx.serialization", "Apache-2.0", "Version 1.7.3. https://github.com/Kotlin/kotlinx.serialization"),
        Entry("OpenTelemetry", "Apache-2.0", "OTLP message definitions for detailed traces. https://opentelemetry.io/"),
        Entry("Protocol Buffers", "BSD-3-Clause", "Protobuf encoding for detailed traces. https://protobuf.dev/"),
        Entry("OkHttp", "Apache-2.0", "Version 4.12.0, for model API requests. https://square.github.io/okhttp/"),
        Entry("Okio", "Apache-2.0", "Version 3.6.0, an OkHttp dependency. https://github.com/square/okio/"),
        Entry("AndroidX(含 Jetpack Compose)", "Apache-2.0", "Compose 1.7.6, Material3 1.3.1, Activity 1.9.3, Lifecycle 2.8.3, Core 1.13.1 and WebKit 1.12.1. https://developer.android.com/jetpack/androidx"),
        Entry("Guava ListenableFuture(由 AndroidX 库引入)", "Apache-2.0", "listenablefuture 1.0. https://github.com/google/guava"),
        Entry("scrcpy", "Apache-2.0", "The shell system-context approach follows scrcpy; source headers retain attribution."),
        Entry("Alpine Linux", "Individual package licenses", RUNTIME_NOTE),
        Entry("XZ for Java", "0BSD", "Version 1.10, for extracting the bundled runtime. https://tukaani.org/xz/java.html"),
        Entry("commonmark-java", "BSD-2-Clause", "Version 0.30.1, including table and strikethrough extensions, for rendering Markdown replies. Copyright (c) 2015, Atlassian Pty Ltd. https://github.com/commonmark/commonmark-java"),
    )
}

/**
 * 许可证页(关于页底部「Apache-2.0 开源许可」进来):先是许可证原文(assets/licenses/Apache-2.0.txt),
 * 下面列出随 Mika 发布的第三方组件,一个组件一行(名称、许可证),说明在行下面;页脚指向完整文本。
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val licenseText = remember {
        runCatching { context.assets.open(LICENSE_ASSET).use { it.readBytes().decodeToString() } }.getOrDefault("")
    }
    val runtime = remember {
        runCatching { context.assets.open("runtime/packages.json").use { Licenses.runtimePackages(it.readBytes().decodeToString()) } }
            .getOrDefault(emptyList())
    }
    val thirdParty = Licenses.ENTRIES.filter { it.name != "Mika" } + runtime
    SubPage(AboutCopy.LICENSE_TITLE, onBack) {
        if (licenseText.isNotEmpty()) {
            MikaCard(Modifier.padding(bottom = 12.dp).testTag("license-text")) {
                Text(
                    licenseText.trimEnd(), Modifier.padding(16.dp),
                    style = MikaType.Hint.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, lineHeight = 19.sp),
                    color = MikaTheme.colors.ink2,
                )
            }
        }
        GroupLabel(stringResource(R.string.licenses_third_party, thirdParty.size))
        GroupCard {
            thirdParty.forEachIndexed { i, e ->
                Column {
                    ListRow(null, Licenses.displayName(e.name), e.license, first = i == 0)
                    Text(e.note, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), style = MikaType.Hint, color = MikaTheme.colors.ink2)
                }
            }
        }
        FooterNote(stringResource(R.string.licenses_footer))
    }
}

private const val LICENSE_ASSET = "licenses/Apache-2.0.txt"
