package mchehab.com.java;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, LocationResultListener {

    private static final int ACTIVITY_RQEUEST_CODE = 1000;
    private static final int PERMISSION_REQUEST_CODE = 1000;
    private static final float ZOOM_LEVEL = 15.0f;
    private static final int REQUEST_LIMIT = 3;

    private GoogleMap googleMap;
    private ClusterManager<MarkerClusterItem> clusterManager;

    private LocationHandler locationHandler;

    private final String API_KEY = "YOUR_API_KEY_HERE";
    private final String PLACES_REQUEST = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?&key=" + API_KEY;

    private int requestCount;
    private String nextPageToken;

    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressBar = findViewById(R.id.progressBar);

        locationHandler = new LocationHandler(this, this, ACTIVITY_RQEUEST_CODE, PERMISSION_REQUEST_CODE);

        SupportMapFragment supportMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.googleMap);
        supportMapFragment.getMapAsync(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_RQEUEST_CODE) {
            if (resultCode == RESULT_OK) {
                locationHandler.getUserLocation();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage("Please enable location")
                        .setPositiveButton("Ok", (dialog, which) -> {
                            locationHandler.getUserLocation();
                            dialog.dismiss();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .setCancelable(false)
                        .create()
                        .show();
            }
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean isPermissionGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PermissionChecker.PERMISSION_GRANTED) {
                    isPermissionGranted = false;
                    break;
                }
            }
            if (isPermissionGranted){
                locationHandler.getUserLocation();
            }else{
                if (shouldShowRequestPermissionRationale(permissions[0]) && shouldShowRequestPermissionRationale(permissions[1])) {
                    locationHandler.getUserLocation();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Error")
                            .setMessage("Please go to settings page to enable location permission")
                            .setPositiveButton("Go to Settings", (dialog, which) -> {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            }).setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                            .setCancelable(false)
                            .create()
                            .show();
                }
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        clusterManager = new ClusterManager<>(this, googleMap);
        clusterManager.setRenderer(new MarkerClusterRenderer<>(this, googleMap, clusterManager));
        setClusterClickListener();
        googleMap.setOnMarkerClickListener(clusterManager);
        googleMap.setOnCameraIdleListener(clusterManager);
        locationHandler.getUserLocation();
    }

    private void setClusterClickListener(){
        clusterManager.setOnClusterClickListener(cluster -> {
            Collection<MarkerClusterItem> clusterItems = cluster.getItems();
            List<String> list = new ArrayList<>();
            for (MarkerClusterItem markerClusterItem : clusterItems){
                list.add(markerClusterItem.getTitle());
            }
            new ListViewDialog(MainActivity.this, list).showDialog();
            return true;
        });
    }

    @SuppressLint("MissingPermission")
    @Override
    public void getLocation(Location location) {
        googleMap.setMyLocationEnabled(true);
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, ZOOM_LEVEL));
        progressBar.setVisibility(View.VISIBLE);
        new PlaceRequest().execute(PLACES_REQUEST + "&radius=500&location=" + latLng.latitude + "," + latLng.longitude);
    }

    private class PlaceRequest extends AsyncTask<String, Integer, JSONArray> {

        @Override
        protected JSONArray doInBackground(String... params) {
            try {
                URL url = new URL(params[0]);

                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setRequestMethod("GET");
                httpURLConnection.connect();

                String line;
                StringBuilder stringBuilder = new StringBuilder("");
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                JSONObject jsonObject = new JSONObject(stringBuilder.toString());
                if (jsonObject.has("next_page_token")) {
                    nextPageToken = jsonObject.getString("next_page_token");
                } else {
                    nextPageToken = "";
                }
                return jsonObject.getJSONArray("results");
            } catch (Exception e) {
                e.printStackTrace();
            }
            return new JSONArray();
        }

        @Override
        protected void onPostExecute(JSONArray jsonArray) {
            progressBar.setVisibility(View.GONE);
            requestCount++;
            try {
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    JSONObject location = jsonObject.getJSONObject("geometry").getJSONObject("location");
                    String name = jsonObject.getString("name");
                    LatLng latLng = new LatLng(location.getDouble("lat"), location.getDouble("lng"));
                    MarkerClusterItem markerClusterItem = new MarkerClusterItem(latLng, name);
                    clusterManager.addItem(markerClusterItem);
                }
                clusterManager.cluster();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (requestCount < REQUEST_LIMIT && !nextPageToken.equals("")) {
                progressBar.setVisibility(View.VISIBLE);
                String url = PLACES_REQUEST + "&pagetoken=" + nextPageToken;
                new Handler().postDelayed(() -> {
                    new PlaceRequest().execute(url);
                }, 2000);
            }
        }
    }
}