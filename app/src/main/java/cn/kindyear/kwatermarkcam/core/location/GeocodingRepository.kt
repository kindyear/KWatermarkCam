package cn.kindyear.kwatermarkcam.core.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Replace this interface to integrate a different reverse geocoder without changing camera state. */
interface GeocodingRepository { suspend fun address(latitude: Double, longitude: Double): String? }
@Singleton
class SystemGeocodingRepository @Inject constructor(@ApplicationContext context: Context) : GeocodingRepository {
    private val geocoder = Geocoder(context, Locale.getDefault())
    override suspend fun address(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        withTimeoutOrNull(8_000) {
            if (Build.VERSION.SDK_INT >= 33) suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull()?.getAddressLine(0))
                    }
                    override fun onError(errorMessage: String?) { if (continuation.isActive) continuation.resume(null) }
                })
            } else legacyAddress(latitude, longitude)
        }
    }
    @Suppress("DEPRECATION")
    private fun legacyAddress(lat: Double, lon: Double) = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.getAddressLine(0)
}
