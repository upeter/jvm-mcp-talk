package org.course.utils

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UtilsTest {

    @Test
    fun `perform kmeans clustering`() {
        val result = performKMeansClustering(listOf(listOf(1.0, 1.0), listOf(2.0, 2.0), listOf(1.0, 2.0), listOf(3.0, 3.0)), 2)
        result.size shouldBe 2

    }
}