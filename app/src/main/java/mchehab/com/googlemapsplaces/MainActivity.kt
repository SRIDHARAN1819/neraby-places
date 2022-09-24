package mchehab.com.googlemapsplaces

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.support.v4.content.PermissionChecker
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.ClusterManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayList
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback, LocationResultListener,
        GoogleApiClient.OnConnectionFailedListener {

    private val ACTIVITY_REQUEST_CODE = 1000
    private val PERMISSION_REQUEST_CODE = 1000
    private val ZOOM_LEVEL = 15.0f

    private lateinit var googleMap: GoogleMap
    private lateinit var locationHandler: LocationHandler

    private val API_KEY = "YOUR_API_KEY_HERE"
    private val PLACES_API_REQUEST = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?key=$API_KEY"

    private var location: Location? = null
    private var nextToken = ""
    private var requestCount = 0
    private val MAX_REQUEST_COUNT = 3

    private lateinit var clusterManager: ClusterManager<MarkerClusterItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)        
            
        locationHandler = LocationHandler(this, this, ACTIVITY_REQUEST_CODE, PERMISSION_REQUEST_CODE)
        locationHandler.getUserLocation()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.googleMap) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ACTIVITY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_CANCELED) {
                AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage("Please enable location to use the app")
                        .setPositiveButton("Enable") { dialog, _ ->
                            locationHandler.getUserLocation()
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                        .setCancelable(false)
                        .create()
                        .show()
            } else {
                locationHandler.getUserLocation()
            }
        } else if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                val place = PlacePicker.getPlace(this, data)
                val marker = MarkerOptions().position(place.latLng).title(place.name.toString())
                googleMap.addMarker(marker)
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(place.latLng, ZOOM_LEVEL))
            }
        }
    }

    @SuppressLint("NewApi")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            var isPermissionGranted = true
            for (i in 0 until permissions.size) {
                if (grantResults[i] != PermissionChecker.PERMISSION_GRANTED) {
                    isPermissionGranted = false
                    break
                }
            }
            if (isPermissionGranted) {
                locationHandler.getUserLocation()
            } else {
                if (shouldShowRequestPermissionRationale(permissions[0]) && shouldShowRequestPermissionRationale(permissions[1])) {
                    locationHandler.getUserLocation()
                } else {
                    AlertDialog.Builder(this)
                            .setTitle("Error")
                            .setMessage("Please go to settings page to enable location permission")
                            .setPositiveButton("Go to settings") { dialog, which ->
                                val intent = Intent()
                                intent.action = ACTION_APPLICATION_DETAILS_SETTINGS
                                val uri = Uri.fromParts("package", packageName, null)
                                intent.data = uri
                                startActivity(intent)
                            }.setNegativeButton("Cancel") { dialog, which -> dialog.dismiss() }
                            .create()
                            .show()
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        clusterManager = ClusterManager(this, googleMap)
        clusterManager.renderer = MarkerClusterRenderer(this, googleMap, clusterManager)
        googleMap.setOnCameraIdleListener(clusterManager)
        googleMap.setOnMarkerClickListener(clusterManager)
        setClusterClickListener()
    }

    private fun setClusterClickListener() {
        clusterManager.setOnClusterClickListener { cluster ->
            val list = ArrayList<String>()
            cluster.items.forEach { list.add(it.title) }
            ListViewDialog(this@MainActivity, list).showDialog()
            true
        }
    }

    @SuppressLint("MissingPermission")
    override fun getLocation(location: Location) {
        this.location = location
        val latLng = LatLng(location.latitude, location.longitude)
        PlaceRequest().execute(PLACES_API_REQUEST + "&location=${latLng.latitude},${latLng.longitude}&radius=500")
        progressBar.visibility = View.VISIBLE
        googleMap.isMyLocationEnabled = true
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, ZOOM_LEVEL))
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {}

    private inner class PlaceRequest : AsyncTask<String, Int, JSONArray>() {

        override fun doInBackground(vararg params: String): JSONArray {
            val url = URL(params[0])
            val httpURLConnection = url.openConnection() as HttpURLConnection
            httpURLConnection.requestMethod = "GET"
            httpURLConnection.connect()

            val result = httpURLConnection.inputStream.bufferedReader().readText()
            val jsonObject = JSONObject(result)
            nextToken = if (jsonObject.has("next_page_token"))
                jsonObject.getString("next_page_token")
            else ""
            return jsonObject.getJSONArray("results")
        }

        override fun onPostExecute(result: JSONArray) {
            super.onPostExecute(result)
            progressBar.visibility = View.GONE
            requestCount++
            for (i in 0 until result.length()) {
                val jsonObject = result.getJSONObject(i)
                val name = jsonObject.getString("name")
                val location = jsonObject.getJSONObject("geometry").getJSONObject("location")
                val latLng = LatLng(location.getDouble("lat"), location.getDouble("lng"))
                val markerClusterItem = MarkerClusterItem(latLng, name)
                clusterManager.addItem(markerClusterItem)
            }
            clusterManager.cluster()
            if (requestCount < MAX_REQUEST_COUNT && nextToken.isNotEmpty()) {
                progressBar.visibility = View.VISIBLE
                val url = "$PLACES_API_REQUEST&pagetoken=$nextToken"
                Handler().postDelayed({
                    PlaceRequest().execute(url)
                }, 2000)
            }
        }
    }
}
