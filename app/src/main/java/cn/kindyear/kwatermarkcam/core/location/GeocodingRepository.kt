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
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull()?.let(::nearbyName))
                    }
                    override fun onError(errorMessage: String?) { if (continuation.isActive) continuation.resume(null) }
                })
            } else legacyAddress(latitude, longitude)
        }
    }
    // Geocoder exposes a feature/locality, not a reliable POI search. Prefer the nearby
    // street/place label; fall back to its address when no named feature is available.
    private fun nearbyName(address: Address): String? {
        val feature = address.featureName?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) }
        val parts = listOfNotNull(address.subLocality, address.thoroughfare, feature).distinct()
        return parts.joinToString(" · ").takeIf { it.isNotBlank() } ?: address.getAddressLine(0)
    }
    @Suppress("DEPRECATION")
    private fun legacyAddress(lat: Double, lon: Double) = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.let(::nearbyName)
}
