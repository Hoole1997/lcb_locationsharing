package com.example.lcb.app

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lcb.app.databinding.LayoutRealtimeSharingBinding
import com.example.lcb.app.pairing.LocationDto
import com.example.lcb.app.pairing.PairingDto
import com.example.lcb.app.pairing.PairingRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class RealtimeSharingActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: LayoutRealtimeSharingBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<androidx.constraintlayout.widget.ConstraintLayout>
    private var googleMap: GoogleMap? = null
    private var selfMarker: Marker? = null
    private val friendMarkersByDeviceId = mutableMapOf<String, Marker>()
    private var hasCreatedMap = false
    private var hasEnteredResume = false
    private var pendingInitialLocation: Location? = null
    private var latestPairings: List<PairingDto> = emptyList()
    private val sharedFriendAdapter = SharedFriendAdapter()
    private var locationUploadJob: Job? = null
    private var pairingsPollingJob: Job? = null
    private var friendActionPopup: PopupWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = LayoutRealtimeSharingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        binding.realtimeSharingToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.realtimeSharingToolbar.setNavigationOnClickListener {
            finish()
        }

        showEmptyState()
        configureSharedListSheet()
        binding.sharedFriendsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@RealtimeSharingActivity)
            adapter = sharedFriendAdapter
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding.goToAddButton.setOnClickListener {
            startActivity(Intent(this, AddFriendCodeActivity::class.java))
        }

        ensureLocationPermission()
        loadSharedFriends()
    }

    private fun configureSharedListSheet() {
        binding.sharedListSheet.visibility = View.VISIBLE
        binding.sharedListSheet.bringToFront()
        binding.realtimeSharingToolbar.bringToFront()
        bottomSheetBehavior = BottomSheetBehavior.from(binding.sharedListSheet).apply {
            isHideable = false
            isDraggable = true
            isFitToContents = false
            peekHeight = SHARED_LIST_PEEK_HEIGHT_DP.dp
            expandedOffset = 104.dp
            state = BottomSheetBehavior.STATE_COLLAPSED
        }
        binding.sharedListSheet.post {
            binding.sharedListSheet.visibility = View.VISIBLE
            binding.sharedListSheet.bringToFront()
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            updateMapPadding()
        }
    }

    override fun onStart() {
        super.onStart()
        if (::binding.isInitialized) {
            startRealtimeSync()
        }
    }

    override fun onStop() {
        friendActionPopup?.dismiss()
        stopRealtimeSync()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return

        if (hasEnteredResume) {
            loadCurrentLocation(animateCamera = false)
            loadSharedFriends()
        } else {
            hasEnteredResume = true
        }
    }

    private fun startRealtimeSync() {
        if (locationUploadJob?.isActive != true) {
            locationUploadJob = lifecycleScope.launch {
                while (isActive) {
                    uploadCurrentLocationOnce()
                    delay(LOCATION_UPLOAD_INTERVAL_MS)
                }
            }
        }

        if (pairingsPollingJob?.isActive != true) {
            pairingsPollingJob = lifecycleScope.launch {
                while (isActive) {
                    refreshSharedFriends(showError = false)
                    delay(PAIRINGS_POLL_INTERVAL_MS)
                }
            }
        }
    }

    private fun stopRealtimeSync() {
        locationUploadJob?.cancel()
        locationUploadJob = null
        pairingsPollingJob?.cancel()
        pairingsPollingJob = null
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            uiSettings.isMapToolbarEnabled = false
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isCompassEnabled = false
            uiSettings.isMyLocationButtonEnabled = false
            setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
                override fun getInfoWindow(marker: Marker): View {
                    return createLocationInfoWindow(marker)
                }

                override fun getInfoContents(marker: Marker): View? = null
            })
        }
        updateMapPadding()
        enableMyLocationLayer()
        pendingInitialLocation?.let { location ->
            renderCurrentLocation(location, animateCamera = false, moveCamera = true)
        } ?: revealMap()
        renderFriendMarkers()
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

    private fun loadInitialLocation() {
        if (!hasLocationPermission()) {
            ensureLocationPermission()
            return
        }

        binding.mapLoadingOverlay.visibility = View.VISIBLE
        binding.realtimeMapContainer.alpha = 0f
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
            initialLocation?.let {
                renderCurrentLocation(it, animateCamera = false, moveCamera = true)
            }
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
                    DEFAULT_REALTIME_ZOOM
                )
            )
        }

        val mapFragment = SupportMapFragment.newInstance(options)
        supportFragmentManager.beginTransaction()
            .replace(R.id.realtimeMapContainer, mapFragment)
            .commitNow()
        mapFragment.getMapAsync(this)
    }

    private fun loadCurrentLocation(animateCamera: Boolean) {
        if (!hasLocationPermission()) {
            ensureLocationPermission()
            return
        }

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        renderCurrentLocation(location, animateCamera, moveCamera = true)
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
                        renderCurrentLocation(location, animateCamera, moveCamera = true)
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

    private fun enableMyLocationLayer() {
        try {
            googleMap?.isMyLocationEnabled = hasLocationPermission()
        } catch (_: SecurityException) {
            googleMap?.isMyLocationEnabled = false
        }
    }

    private fun renderCurrentLocation(
        location: Location,
        animateCamera: Boolean,
        moveCamera: Boolean,
        resolveAddress: Boolean = true
    ) {
        pendingInitialLocation = location
        val map = googleMap ?: return
        val currentLatLng = LatLng(location.latitude, location.longitude)
        val markerInfo = RealtimeMarkerInfo(
            title = getString(R.string.realtime_you_are_at),
            address = coordinatesText(location),
            bubbleBackground = R.drawable.bg_realtime_bubble_self,
            pointerBackground = R.drawable.bg_live_location_bubble_pointer
        )

        val marker = selfMarker
        if (marker == null) {
            selfMarker = map.addMarker(
                markerOptions(
                    position = currentLatLng,
                    title = markerInfo.title,
                    address = markerInfo.address,
                    iconRes = R.drawable.ic_realtime_pin_self
                )
            )
        } else {
            marker.position = currentLatLng
            marker.title = markerInfo.title
            marker.snippet = markerInfo.address
        }
        selfMarker?.tag = markerInfo
        selfMarker?.showInfoWindow()

        if (moveCamera) {
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                currentLatLng,
                DEFAULT_REALTIME_ZOOM
            )
            if (animateCamera) {
                map.animateCamera(cameraUpdate)
            } else {
                map.moveCamera(cameraUpdate)
            }
        }
        revealMap()
        if (resolveAddress) {
            resolveSelfAddress(location)
        }
    }

    private fun loadSharedFriends() {
        binding.sharedFriendsRecyclerView.visibility = View.GONE

        lifecycleScope.launch {
            refreshSharedFriends(showError = true)
        }
    }

    private suspend fun refreshSharedFriends(showError: Boolean) {
        runCatching { PairingRepository.listPairings(this@RealtimeSharingActivity) }
            .onSuccess { pairings ->
                latestPairings = pairings
                if (pairings.isEmpty()) {
                    showEmptyState()
                } else {
                    showSharedFriends(pairings)
                }
                renderFriendMarkers()
            }
            .onFailure {
                latestPairings = emptyList()
                showEmptyState()
                clearFriendMarkers()
                if (showError) {
                    Toast.makeText(
                        this@RealtimeSharingActivity,
                        R.string.pairing_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private suspend fun uploadCurrentLocationOnce() {
        if (!hasLocationPermission()) return
        val location = runCatching {
            CurrentLocationUploader.currentDeviceLocation(fusedLocationClient)
        }.getOrNull() ?: return
        renderCurrentLocation(
            location = location,
            animateCamera = false,
            moveCamera = false,
            resolveAddress = false
        )
        val address = CurrentLocationUploader.reverseGeocode(this, location)
        applySelfAddress(address)
        CurrentLocationUploader.uploadKnownLocation(
            context = this@RealtimeSharingActivity,
            location = location,
            address = address
        )
    }

    private fun showEmptyState() {
        sharedFriendAdapter.submitList(emptyList())
        binding.sharedFriendsRecyclerView.visibility = View.GONE
        binding.emptyStateGroup.visibility = View.VISIBLE
    }

    private fun showSharedFriends(pairings: List<PairingDto>) {
        sharedFriendAdapter.submitList(pairings)
        binding.sharedFriendsRecyclerView.visibility = View.VISIBLE
        binding.emptyStateGroup.visibility = View.GONE
    }

    private fun renderFriendMarkers() {
        val map = googleMap ?: return
        clearFriendMarkers()
        latestPairings.forEach { pairing ->
            val location = pairing.latestLocation ?: return@forEach
            val friendName = pairing.displayName()
            val markerInfo = RealtimeMarkerInfo(
                title = getString(R.string.realtime_friend_are_at, friendName),
                address = location.formatAddress(),
                bubbleBackground = R.drawable.bg_realtime_bubble_friend,
                pointerBackground = R.drawable.bg_realtime_bubble_friend
            )
            val marker = map.addMarker(
                markerOptions(
                    position = location.toLatLng(),
                    title = markerInfo.title,
                    address = markerInfo.address,
                    iconRes = R.drawable.ic_realtime_pin_friend
                )
            )
            marker?.tag = markerInfo
            marker?.let {
                friendMarkersByDeviceId[pairing.friendDeviceId] = it
            }
        }
    }

    private fun clearFriendMarkers() {
        friendMarkersByDeviceId.values.forEach(Marker::remove)
        friendMarkersByDeviceId.clear()
    }

    private fun focusFriendLocation(pairing: PairingDto) {
        val location = pairing.latestLocation ?: return
        val map = googleMap ?: return
        val marker = friendMarkersByDeviceId[pairing.friendDeviceId]
        val latLng = location.toLatLng()
        marker?.showInfoWindow()
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_REALTIME_ZOOM))
    }

    private fun markerOptions(
        position: LatLng,
        title: String,
        address: String,
        @DrawableRes iconRes: Int
    ): MarkerOptions {
        val options = MarkerOptions()
            .position(position)
            .anchor(0.5f, 1f)
            .title(title)
            .snippet(address)
        bitmapDescriptorFromVector(iconRes)?.let(options::icon)
        return options
    }

    private fun createLocationInfoWindow(marker: Marker): View {
        val infoWindow = layoutInflater.inflate(R.layout.layout_realtime_location_info_window, null)
        val markerInfo = marker.tag as? RealtimeMarkerInfo
        infoWindow.findViewById<View>(R.id.realtimeInfoBubble).setBackgroundResource(
            markerInfo?.bubbleBackground ?: R.drawable.bg_realtime_bubble_self
        )
        infoWindow.findViewById<View>(R.id.realtimeInfoPointer).setBackgroundResource(
            markerInfo?.pointerBackground ?: R.drawable.bg_live_location_bubble_pointer
        )
        infoWindow.findViewById<TextView>(R.id.realtimeInfoTitle).text =
            markerInfo?.title ?: marker.title ?: getString(R.string.realtime_you_are_at)
        infoWindow.findViewById<TextView>(R.id.realtimeInfoAddress).text =
            markerInfo?.address ?: marker.snippet ?: getString(R.string.live_location_locating)
        return infoWindow
    }

    private fun resolveSelfAddress(location: Location) {
        lifecycleScope.launch {
            applySelfAddress(CurrentLocationUploader.reverseGeocode(this@RealtimeSharingActivity, location))
        }
    }

    private fun applySelfAddress(resolvedAddress: String?) {
        if (resolvedAddress.isNullOrBlank() || isFinishing || isDestroyed) return
        val markerInfo = RealtimeMarkerInfo(
            title = getString(R.string.realtime_you_are_at),
            address = resolvedAddress,
            bubbleBackground = R.drawable.bg_realtime_bubble_self,
            pointerBackground = R.drawable.bg_live_location_bubble_pointer
        )
        selfMarker?.snippet = resolvedAddress
        selfMarker?.tag = markerInfo
        if (selfMarker?.isInfoWindowShown == true) {
            selfMarker?.showInfoWindow()
        }
    }

    private fun updateMapPadding() {
        googleMap?.setPadding(0, 104.dp, 0, 0)
    }

    private fun revealMap() {
        binding.mapLoadingOverlay.visibility = View.GONE
        binding.realtimeMapContainer.animate()
            .alpha(1f)
            .setDuration(160L)
            .start()
    }

    private fun bitmapDescriptorFromVector(@DrawableRes drawableRes: Int): BitmapDescriptor? {
        val drawable = AppCompatResources.getDrawable(this, drawableRes) ?: return null
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 62.dp
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 72.dp
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun LocationDto?.formatAddress(): String {
        return this?.let {
            it.address?.takeIf { address -> address.isNotBlank() }
                ?: getString(R.string.realtime_location_format, it.lat, it.lng)
        } ?: getString(R.string.realtime_location_not_shared)
    }

    private fun LocationDto.toLatLng(): LatLng {
        return LatLng(lat, lng)
    }

    private fun LocationDto.displayTime(): String {
        return (updatedAt ?: recordedAt).displayTime()
    }

    private fun String?.displayTime(): String {
        if (isNullOrBlank()) return "--:--"
        return try {
            OffsetDateTime.parse(this)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: DateTimeParseException) {
            "--:--"
        }
    }

    private fun coordinatesText(location: Location): String {
        return String.format(
            Locale.US,
            "%.6f, %.6f",
            location.latitude,
            location.longitude
        )
    }

    private fun showLocationToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun sharedFriendAddress(pairing: PairingDto): String {
        return pairing.latestLocation?.let { location ->
            getString(
                R.string.realtime_friend_location_summary,
                pairing.displayName(),
                location.formatAddress()
            )
        } ?: getString(R.string.realtime_location_not_shared)
    }

    private fun sharedFriendTime(pairing: PairingDto): String {
        return pairing.latestLocation?.displayTime() ?: "--:--"
    }

    private fun showFriendActionPopup(anchor: View, pairing: PairingDto) {
        friendActionPopup?.dismiss()
        val popupView = layoutInflater.inflate(R.layout.layout_friend_action_popup, null)
        val popup = PopupWindow(
            popupView,
            FRIEND_ACTION_POPUP_WIDTH_DP.dp,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = 10.dp.toFloat()
        }

        popupView.findViewById<TextView>(R.id.editFriendAction).setOnClickListener {
            popup.dismiss()
            startActivity(AddFriendProfileActivity.createEditIntent(this, pairing))
        }
        popupView.findViewById<TextView>(R.id.deleteFriendAction).setOnClickListener {
            popup.dismiss()
            showDeleteFriendDialog(pairing)
        }

        friendActionPopup = popup
        val horizontalOffset = anchor.width - FRIEND_ACTION_POPUP_WIDTH_DP.dp
        popup.showAsDropDown(anchor, horizontalOffset, -anchor.height)
    }

    private fun showDeleteFriendDialog(pairing: PairingDto) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val contentView = layoutInflater.inflate(R.layout.dialog_delete_friend_confirm, null, false)
        contentView.findViewById<TextView>(R.id.deleteConfirmTitle).setText(R.string.friend_delete_title)
        contentView.findViewById<TextView>(R.id.deleteConfirmMessage).text = getString(
            R.string.friend_delete_message,
            pairing.displayName()
        )
        contentView.findViewById<TextView>(R.id.deleteConfirmCancel).setOnClickListener {
            dialog.dismiss()
        }
        contentView.findViewById<TextView>(R.id.deleteConfirmDelete).setOnClickListener {
            dialog.dismiss()
            deleteFriend(pairing)
        }

        dialog.setContentView(contentView)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            decorView.setPadding(0, 0, 0, 0)
            setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun deleteFriend(pairing: PairingDto) {
        lifecycleScope.launch {
            runCatching {
                PairingRepository.deletePairing(
                    context = this@RealtimeSharingActivity,
                    friendDeviceId = pairing.friendDeviceId
                )
            }.onSuccess {
                removePairing(pairing.friendDeviceId)
                Toast.makeText(
                    this@RealtimeSharingActivity,
                    R.string.friend_deleted,
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    this@RealtimeSharingActivity,
                    error.message ?: getString(R.string.pairing_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun replacePairing(updatedPairing: PairingDto) {
        latestPairings = latestPairings.map { pairing ->
            if (pairing.friendDeviceId == updatedPairing.friendDeviceId) {
                updatedPairing
            } else {
                pairing
            }
        }
        showSharedFriends(latestPairings)
        renderFriendMarkers()
    }

    private fun removePairing(friendDeviceId: String) {
        latestPairings = latestPairings.filterNot { it.friendDeviceId == friendDeviceId }
        if (latestPairings.isEmpty()) {
            showEmptyState()
        } else {
            showSharedFriends(latestPairings)
        }
        renderFriendMarkers()
    }

    private fun hasLocationPermission(): Boolean {
        return XXPermissions.isGrantedPermission(this, coarseLocationPermission()) ||
            XXPermissions.isGrantedPermission(this, fineLocationPermission())
    }

    private fun coarseLocationPermission(): IPermission =
        PermissionLists.getAccessCoarseLocationPermission()

    private fun fineLocationPermission(): IPermission =
        PermissionLists.getAccessFineLocationPermission()

    private inner class SharedFriendAdapter :
        RecyclerView.Adapter<SharedFriendAdapter.SharedFriendViewHolder>() {
        private val items = mutableListOf<PairingDto>()

        fun submitList(pairings: List<PairingDto>) {
            items.clear()
            items.addAll(pairings)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SharedFriendViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_realtime_shared_friend, parent, false)
            return SharedFriendViewHolder(view)
        }

        override fun onBindViewHolder(holder: SharedFriendViewHolder, position: Int) {
            holder.bind(items[position], position, isLastItem = position == items.lastIndex)
        }

        override fun getItemCount(): Int = items.size

        private inner class SharedFriendViewHolder(itemView: View) :
            RecyclerView.ViewHolder(itemView) {
            private val avatarView: ImageView = itemView.findViewById(R.id.sharedFriendAvatar)
            private val nameView: TextView = itemView.findViewById(R.id.sharedFriendName)
            private val addressView: TextView = itemView.findViewById(R.id.sharedFriendAddress)
            private val timeView: TextView = itemView.findViewById(R.id.sharedFriendTime)
            private val moreButton: ImageView = itemView.findViewById(R.id.sharedFriendMoreButton)
            private val dividerView: View = itemView.findViewById(R.id.sharedFriendDivider)

            fun bind(pairing: PairingDto, position: Int, isLastItem: Boolean) {
                avatarView.setImageResource(
                    if (pairing.friendGender == "female") {
                        R.drawable.ic_realtime_avatar_female
                    } else {
                        R.drawable.ic_realtime_avatar_male
                    }
                )
                nameView.text = pairing.displayName()
                addressView.text = sharedFriendAddress(pairing)
                timeView.text = sharedFriendTime(pairing)
                dividerView.visibility = if (isLastItem) View.GONE else View.VISIBLE
                itemView.setOnClickListener {
                    focusFriendLocation(pairing)
                }
                moreButton.setOnClickListener {
                    showFriendActionPopup(moreButton, pairing)
                }
            }
        }
    }
}

private fun PairingDto.displayName(): String {
    return friendDisplayName?.takeIf { it.isNotBlank() } ?: friendDeviceName
}

private val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

private const val DEFAULT_REALTIME_ZOOM = 16f
private const val SHARED_LIST_PEEK_HEIGHT_DP = 72
private const val FRIEND_ACTION_POPUP_WIDTH_DP = 136
private const val LOCATION_UPLOAD_INTERVAL_MS = 15_000L
private const val PAIRINGS_POLL_INTERVAL_MS = 10_000L
