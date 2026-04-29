package com.sonai.ssiv.internal

import android.content.ContentResolver

internal const val URI_SCHEME_FILE = ContentResolver.SCHEME_FILE
internal const val URI_SCHEME_ZIP = "file+zip"
internal const val URI_SCHEME_RES = ContentResolver.SCHEME_ANDROID_RESOURCE
internal const val URI_SCHEME_CONTENT = ContentResolver.SCHEME_CONTENT
internal const val URI_PATH_ASSET = "/android_asset/"

internal const val FILE_PREFIX = "file://"
internal const val ASSET_PREFIX = "${FILE_PREFIX}android_asset/"
internal const val ZIP_PREFIX = "${URI_SCHEME_ZIP}://"
internal const val RESOURCE_PREFIX = "${URI_SCHEME_RES}://"
