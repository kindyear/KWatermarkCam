package cn.kindyear.kwatermarkcam.core.location

import android.location.LocationManager

/** Coarse permission can use the network provider; GPS requires fine permission. */
object LocationProviderPolicy {
    fun allowedProviders(finePermission: Boolean, enabled: Set<String>): List<String> = buildList {
        if (LocationManager.NETWORK_PROVIDER in enabled) add(LocationManager.NETWORK_PROVIDER)
        if (finePermission && LocationManager.GPS_PROVIDER in enabled) add(LocationManager.GPS_PROVIDER)
    }
}
