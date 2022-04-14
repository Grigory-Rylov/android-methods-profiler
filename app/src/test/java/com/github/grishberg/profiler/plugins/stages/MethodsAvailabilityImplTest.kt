package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.analyzer.ProfileDataImpl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MethodsAvailabilityImplTest {

    @Test
    fun `lambda is not available method`() {
        val underTest = MethodsAvailabilityImpl()

        assertFalse(underTest.isMethodAvailable(method("com.example.Foo\$3562b756")))
    }

    @Test
    fun `inner class method is available`() {
        val underTest = MethodsAvailabilityImpl()

        assertTrue(underTest.isMethodAvailable(method("com.example.Foo\$Class.method")))
    }


    private fun method(methodName: String): ProfileData {
        return ProfileDataImpl(methodName, 0, 0.0, 0.0)
    }
}
