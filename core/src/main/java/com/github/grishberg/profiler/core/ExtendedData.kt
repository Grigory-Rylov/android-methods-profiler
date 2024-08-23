package com.github.grishberg.profiler.core

sealed interface ExtendedData {
    val tag: String?
    val id:String
    val fullName: String
    data class CppFunctionData(
        override val tag: String?,
        override val id: String,
        override val fullName: String,
        val classOrNamespace: String,
        val parameters:List<String>,
        val isUserCode: Boolean,
        val fileName: String,
        val vAddress: Long,
    ):ExtendedData

    data class JavaMethodData(override val tag: String?,
        override val id: String,
        override val fullName: String,
        val className: String,
        val signature: String,
        ):ExtendedData

    data class NoSymbolData(override val tag: String?,
        override val id: String,
        override val fullName: String,
        val isKernel: Boolean,
        ):ExtendedData

    data class SyscallData(override val tag: String?,
        override val id: String,
        override val fullName: String,
        ):ExtendedData
}
