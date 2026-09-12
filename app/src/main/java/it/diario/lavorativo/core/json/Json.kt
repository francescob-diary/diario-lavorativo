package it.diario.lavorativo.core.json

/**
 * JSON minimo, scritto a mano.
 *
 * Android ha org.json gia' dentro, ma e' codice che fuori da un telefono
 * non si puo' provare. Il ripristino e' l'unica funzione dell'app dove uno
 * sbaglio non da' fastidio: cancella i dati. Quindi il formato del backup
 * deve essere verificabile andata e ritorno, e per esserlo deve essere
 * Kotlin puro.
 *
 * Copre quello che serve al backup: oggetti, liste, stringhe, numeri,
 * booleani e null. Niente di piu'.
 */
sealed interface JsonValue {

    data class Obj(val fields: Map<String, JsonValue> = emptyMap()) : JsonValue {
        operator fun get(key: String): JsonValue? = fields[key]

        fun string(key: String): String =
            (fields[key] as? Str)?.value ?: ""

        fun stringOrNull(key: String): String? =
            (fields[key] as? Str)?.value

        fun long(key: String, fallback: Long = 0L): Long =
            (fields[key] as? Num)?.asLong() ?: fallback

        fun longOrNull(key: String): Long? = (fields[key] as? Num)?.asLong()

        fun int(key: String, fallback: Int = 0): Int =
            (fields[key] as? Num)?.asLong()?.toInt() ?: fallback

        fun intOrNull(key: String): Int? = (fields[key] as? Num)?.asLong()?.toInt()

        fun double(key: String, fallback: Double = 0.0): Double =
            (fields[key] as? Num)?.asDouble() ?: fallback

        fun doubleOrNull(key: String): Double? = (fields[key] as? Num)?.asDouble()

        fun bool(key: String, fallback: Boolean = false): Boolean =
            (fields[key] as? Bool)?.value ?: fallback

        fun array(key: String): List<JsonValue> =
            (fields[key] as? Arr)?.items ?: emptyList()

        fun objects(key: String): List<Obj> = array(key).filterIsInstance<Obj>()
    }

    data class Arr(val items: List<JsonValue> = emptyList()) : JsonValue

    data class Str(val value: String) : JsonValue

    /**
     * Il numero si conserva com'era scritto, non come Double.
     * Un istante in millisecondi riscritto come 1.7412E12 sarebbe un orario
     * sbagliato al ritorno: qui invece torna identico a com'era.
     */
    data class Num(val raw: String) : JsonValue {
        fun asLong(): Long = raw.toDoubleOrNull()?.toLong() ?: 0L
        fun asDouble(): Double = raw.toDoubleOrNull() ?: 0.0

        companion object {
            fun of(value: Long): Num = Num(value.toString())
            fun of(value: Int): Num = Num(value.toString())
            fun of(value: Double): Num = Num(value.toString())
        }
    }

    data class Bool(val value: Boolean) : JsonValue

    data object Null : JsonValue
}

/** Costruisce un oggetto saltando le chiavi con valore nullo. */
class JsonObjectBuilder {
    private val fields = LinkedHashMap<String, JsonValue>()

    fun put(key: String, value: String?) {
        fields[key] = if (value == null) JsonValue.Null else JsonValue.Str(value)
    }

    fun put(key: String, value: Long?) {
        fields[key] = if (value == null) JsonValue.Null else JsonValue.Num.of(value)
    }

    fun put(key: String, value: Int?) {
        fields[key] = if (value == null) JsonValue.Null else JsonValue.Num.of(value)
    }

    fun put(key: String, value: Double?) {
        fields[key] = if (value == null) JsonValue.Null else JsonValue.Num.of(value)
    }

    fun put(key: String, value: Boolean) {
        fields[key] = JsonValue.Bool(value)
    }

    fun put(key: String, value: JsonValue) {
        fields[key] = value
    }

    fun putArray(key: String, values: List<JsonValue>) {
        fields[key] = JsonValue.Arr(values)
    }

    fun build(): JsonValue.Obj = JsonValue.Obj(fields)
}

fun jsonObject(block: JsonObjectBuilder.() -> Unit): JsonValue.Obj =
    JsonObjectBuilder().apply(block).build()

/** Trasforma un valore nel testo da scrivere sul file. */
object JsonWriter {

    fun write(value: JsonValue, indent: Boolean = true): String {
        val sb = StringBuilder()
        append(sb, value, if (indent) 0 else -1)
        return sb.toString()
    }

    private fun append(sb: StringBuilder, value: JsonValue, level: Int) {
        when (value) {
            is JsonValue.Obj -> appendObject(sb, value, level)
            is JsonValue.Arr -> appendArray(sb, value, level)
            is JsonValue.Str -> sb.append(quote(value.value))
            is JsonValue.Num -> sb.append(value.raw)
            is JsonValue.Bool -> sb.append(if (value.value) "true" else "false")
            JsonValue.Null -> sb.append("null")
        }
    }

    private fun appendObject(sb: StringBuilder, value: JsonValue.Obj, level: Int) {
        if (value.fields.isEmpty()) {
            sb.append("{}")
            return
        }
        // Livello -1 vuol dire forma compatta: va portato avanti cosi'
        // com'e', senno' -1 + 1 fa zero e ricomincia a mandare a capo.
        val dentro = if (level < 0) -1 else level + 1

        sb.append("{")
        var first = true
        value.fields.forEach { (key, child) ->
            if (!first) sb.append(",")
            first = false
            newLine(sb, dentro)
            sb.append(quote(key))
            sb.append(":")
            if (level >= 0) sb.append(" ")
            append(sb, child, dentro)
        }
        newLine(sb, level)
        sb.append("}")
    }

    private fun appendArray(sb: StringBuilder, value: JsonValue.Arr, level: Int) {
        if (value.items.isEmpty()) {
            sb.append("[]")
            return
        }
        val dentro = if (level < 0) -1 else level + 1

        sb.append("[")
        var first = true
        value.items.forEach { child ->
            if (!first) sb.append(",")
            first = false
            newLine(sb, dentro)
            append(sb, child, dentro)
        }
        newLine(sb, level)
        sb.append("]")
    }

    private fun newLine(sb: StringBuilder, level: Int) {
        if (level < 0) return
        sb.append("\n")
        repeat(level) { sb.append("  ") }
    }

    fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        value.forEach { c ->
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else ->
                    if (c.code < 0x20) {
                        sb.append("\\u")
                        sb.append(c.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(c)
                    }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}

/** Errore di lettura, con la posizione: serve per capire un file rovinato. */
class JsonException(message: String, val position: Int) :
    Exception(message + " (posizione " + position.toString() + ")")

/** Legge il testo e ricostruisce i valori. */
object JsonParser {

    fun parse(text: String): JsonValue {
        val reader = Reader(text)
        reader.skipWhitespace()
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) {
            throw JsonException("Testo in piu' dopo la fine del documento", reader.position)
        }
        return value
    }

    fun parseObject(text: String): JsonValue.Obj =
        parse(text) as? JsonValue.Obj
            ?: throw JsonException("Il documento non e' un oggetto", 0)

    private class Reader(private val text: String) {
        var position = 0

        fun atEnd(): Boolean = position >= text.length

        fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        fun readValue(): JsonValue {
            if (atEnd()) throw JsonException("Documento finito troppo presto", position)

            return when (val c = text[position]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonValue.Str(readString())
                't' -> readLiteral("true").let { JsonValue.Bool(true) }
                'f' -> readLiteral("false").let { JsonValue.Bool(false) }
                'n' -> readLiteral("null").let { JsonValue.Null }
                else ->
                    if (c == '-' || c.isDigit()) {
                        readNumber()
                    } else {
                        throw JsonException("Carattere inatteso: " + c, position)
                    }
            }
        }

        private fun readObject(): JsonValue.Obj {
            expect('{')
            val fields = LinkedHashMap<String, JsonValue>()
            skipWhitespace()

            if (peek() == '}') {
                position++
                return JsonValue.Obj(fields)
            }

            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                fields[key] = readValue()
                skipWhitespace()

                when (peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return JsonValue.Obj(fields)
                    }
                    else -> throw JsonException("Manca la virgola o la parentesi", position)
                }
            }
        }

        private fun readArray(): JsonValue.Arr {
            expect('[')
            val items = mutableListOf<JsonValue>()
            skipWhitespace()

            if (peek() == ']') {
                position++
                return JsonValue.Arr(items)
            }

            while (true) {
                skipWhitespace()
                items += readValue()
                skipWhitespace()

                when (peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return JsonValue.Arr(items)
                    }
                    else -> throw JsonException("Manca la virgola o la quadra", position)
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()

            while (true) {
                if (atEnd()) throw JsonException("Stringa mai chiusa", position)
                when (val c = text[position]) {
                    '"' -> {
                        position++
                        return sb.toString()
                    }
                    '\\' -> {
                        position++
                        if (atEnd()) throw JsonException("Sequenza di fuga incompleta", position)
                        when (val e = text[position]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (position + 4 >= text.length) {
                                    throw JsonException("Codice unicode incompleto", position)
                                }
                                val hex = text.substring(position + 1, position + 5)
                                val code = hex.toIntOrNull(16)
                                    ?: throw JsonException("Codice unicode non valido", position)
                                sb.append(code.toChar())
                                position += 4
                            }
                            else -> throw JsonException("Sequenza di fuga sconosciuta: " + e, position)
                        }
                        position++
                    }
                    else -> {
                        sb.append(c)
                        position++
                    }
                }
            }
        }

        private fun readNumber(): JsonValue.Num {
            val start = position
            if (peek() == '-') position++
            while (!atEnd() && (text[position].isDigit() || text[position] in ".eE+-")) {
                position++
            }
            val raw = text.substring(start, position)
            if (raw.toDoubleOrNull() == null) {
                throw JsonException("Numero non valido: " + raw, start)
            }
            return JsonValue.Num(raw)
        }

        private fun readLiteral(literal: String) {
            if (!text.startsWith(literal, position)) {
                throw JsonException("Atteso " + literal, position)
            }
            position += literal.length
        }

        private fun peek(): Char =
            if (atEnd()) throw JsonException("Documento finito troppo presto", position)
            else text[position]

        private fun expect(c: Char) {
            if (atEnd() || text[position] != c) {
                throw JsonException("Atteso " + c, position)
            }
            position++
        }
    }
}
