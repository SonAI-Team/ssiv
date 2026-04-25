package com.sonai.ssiv.decoder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DecoderFactoryTest {

    @Test
    fun `test lambda factory`() {
        val expected = "decoder"
        val factory = DecoderFactory { expected }
        
        assertEquals(expected, factory.make())
    }
}
