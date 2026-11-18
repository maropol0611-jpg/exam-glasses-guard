package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class DistanceEstimateTest {
    @Test fun invalidStrengthDoesNotInventDistance() {
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, 0.0, -200.0).forEach { assertNull(estimateDistance(it)) }
    }

    @Test fun weakerSignalHasLargerModelDistanceAndOneMeterReferenceIsHonored() {
        assertEquals(1.0, estimateDistance(-59.0, -59.0)!!.meters, 0.0001)
        assertTrue(estimateDistance(-80.0)!!.meters > estimateDistance(-60.0)!!.meters)
        assertEquals(1.0, estimateDistance(-70.0, -70.0)!!.meters, 0.0001)
    }
}
