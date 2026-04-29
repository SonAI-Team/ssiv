package com.sonai.ssiv

import java.io.IOException

/**
 * An exception thrown when an image cannot be decoded.
 */
class ImageDecodeException : IOException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
