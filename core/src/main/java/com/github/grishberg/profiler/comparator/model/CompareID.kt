package com.github.grishberg.profiler.comparator.model

data class CompareID(
    val name: String,
    val parent: CompareID?
) {
    private var _hashcode = Int.MIN_VALUE

    override fun hashCode(): Int {
        if (_hashcode != Int.MIN_VALUE) return _hashcode

        _hashcode = name.hashCode()
        _hashcode = 31 * _hashcode + (parent?.hashCode() ?: 0)
        return _hashcode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CompareID

        if (name != other.name) return false
        if (hashCode() != other.hashCode()) return false

        return true
    }
}
