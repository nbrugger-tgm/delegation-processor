package com.example.consumer

import com.example.external.Packager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PackagerDelegatorTest {
    private class TestPackager : Packager {
        override fun pack(value: String): String = "packed:$value"
    }

    private class Prefixing(
        override val packagerDelegate: Packager,
    ) : PackagerDelegator

    @Test
    fun `delegates from a Java package-info anchor`() {
        val packager = Prefixing(TestPackager())

        assertThat(packager.pack("value")).isEqualTo("packed:value")
    }
}
