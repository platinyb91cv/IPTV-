package com.example

import com.example.data.parser.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    private val parser = M3uParser()

    @Test
    fun `parses M3U channels with logos, groups, and EXTGRP`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cnn.us" tvg-name="CNN" tvg-logo="https://example.com/cnn.png" group-title="Notícias",CNN International
            http://stream.example.com/cnn.m3u8
            #EXTINF:-1 tvg-logo="https://example.com/sport.png",Sport TV 1
            #EXTGRP:Desporto
            https://stream.example.com/sport1.ts
            #EXTINF:-1,Canal Livre
            http://stream.example.com/livre.m3u8
        """.trimIndent()

        val channels = parser.parse(m3u.byteInputStream(), playlistId = 42L)

        assertEquals(3, channels.size)

        assertEquals("CNN International", channels[0].name)
        assertEquals("Notícias", channels[0].groupName)
        assertEquals("https://example.com/cnn.png", channels[0].logoUrl)
        assertEquals("cnn.us", channels[0].tvgId)
        assertEquals("http://stream.example.com/cnn.m3u8", channels[0].streamUrl)
        assertEquals(42L, channels[0].playlistId)

        assertEquals("Sport TV 1", channels[1].name)
        assertEquals("Desporto", channels[1].groupName)
        assertEquals("https://stream.example.com/sport1.ts", channels[1].streamUrl)

        assertEquals("Canal Livre", channels[2].name)
        assertEquals("Sem Categoria", channels[2].groupName)
        assertNull(channels[2].logoUrl)
    }
}
