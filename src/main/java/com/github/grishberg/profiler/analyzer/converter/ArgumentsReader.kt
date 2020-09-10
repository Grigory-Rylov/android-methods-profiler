package com.github.grishberg.profiler.analyzer.converter

enum class Type {
    SIMPLE,
    OBJECT,
    OBFUSCATED
}

data class TypeHolder(val name: String, val type: Type = Type.SIMPLE)

class ArgumentsReader {
    private var state: State = Idle()
    private val result = mutableListOf<TypeHolder>()

    fun read(arguments: String): List<TypeHolder> {
        result.clear()
        state = Idle()
        for (c in arguments) {
            state.processSymbol(c)
        }
        return result
    }

    private inner class Idle(
        private val isArray: Boolean = false
    ) : State {
        override fun processSymbol(symbol: Char) {
            when (symbol) {
                'L' -> state = ReadingObject(isArray)
                '[' -> state = Idle(isArray = true)
                'Z' -> result.add(TypeHolder("boolean" + if (isArray) "[]" else ""))
                'B' -> result.add(TypeHolder("byte" + if (isArray) "[]" else ""))
                'C' -> result.add(TypeHolder("char" + if (isArray) "[]" else ""))
                'S' -> result.add(TypeHolder("short" + if (isArray) "[]" else ""))
                'I' -> result.add(TypeHolder("int" + if (isArray) "[]" else ""))
                'J' -> result.add(TypeHolder("long" + if (isArray) "[]" else ""))
                'F' -> result.add(TypeHolder("float" + if (isArray) "[]" else ""))
                'D' -> result.add(TypeHolder("double" + if (isArray) "[]" else ""))
                'V' -> result.add(TypeHolder("void" + if (isArray) "[]" else ""))
            }
        }
    }

    private inner class ReadingObject(
        private val isArray: Boolean
    ) : State {
        private val buffer = StringBuilder()

        override fun processSymbol(symbol: Char) {
            if (symbol == ';') {
                state = Idle()
                var readType = buffer.toString()
                val hasSlashes = readType.indexOf('/') >= 0
                if (hasSlashes) {
                    readType = readType.replace("/", ".")
                }
                result.add(
                    TypeHolder(
                        readType + if (isArray) "[]" else "",
                        if (hasSlashes) Type.OBJECT else Type.OBFUSCATED
                    )
                )
            } else {
                buffer.append(symbol)
            }
        }
    }

    private interface State {
        fun processSymbol(symbol: Char)
    }
}