package dev.privatecompanion.eclipse

import org.junit.Assert.*
import org.junit.Test

class CpuModeTest {
    @Test fun autoIsDefaultAndMissingValuesDoNotCrash() {
        assertEquals(CpuMode.AUTO, CpuMode.fromStored(null))
        assertEquals(CpuMode.AUTO, CpuMode.fromStored("invalid"))
    }
    @Test fun persistedModesRoundTrip() {
        for (mode in CpuMode.entries) assertEquals(mode, CpuMode.fromStored(mode.name))
    }
    @Test fun lowCoreDevicesNeverOversubscribe() {
        for (mode in CpuMode.entries) {
            assertEquals(1, mode.threads(1))
            assertEquals(1, mode.threads(0))
            assertTrue(mode.threads(2) in 1..2)
        }
    }
    @Test fun typicalEightCorePhoneKeepsHeadroomInAuto() {
        assertEquals(4, CpuMode.AUTO.threads(8))
        assertEquals(2, CpuMode.EFFICIENT.threads(8))
        assertEquals(4, CpuMode.BALANCED.threads(8))
        assertEquals(6, CpuMode.PERFORMANCE.threads(8))
    }
    @Test fun noModeRequestsMoreThanEightThreads() {
        for (mode in CpuMode.entries)
            for (count in listOf(-10, 0, 1, 2, 4, 6, 8, 16, 64, 1000)) {
                assertTrue(mode.threads(count) in 1..8)
                assertTrue(mode.threads(count) <= count.coerceAtLeast(1))
            }
    }
    @Test fun autoOnSmallDeviceReservesCapacity() {
        assertEquals(2, CpuMode.AUTO.threads(3))
        assertEquals(3, CpuMode.AUTO.threads(4))
        assertEquals(4, CpuMode.AUTO.threads(6))
    }
}
