package com.sonai.ssiv.decoder

/**
 * Interface for [SSIVImageDecoder] and [ImageRegionDecoder] factories.
 * @param T the class of decoder that will be produced.
 */
fun interface DecoderFactory<T> {

    /**
     * Produce a new instance of a decoder with type [T].
     * @return a new instance of your decoder.
     */
    fun make(): T

}
