package mchehab.com.googlemapsplaces

import android.Manifest
import android.app.Activity
import android.content.Context.LOCATION_SERVICE
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*

class LocationHandler(val activity: Activity, val locationResultListener: LocationResultListener,
                      val activityRequestCode: Int, val permissionRequestCode: Int) {
    init {
        initLocationVariables()
    }

    private lateinit var locationManager: LocationManager
    private lateinit var locationRequest: LocationRequest
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
    private val COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
    private val GRANTED = PackageManager.PERMISSION_GRANTED

    private fun initLocationVariables() {
        locationManager = activity.getSystemService(LOCATION_SERVICE) as LocationManager
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity)
        locationRequest = LocationRequest
                .create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)
                .setFastestInterval(0)
        locationManager = activity.getSystemService(LOCATION_SERVICE) as LocationManager
        initLocationCallBack()
    }

    private fun initLocationCallBack() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)
                locationResultListener.getLocation(locationResult!!.lastLocation)
                fusedLocationProviderClient.removeLocationUpdates(locationCallback)
            }
        }
    }

    private fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun isPermissionGranted(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(activity, FINE_LOCATION) == GRANTED &&
                ContextCompat.checkSelfPermission(activity, COARSE_LOCATION) == GRANTED
    }

    private fun requestPermission(activity: Activity, requestCode: Int) {
        val permissions = arrayOf(FINE_LOCATION, COARSE_LOCATION)
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }

    private fun promptUserToEnableLocation(requestCode: Int) {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        builder.setAlwaysShow(true)
        LocationServices
                .getSettingsClient(activity)
                .checkLocationSettings(builder.build())
                .addOnSuccessListener { locationSettingsResponse -> getLastKnownLocation() }
                .addOnFailureListener { e ->
                    val status = (e as ApiException).statusCode
                    when (status) {
                        LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                            val resolvableApiException = e as ResolvableApiException
                            resolvableApiException.startResolutionForResult(activity, requestCode)
                        } catch (exception: IntentSender.SendIntentException) {
                            exception.printStackTrace()
                        }
                    }
                }
    }

    fun getUserLocation() {
        if (!isGooglePlayServicesAvailable(activity)) {
            return
        }
        if (!isPermissionGranted(activity)) {
            requestPermission(activity, permissionRequestCode)
            return
        }
        if (!isLocationEnabled()) {
            promptUserToEnableLocation(activityRequestCode)
            return
        }
        getLastKnownLocation()
    }

    @SuppressWarnings("MissingPermission")
    private fun getLastKnownLocation() {
        fusedLocationProviderClient.lastLocation.addOnCompleteListener {
            val location = it.result
            if (location == null) {
                fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
            } else {
                locationResultListener.getLocation(location)
            }
        }
    }

    private fun isGooglePlayServicesAvailable(activity: Activity): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val status = googleApiAvailability.isGooglePlayServicesAvailable(activity)
        if (status != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(status)) {
                googleApiAvailability.getErrorDialog(activity, status, 2404).show()
            }
            return false
        }
        return true
    }
}