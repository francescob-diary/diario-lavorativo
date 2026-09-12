package it.diario.lavorativo.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import it.diario.lavorativo.domain.model.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Implementazione basata su LocationManager, la classe di sistema.
 *
 * Scelta: niente Google Play Services. Il FusedLocationProvider e' piu' preciso,
 * ma aggiunge una dipendenza pesante e non funziona sui telefoni senza servizi
 * Google. Per riconoscere un cantiere entro cento metri il GPS di sistema basta.
 * Se in futuro servira' piu' precisione si sostituisce solo questa classe.
 */
class AndroidLocationProvider(context: Context) : LocationProvider {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    override fun isLocationEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(timeoutMillis: Long): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionMissing
        if (!isLocationEnabled()) return LocationResult.LocationDisabled

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            else -> LocationManager.NETWORK_PROVIDER
        }

        // Se c'e' un fix recente lo usiamo subito: in cantiere si aspetta volentieri
        // zero secondi invece di quindici.
        lastKnownFresh()?.let { return LocationResult.Success(it.toGeoPoint()) }

        val located = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Richiesto dalle versioni vecchie di Android")
                    override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) = Unit

                    override fun onProviderDisabled(p: String) {
                        locationManager.removeUpdates(this)
                        if (cont.isActive) cont.resume(null)
                    }
                }
                try {
                    locationManager.requestLocationUpdates(
                        provider,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
                cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
            }
        }

        return when {
            located != null -> LocationResult.Success(located.toGeoPoint())
            else -> LocationResult.Timeout
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownFresh(): Location? {
        val now = System.currentTimeMillis()
        return locationManager.allProviders
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time <= MAX_FIX_AGE_MILLIS }
            .minByOrNull { it.accuracy }
    }

    private fun Location.toGeoPoint(): GeoPoint = GeoPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null
    )

    private companion object {
        /** Un fix piu' vecchio di due minuti non descrive piu' dove sei. */
        const val MAX_FIX_AGE_MILLIS = 2 * 60 * 1000L
    }
}
