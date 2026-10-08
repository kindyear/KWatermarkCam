package cn.kindyear.kwatermarkcam

import cn.kindyear.kwatermarkcam.core.location.LocationProviderPolicy
import org.junit.Assert.*
import org.junit.Test

class LocationProviderPolicyTest {
    @Test fun approximatePermissionNeverRequestsGps() {
        assertEquals(listOf("network"), LocationProviderPolicy.allowedProviders(false, setOf("network", "gps")))
        assertTrue(LocationProviderPolicy.allowedProviders(false, setOf("gps")).isEmpty())
    }
    @Test fun finePermissionOnlyRequestsEnabledProviders() {
        assertEquals(listOf("network", "gps"), LocationProviderPolicy.allowedProviders(true, setOf("network", "gps")))
        assertEquals(listOf("gps"), LocationProviderPolicy.allowedProviders(true, setOf("gps")))
        assertTrue(LocationProviderPolicy.allowedProviders(true, emptySet()).isEmpty())
    }
}
