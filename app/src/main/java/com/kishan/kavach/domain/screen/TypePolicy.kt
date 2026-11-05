package com.kishan.kavach.domain.screen

sealed interface TypeResult {
    data object Unavailable : TypeResult
    data class Typed(val text: String) : TypeResult
    data class Mismatch(val expected: String, val actual: String) : TypeResult
}

object TypePolicy {

    fun decide(channelResult: String?, expected: String): TypeResult {
        if (channelResult == null) {
            return TypeResult.Unavailable
        }
        if (channelResult == expected) {
            return TypeResult.Typed(channelResult)
        }
        return TypeResult.Mismatch(expected = expected, actual = channelResult)
    }
}
