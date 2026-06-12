package com.example.lcb.app

import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.lcb.app.databinding.LayoutLiveLocationBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import java.io.IOException
import java.util.Locale
import kotlin.concurrent.thread

class LiveLocationActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: LayoutLiveLocationBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null
    private var currentMarker: Marker? = null
    private var hasCreatedMap = false
    private var pendingInitialLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = LayoutLiveLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        binding.liveLocationToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.liveLocationToolbar.setNavigationOnClickListener {
            finish()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        binding.recenterButton.setOnClickListener {
            loadCurrentLocation(animateCamera = true)
        }

        ensureLocationPermission()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            uiSettings.isMapToolbarEnabled = false
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isCompassEnabled = false
            uiSettings.isMyLocationButtonEnabled = false
            setPadding(0, 124.dp, 0, 108.dp)
            setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
                override fun getInfoWindow(marker: Marker): View {
                    return createLocationInfoWindow(marker)
                }

                override fun getInfoContents(marker: Marker): View? = null
            })
        }
        pendingInitialLocation?.let { location ->
            renderLocation(location, animateCamera = false)
        } ?: revealMap()
    }

    private fun ensureLocationPermission() {
        if (hasLocationPermission()) {
            loadInitialLocation()
            return
        }

        showLocationToast(R.string.live_location_permission_needed)
        XXPermissions.with(this)
            .permission(coarseLocationPermission())
            .permission(fineLocationPermission())
            .request { _, deniedList ->
                if (deniedList.isEmpty() || hasLocationPermission()) {
                    loadInitialLocation()
                } else {
                    showLocationToast(R.string.live_location_permission_denied)
                    createMap(initialLocation = null)
                }
            }
    }

    private fun enableMyLocationLayer() {
        try {
            googleMap?.isMyLocationEnabled = hasLocationPermission()
        } catch (_: SecurityException) {
            googleMap?.isMyLocationEnabled = false
        }
    }

    private fun loadInitialLocation() {
        if (!hasLocationPermission()) {
            ensureLocationPermission()
            return
        }

        binding.mapLoadingOverlay.visibility = View.VISIBLE
        binding.liveLocationMapContainer.alpha = 0f
        showLocationToast(R.string.live_location_locating)
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        createMap(initialLocation = location)
                    } else {
                        requestFreshInitialLocation()
                    }
                }
                .addOnFailureListener {
                    requestFreshInitialLocation()
                }
        } catch (_: SecurityException) {
            showLocationToast(R.string.live_location_permission_denied)
            createMap(initialLocation = null)
        }
    }

    private fun requestFreshInitialLocation() {
        try {
            fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        createMap(initialLocation = location)
                    } else {
                        showLocationToast(R.string.live_location_unavailable)
                        createMap(initialLocation = null)
                    }
                }
                .addOnFailureListener {
                    showLocationToast(R.string.live_location_unavailable)
                    createMap(initialLocation = null)
                }
        } catch (_: SecurityException) {
            showLocationToast(R.string.live_location_permission_denied)
            createMap(initialLocation = null)
        }
    }

    private fun createMap(initialLocation: Location?) {
        pendingInitialLocation = initialLocation
        if (hasCreatedMap) {
            initialLocation?.let { renderLocation(it, animateCamera = false) }
            return
        }

        hasCreatedMap = true
        val options = GoogleMapOptions()
            .mapToolbarEnabled(false)
            .zoomControlsEnabled(false)
            .compassEnabled(false)

        initialLocation?.let { location ->
            options.camera(
                CameraPosition.fromLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    DEFAULT_LOCATION_ZOOM
                )
            )
        }

        val mapFragment = SupportMapFragment.newInstance(options)
        supportFragmentManager.beginTransaction()
            .replace(R.id.liveLocationMapContainer, mapFragment)
            .commitNow()
        mapFragment.getMapAsync(this)
    }

    private fun loadCurrentLocation(animateCamera: Boolean) {
        if (!hasLocationPermission()) {
            ensureLocationPermission()
            return
        }

        showLocationToast(R.string.live_location_locating)
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        renderLocation(location, animateCamera)
                    } else {
                        requestFreshLocation(animateCamera)
                    }
                }
                .addOnFailureListener {
                    requestFreshLocation(animateCamera)
                }
        } catch (_: SecurityException) {
            showLocationToast(R.string.live_location_permission_denied)
        }
    }

    private fun requestFreshLocation(animateCamera: Boolean) {
        try {
            fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        renderLocation(location, animateCamera)
                    } else {
                        showLocationToast(R.string.live_location_unavailable)
                    }
                }
                .addOnFailureListener {
                    showLocationToast(R.string.live_location_unavailable)
                }
        } catch (_: SecurityException) {
            showLocationToast(R.string.live_location_permission_denied)
        }
    }

    private fun renderLocation(location: Location, animateCamera: Boolean) {
        pendingInitialLocation = location
        val map = googleMap ?: return
        val currentLatLng = LatLng(location.latitude, location.longitude)
        val coordinates = coordinatesText(location)
        currentMarker?.remove()
        currentMarker = map.addMarker(
            MarkerOptions()
                .position(currentLatLng)
                .title(getString(R.string.live_location_you_are_at))
                .snippet(coordinates)
        )?.also { marker ->
            marker.showInfoWindow()
        }
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(currentLatLng, DEFAULT_LOCATION_ZOOM)
        if (animateCamera) {
            map.animateCamera(cameraUpdate)
        } else {
            map.moveCamera(cameraUpdate)
        }
        revealMap()
        resolveAddress(location)
    }

    private fun revealMap() {
        binding.mapLoadingOverlay.visibility = View.GONE
        binding.liveLocationMapContainer.animate()
            .alpha(1f)
            .setDuration(160L)
            .start()
    }

    private fun resolveAddress(location: Location) {
        thread(name = "live-location-geocoder") {
            val resolvedAddress = try {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }

            if (!resolvedAddress.isNullOrBlank()) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        currentMarker?.snippet = resolvedAddress
                        if (currentMarker?.isInfoWindowShown == true) {
                            currentMarker?.showInfoWindow()
                        }
                    }
                }
            }
        }
    }

    private fun createLocationInfoWindow(marker: Marker): View {
        val infoWindow = layoutInflater.inflate(R.layout.layout_current_location_info_window, null)
        infoWindow.findViewById<TextView>(R.id.infoWindowTitle).text =
            marker.title ?: getString(R.string.live_location_you_are_at)
        infoWindow.findViewById<TextView>(R.id.infoWindowAddress).text =
            marker.snippet ?: getString(R.string.live_location_locating)
        return infoWindow
    }

    private fun showLocationToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun coordinatesText(location: Location): String {
        return String.format(
            Locale.US,
            "%.6f, %.6f",
            location.latitude,
            location.longitude
        )
    }

    private fun hasLocationPermission(): Boolean {
        return XXPermissions.isGrantedPermission(this, coarseLocationPermission()) ||
            XXPermissions.isGrantedPermission(this, fineLocationPermission())
    }

    private fun coarseLocationPermission(): IPermission =
        PermissionLists.getAccessCoarseLocationPermission()

    private fun fineLocationPermission(): IPermission =
        PermissionLists.getAccessFineLocationPermission()
}

private val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

private const val DEFAULT_LOCATION_ZOOM = 16f
