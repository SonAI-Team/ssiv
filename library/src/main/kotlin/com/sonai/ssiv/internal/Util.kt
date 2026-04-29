package com.sonai.ssiv.internal

import android.net.Uri
import java.util.zip.ZipFile

internal fun String.fixUriPrefix(): String = replace(
    regex = Regex(":(%2F)+", RegexOption.IGNORE_CASE),
    replacement = ":///",
)

internal fun <T> Uri.useZipEntry(block: (ZipFile, java.util.zip.ZipEntry) -> T): T {
    val file = ZipFile(schemeSpecificPart)
    return file.use { file ->
        val entry = requireNotNull(file.getEntry(fragment)) {
            "Entry $fragment not found in the zip"
        }
        block(file, entry)
    }
}
