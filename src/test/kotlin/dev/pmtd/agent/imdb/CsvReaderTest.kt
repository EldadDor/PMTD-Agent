package dev.pmtd.agent.imdb

import java.io.StringReader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CsvReaderTest {
    @Test
    fun `empty input is exhausted immediately`() {
        val reader = csv("")

        assertThat(reader.readRecord()).isNull()
        assertThat(reader.readRecord()).isNull()
    }

    @Test
    fun `unquoted fields retain whitespace and empty columns including trailing column`() {
        val reader = csv("first,, padded ,last,")

        assertThat(reader.readRecord()).containsExactly("first", "", " padded ", "last", "")
        assertThat(reader.readRecord()).isNull()
    }

    @Test
    fun `LF and CRLF separate records without producing a trailing phantom record`() {
        val reader = csv("first,one\r\nsecond,two\nthird,three\r\n")

        assertThat(reader.readRecord()).containsExactly("first", "one")
        assertThat(reader.readRecord()).containsExactly("second", "two")
        assertThat(reader.readRecord()).containsExactly("third", "three")
        assertThat(reader.readRecord()).isNull()
    }

    @Test
    fun `quoted commas and empty quoted fields remain single fields`() {
        val reader = csv("\"Synthetic, title\",\"\",plain\r\n")

        assertThat(reader.readRecord()).containsExactly("Synthetic, title", "", "plain")
        assertThat(reader.readRecord()).isNull()
    }

    @Test
    fun `doubled quotes decode as literal quotes inside a quoted field`() {
        val reader = csv("\"The \"\"Synthetic\"\" Title\",\"\"\"\"\n")

        assertThat(reader.readRecord()).containsExactly("The \"Synthetic\" Title", "\"")
    }

    @Test
    fun `quoted LF stays inside a field while the next logical record remains separate`() {
        val reader = csv("id,\"line one\nline two\",tail\nnext,record,end")

        assertThat(reader.readRecord()).containsExactly("id", "line one\nline two", "tail")
        assertThat(reader.readRecord()).containsExactly("next", "record", "end")
        assertThat(reader.readRecord()).isNull()
    }

    @Test
    fun `quoted CRLF is preserved without splitting a logical record`() {
        val reader = csv("\"line one\r\nline two\",tail\r\n")

        assertThat(reader.readRecord()).containsExactly("line one\r\nline two", "tail")
        assertThat(reader.readRecord()).isNull()
    }

    @Test
    fun `a complete quoted record can end at EOF without a newline`() {
        val reader = csv("plain,\"last field\"")

        assertThat(reader.readRecord()).containsExactly("plain", "last field")
        assertThat(reader.readRecord()).isNull()
    }

    @Test
    fun `a blank line is one empty field rather than end of input`() {
        val reader = csv("\r\nnext,value")

        assertThat(reader.readRecord()).containsExactly("")
        assertThat(reader.readRecord()).containsExactly("next", "value")
        assertThat(reader.readRecord()).isNull()
    }

    @Test
    fun `unterminated quoted field rejects EOF instead of returning partial data`() {
        val reader = csv("valid,\"unfinished\nfield")

        assertThatThrownBy { reader.readRecord() }.isInstanceOf(CsvFormatException::class.java)
    }

    @Test
    fun `quote appearing within an unquoted field is malformed`() {
        val reader = csv("first,unquoted\"value,last")

        assertThatThrownBy { reader.readRecord() }.isInstanceOf(CsvFormatException::class.java)
    }

    @Test
    fun `characters after a closing quote before a separator are malformed`() {
        val reader = csv("\"closed\"unexpected,next")

        assertThatThrownBy { reader.readRecord() }.isInstanceOf(CsvFormatException::class.java)
    }

    private fun csv(value: String) = CsvReader(StringReader(value))
}
