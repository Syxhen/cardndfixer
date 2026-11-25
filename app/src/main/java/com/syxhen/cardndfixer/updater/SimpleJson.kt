package com.syxhen.cardndfixer.updater

/**
 * Lightweight, zero-reflection JSON parser used for GitHub Releases API payloads.
 * Avoids Android framework org.json stubs in local JVM unit tests and requires no
 * serialization libraries or R8 keep rules.
 */
internal object SimpleJson {

    fun parse(input: String): Any? {
        val reader = Reader(input)
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.isAtEnd()) {
            throw IllegalArgumentException("Unexpected trailing JSON content at index ${reader.pos}")
        }
        return value
    }

    private class Reader(private val text: String) {
        var pos: Int = 0

        fun isAtEnd(): Boolean = pos >= text.length

        fun skipWhitespace() {
            while (!isAtEnd() && text[pos].isWhitespace()) {
                pos++
            }
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (isAtEnd()) throw IllegalArgumentException("Unexpected end of JSON")
            return when (text[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                '-', in '0'..'9' -> readNumber()
                else -> throw IllegalArgumentException("Unexpected char '${text[pos]}' at index $pos")
            }
        }

        private fun readObject(): Map<String, Any?> {
            pos++ // '{'
            skipWhitespace()
            val map = LinkedHashMap<String, Any?>()
            if (!isAtEnd() && text[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (isAtEnd() || text[pos] != '"') {
                    throw IllegalArgumentException("Expected object key string at index $pos")
                }
                val key = readString()
                skipWhitespace()
                if (isAtEnd() || text[pos] != ':') {
                    throw IllegalArgumentException("Expected ':' at index $pos")
                }
                pos++
                map[key] = readValue()
                skipWhitespace()
                if (isAtEnd()) throw IllegalArgumentException("Unterminated JSON object")
                when (text[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at index $pos")
                }
            }
        }

        private fun readArray(): List<Any?> {
            pos++ // '['
            skipWhitespace()
            val list = ArrayList<Any?>()
            if (!isAtEnd() && text[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(readValue())
                skipWhitespace()
                if (isAtEnd()) throw IllegalArgumentException("Unterminated JSON array")
                when (text[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at index $pos")
                }
            }
        }

        private fun readString(): String {
            pos++ // '"'
            val sb = StringBuilder()
            while (!isAtEnd()) {
                val c = text[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (isAtEnd()) throw IllegalArgumentException("Unterminated escape sequence")
                        when (val esc = text[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) {
                                    throw IllegalArgumentException("Incomplete unicode escape at index $pos")
                                }
                                val code = text.substring(pos, pos + 4).toInt(16)
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Unknown escape '\\$esc' at index $pos")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated JSON string")
        }

        private fun readLiteral(expected: String, result: Any?): Any? {
            if (!text.startsWith(expected, pos)) {
                throw IllegalArgumentException("Expected '$expected' at index $pos")
            }
            pos += expected.length
            return result
        }

        private fun readNumber(): Number {
            val start = pos
            if (text[pos] == '-') pos++
            while (!isAtEnd() && text[pos] in '0'..'9') pos++
            var isDecimal = false
            if (!isAtEnd() && text[pos] == '.') {
                isDecimal = true
                pos++
                while (!isAtEnd() && text[pos] in '0'..'9') pos++
            }
            if (!isAtEnd() && (text[pos] == 'e' || text[pos] == 'E')) {
                isDecimal = true
                pos++
                if (!isAtEnd() && (text[pos] == '+' || text[pos] == '-')) pos++
                while (!isAtEnd() && text[pos] in '0'..'9') pos++
            }
            val token = text.substring(start, pos)
            return if (isDecimal) token.toDouble() else token.toLong()
        }
    }
}
