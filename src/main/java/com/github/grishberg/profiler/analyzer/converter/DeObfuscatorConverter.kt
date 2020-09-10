package com.github.grishberg.profiler.analyzer.converter

import proguard.retrace.DeObfuscator

private const val SIGNATURE_REGEX = "\\((.*)\\)(.*)"

class DeObfuscatorConverter(
    private val deObfuscator: DeObfuscator
) : NameConverter {
    private val reg = Regex(SIGNATURE_REGEX)
    private val argumentsReader = ArgumentsReader()


    override fun convertClassName(sourceClassName: String): String {
        return deObfuscator.originalClassName(sourceClassName)
    }

    override fun convertMethodName(className: String, sourceMethodName: String, signature: String?): String {
        var typeFromSignature: String? = null
        var argumentsFromSignature: String? = null

        if (signature != null) {
            val match = reg.find(signature)
            if (match != null) {
                val argumentSignature = match.groupValues[1]
                val typeSignature = match.groupValues[2]
                typeFromSignature = argumentsReader.read(typeSignature).firstOrNull()?.name
                if (typeFromSignature != null) {
                    typeFromSignature = deObfuscator.originalClassName(typeFromSignature)
                }
                argumentsFromSignature = decodeArguments(argumentSignature)
            }
        }

        return deObfuscator.originalMethodName(className, sourceMethodName, typeFromSignature, argumentsFromSignature)
    }

    private fun decodeArguments(argumentSignature: String): String? {
        val argumentsList = argumentsReader.read(argumentSignature).map {
            if (it.type == Type.OBFUSCATED)
                deObfuscator.originalClassName(it.name)
            else it.name
        }

        if (argumentsList.isEmpty()) {
            return null
        }
        return argumentsList.joinToString(",")
    }
}