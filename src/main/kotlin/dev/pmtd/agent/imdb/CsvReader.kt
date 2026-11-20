package dev.pmtd.agent.imdb

import java.io.PushbackReader
import java.io.Reader

class CsvFormatException(message: String) : IllegalArgumentException(message)

/** Reads records one at a time, including quoted fields containing line breaks. */
class CsvReader(reader: Reader) {
    private val input = PushbackReader(reader, 1)
    private var firstCharacter = true

    fun readRecord(): List<String>? {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var started = false
        while (true) {
            var value = input.read()
            if (firstCharacter) {
                firstCharacter = false
                if (value == 0xFEFF) value = input.read()
            }
            if (value == -1) {
                if (quoted) throw CsvFormatException("Unterminated quoted field")
                if (!started && fields.isEmpty() && field.isEmpty()) return null
                fields.add(field.toString())
                return fields
            }
            val char = value.toChar()
            started = true
            if (quoted) {
                if (char == '"') {
                    val next = input.read()
                    if (next == '"'.code) field.append('"')
                    else {
                        quoted = false
                        closedQuote = true
                        if (next != -1) input.unread(next)
                    }
                } else field.append(char)
                continue
            }
            when (char) {
                ',' -> {
                    fields.add(field.toString())
                    field.setLength(0)
                    closedQuote = false
                }
                '\n', '\r' -> {
                    if (char == '\r') {
                        val next = input.read()
                        if (next != '\n'.code && next != -1) input.unread(next)
                    }
                    fields.add(field.toString())
                    return fields
                }
                '"' -> {
                    if (field.isNotEmpty() || closedQuote) throw CsvFormatException("Unexpected quote")
                    quoted = true
                }
                else -> {
                    if (closedQuote) throw CsvFormatException("Unexpected character after quoted field")
                    field.append(char)
                }
            }
        }
    }
}
