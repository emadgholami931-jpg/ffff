package com.vazheyar.app.data

import java.io.Reader

object CsvWordParser {
    fun parse(reader: Reader): List<String> {
        val rows = parseRows(reader)
        if (rows.isEmpty()) return emptyList()

        val header = rows.first().map { it.trim().removePrefix("\uFEFF").lowercase() }
        val preferredNames = setOf("word", "english", "vocab", "vocabulary", "کلمه", "واژه")
        val headerIndex = header.indexOfFirst { it in preferredNames }
        val hasHeader = headerIndex >= 0
        val wordIndex = if (hasHeader) headerIndex else 0
        val dataRows = if (hasHeader) rows.drop(1) else rows

        return dataRows.asSequence()
            .mapNotNull { it.getOrNull(wordIndex)?.trim() }
            .map { it.removePrefix("\uFEFF").trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .toList()
    }

    private fun parseRows(reader: Reader): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var current = reader.read()

        while (current != -1) {
            val ch = current.toChar()
            if (inQuotes) {
                if (ch == '"') {
                    val next = reader.read()
                    if (next == '"'.code) {
                        cell.append('"')
                        current = reader.read()
                        continue
                    } else {
                        inQuotes = false
                        current = next
                        continue
                    }
                } else {
                    cell.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row.add(cell.toString())
                        cell.clear()
                    }
                    '\n' -> {
                        row.add(cell.toString().trimEnd('\r'))
                        cell.clear()
                        if (row.any { it.isNotBlank() }) rows.add(row)
                        row = mutableListOf()
                    }
                    else -> cell.append(ch)
                }
            }
            current = reader.read()
        }

        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString().trimEnd('\r'))
            if (row.any { it.isNotBlank() }) rows.add(row)
        }
        return rows
    }
}
