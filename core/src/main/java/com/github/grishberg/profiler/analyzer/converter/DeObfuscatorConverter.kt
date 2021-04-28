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
                val typeHolder = argumentsReader.read(typeSignature).firstOrNull()
                typeFromSignature = typeHolder?.name
                if (typeHolder != null && typeHolder.type != Type.SIMPLE) {
                    typeFromSignature = deObfuscator.originalClassName(typeFromSignature)
                }
                argumentsFromSignature = decodeArguments(argumentSignature)
            }
        }

        val originalMethodName =
            deObfuscator.originalMethodName(className, sourceMethodName, typeFromSignature, argumentsFromSignature)
        if (originalMethodName == sourceMethodName && originalMethodName.length < 2 && className.endsWith("Kt")) {
            val javaClassName = className.substring(0, className.length - 2)
            return deObfuscator.originalMethodName(javaClassName, sourceMethodName, typeFromSignature, argumentsFromSignature)
        }
        return removeMethodPackageIfNeeded(originalMethodName)
    }

    private fun removeMethodPackageIfNeeded(methodName: String): String {
        val lastIndexOfDot = methodName.lastIndexOf('.')
        if (lastIndexOfDot < 0) {
            return methodName
        }
        return methodName.substring(lastIndexOfDot +1)
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