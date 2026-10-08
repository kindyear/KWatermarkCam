package cn.kindyear.kwatermarkcam.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import cn.kindyear.kwatermarkcam.domain.model.LocationSnapshot
import cn.kindyear.kwatermarkcam.domain.model.LocationStatus
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private val Context.locationCache by preferencesDataStore("location_cache")
/** Foreground-only location stream. Collection cancellation unregisters every listener. */
@Singleton
class LocationSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val geocoder: GeocodingRepository,
) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val cache = context.locationCache
    private val cachedMaxAge = 24 * 60 * 60 * 1000L
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Stale locations remain explicitly labelled CACHED, never as a fresh success. */
    fun observe(): Flow<LocationSnapshot> = flow {
        if (!hasPermission()) { emit(LocationSnapshot(LocationStatus.PERMISSION_REQUIRED)); return@flow }
        if (!manager.isLocationEnabled) { emit(LocationSnapshot(LocationStatus.SERVICES_OFF)); return@flow }
        emit(LocationSnapshot(LocationStatus.ACQUIRING))
        val stored = readCache()
        if (stored != null) emit(stored)
        coroutineScope {
            val output = MutableSharedFlow<LocationSnapshot>(extraBufferCapacity = 8)
            val timeout = launch { delay(20_000); output.emit(stored?.copy(status = LocationStatus.CACHED) ?: LocationSnapshot(LocationStatus.TIMEOUT)) }
            val updater = launch {
                rawLocations().collectLatest { location ->
                    val age = System.currentTimeMillis() - location.time
                    if (age !in 0..cachedMaxAge || !location.hasAccuracy()) return@collectLatest
                    val fresh = age < 120_000 && location.accuracy <= 150f
                    if (fresh) timeout.cancel()
                    var snapshot = LocationSnapshot(if (fresh) LocationStatus.SUCCESS else LocationStatus.CACHED,
                        location.latitude, location.longitude, measuredAt = location.time, accuracy = location.accuracy)
                    output.emit(snapshot)
                    val address = try { geocoder.address(location.latitude, location.longitude) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { Log.w("LocationSource", "Reverse geocoding failed", e); null }
                    snapshot = snapshot.copy(address = address, status = when { !fresh -> LocationStatus.CACHED; address == null -> LocationStatus.GEOCODING_FAILED; else -> LocationStatus.SUCCESS })
                    output.emit(snapshot)
                    writeCache(snapshot)
                }
            }
            try { output.collect { emit(it) } } finally { updater.cancel(); timeout.cancel() }
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.w("LocationSource", "Location request failed", error)
        emit(LocationSnapshot(if (error is SecurityException) LocationStatus.PERMISSION_REQUIRED else if (!manager.isLocationEnabled) LocationStatus.SERVICES_OFF else LocationStatus.FAILED))
    }

    @Suppress("MissingPermission")
    private fun rawLocations(): Flow<Location> = callbackFlow {
        if (!hasPermission()) { close(SecurityException("Location permission missing")); return@callbackFlow }
        val hasGms = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        if (hasGms) {
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) { result.lastLocation?.let { trySend(it) } }
                override fun onLocationAvailability(availability: LocationAvailability) {
                    if (!manager.isLocationEnabled) close(IllegalStateException("Location disabled"))
                }
            }
            fused.lastLocation.addOnSuccessListener { it?.let { location -> trySend(location) } }
                .addOnFailureListener { Log.w("LocationSource", "Last location unavailable", it) }
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000)
                .setMinUpdateIntervalMillis(30_000).setMinUpdateDistanceMeters(20f).build()
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper()).addOnFailureListener { close(it) }
            awaitClose { fused.removeLocationUpdates(callback).addOnFailureListener { Log.w("LocationSource", "Could not remove listener", it) } }
        } else {
            // Devices without Google Play services remain useful offline with system providers.
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) { trySend(location) }
                override fun onProviderDisabled(provider: String) { if (!manager.isLocationEnabled) close(IllegalStateException("Location disabled")) }
            }
            val finePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val enabled = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter { manager.isProviderEnabled(it) }.toSet()
            val providers = LocationProviderPolicy.allowedProviders(finePermission, enabled)
            try {
                providers.forEach { provider ->
                    manager.getLastKnownLocation(provider)?.let { trySend(it) }
                    manager.requestLocationUpdates(provider, 60_000, 20f, listener, Looper.getMainLooper())
                }
                awaitClose { }
            } finally {
                // Also unregister if permission is revoked midway through provider registration.
                try { manager.removeUpdates(listener) }
                catch (e: Exception) { Log.w("LocationSource", "Could not remove system location listener", e) }
            }
        }
    }
    private suspend fun readCache(): LocationSnapshot? {
        val p = cache.data.first()
        val time = p[longPreferencesKey("time")] ?: return null
        if (System.currentTimeMillis() - time !in 0..cachedMaxAge) return null
        val lat = p[doublePreferencesKey("lat")] ?: return null
        val lon = p[doublePreferencesKey("lon")] ?: return null
        return LocationSnapshot(LocationStatus.CACHED, lat, lon, p[stringPreferencesKey("address")], time, p[floatPreferencesKey("accuracy")])
    }
    private suspend fun writeCache(s: LocationSnapshot) { cache.edit { p ->
        p[doublePreferencesKey("lat")] = requireNotNull(s.latitude); p[doublePreferencesKey("lon")] = requireNotNull(s.longitude)
        p[longPreferencesKey("time")] = requireNotNull(s.measuredAt); p[floatPreferencesKey("accuracy")] = s.accuracy ?: 0f
        if (s.address == null) p.remove(stringPreferencesKey("address")) else p[stringPreferencesKey("address")] = s.address
    } }
}
