package com.example.data.parser

import com.example.data.local.entity.ChannelEntity
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class M3uParser {

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]*))""")

    fun parse(inputStream: InputStream, playlistId: Long): List<ChannelEntity> {
        val channels = ArrayList<ChannelEntity>(256)
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            var pendingName: String? = null
            var pendingLogo: String? = null
            var pendingGroup: String? = null
            var pendingTvgId: String? = null
            var order = 0

            var rawLine = reader.readLine()
            while (rawLine != null) {
                val line = rawLine.trim().trimStart('\uFEFF')
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        val attrsPart = extractAttributesSection(line)
                        val attrs = mutableMapOf<String, String>()
                        attrRegex.findAll(attrsPart).forEach { match ->
                            val key = match.groupValues[1].lowercase()
                            val value = match.groupValues[2]
                                .ifEmpty { match.groupValues[3] }
                                .ifEmpty { match.groupValues[4] }
                            attrs[key] = value.trim()
                        }

                        val commaIndex = findChannelNameCommaIndex(line)
                        val titleFromComma = if (commaIndex != -1 && commaIndex + 1 < line.length) {
                            line.substring(commaIndex + 1).trim()
                        } else {
                            ""
                        }

                        pendingName = titleFromComma.ifBlank {
                            attrs["tvg-name"].orEmpty()
                        }.ifBlank { "Canal ${order + 1}" }

                        pendingLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                        pendingGroup = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: pendingGroup
                        pendingTvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                    }

                    line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                        val grp = line.substringAfter(":").trim()
                        if (grp.isNotBlank()) {
                            pendingGroup = grp
                        }
                    }

                    line.startsWith("#") -> {
                        // Ignore other M3U directives (#EXTM3U, #EXTVLCOPT, etc.)
                    }

                    else -> {
                        // Stream URL line
                        if (line.contains("://")) {
                            channels.add(
                                ChannelEntity(
                                    playlistId = playlistId,
                                    name = pendingName ?: "Canal ${order + 1}",
                                    streamUrl = line,
                                    logoUrl = pendingLogo,
                                    groupName = pendingGroup?.takeIf { it.isNotBlank() } ?: "Sem Categoria",
                                    tvgId = pendingTvgId,
                                    isFavorite = false,
                                    streamOrder = order++
                                )
                            )
                        }
                        pendingName = null
                        pendingLogo = null
                        pendingGroup = null
                        pendingTvgId = null
                    }
                }
                rawLine = reader.readLine()
            }
        }
        return channels
    }

    private fun extractAttributesSection(line: String): String {
        val commaIndex = findChannelNameCommaIndex(line)
        return if (commaIndex != -1) line.substring(0, commaIndex) else line
    }

    private fun findChannelNameCommaIndex(line: String): Int {
        var inDoubleQuote = false
        var inSingleQuote = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
                '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
                ',' -> if (!inDoubleQuote && !inSingleQuote) return i
            }
        }
        return -1
    }
}
