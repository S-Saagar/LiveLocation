package com.example.sc_96.fosside;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.location.LocationManager.GPS_PROVIDER;
import static com.example.sc_96.fosside.MapsUtils.animateMarker;
import static com.example.sc_96.fosside.TrackerService.BROADCAST_ACTION_GPS_STATUS;
import static com.example.sc_96.fosside.TrackerService.BROADCAST_ACTION_LOCATION;
import static com.example.sc_96.fosside.TrackerService.EXTRA_GPS_STATUS;
import static com.example.sc_96.fosside.TrackerService.EXTRA_LOCATION;
import static com.example.sc_96.fosside.TrackerService.EXTRA_PROVIDER;

public class MainActivity extends FragmentActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, OnMapReadyCallback {

    private static final int PERMISSIONS_REQUEST = 1;
    private static final String TAG = MainActivity.class.getSimpleName();
    private GoogleMap mMap;
    private Marker marker;
    private boolean isMoving;
    private String provider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        int permission = ContextCompat.checkSelfPermission(this,
                ACCESS_FINE_LOCATION);
        if (permission == PERMISSION_GRANTED) {
            GPSEnable();
            startTrackerService();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST);
        }
    }

    private void startTrackerService() {
        startService(new Intent(this, TrackerService.class));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[]
            grantResults) {
        if (requestCode == PERMISSIONS_REQUEST && grantResults.length == 1
                && grantResults[0] == PERMISSION_GRANTED) {
            GPSEnable();
            startTrackerService();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equalsIgnoreCase(BROADCAST_ACTION_LOCATION)) {
                Location location = intent.getParcelableExtra(EXTRA_LOCATION);
                provider = intent.getStringExtra(EXTRA_PROVIDER);
                ((TextView) findViewById(R.id.tv_provider)).setText(provider);
                if (mMap != null) {
                    LatLng live_location = new LatLng(location.getLatitude(), location.getLongitude());
                    if (marker == null) {
                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(live_location)
                                .title("Marker is here!")
//                                .icon(BitmapDescriptorFactory.fromBitmap(bitmapMarker(MainActivity.this, R.drawable.ic_car)))
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car));
                        marker = mMap.addMarker(markerOptions);
                        marker.setRotation(location.getBearing() + 180);

                        isMoving = true;
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(live_location, 18), 3000, callBack);
                    } else {
                        if (!isMoving) {
                            isMoving = true;
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(live_location, 18), 500, callBack);
                        }
                        animateMarker(location, marker);
                    }
                }
            } else if (action != null && action.equalsIgnoreCase(BROADCAST_ACTION_GPS_STATUS)) {
                Toast.makeText(context, "You have " + (intent.getBooleanExtra(EXTRA_GPS_STATUS, false) ? "enable" : "disabled") + " GPS", Toast.LENGTH_SHORT).show();
                GPSEnable();
            }
        }
    };

    private GoogleMap.CancelableCallback callBack = new GoogleMap.CancelableCallback() {
        @Override
        public void onFinish() {
            isMoving = false;
        }

        @Override
        public void onCancel() {

        }
    };

    private void registerReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BROADCAST_ACTION_LOCATION);
        intentFilter.addAction(BROADCAST_ACTION_GPS_STATUS);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter);
    }

    private void unregisterReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver();
    }

    @Override
    protected void onPause() {
        unregisterReceiver();
        super.onPause();
    }

    private void GPSEnable() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (lm.isProviderEnabled(GPS_PROVIDER)) return;

        LocationRequest mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(500)
                .setFastestInterval(500);

        LocationSettingsRequest.Builder settingsBuilder = new LocationSettingsRequest
                .Builder()
                .addLocationRequest(mLocationRequest);

        settingsBuilder.setAlwaysShow(true);

        Task<LocationSettingsResponse> result = LocationServices
                .getSettingsClient(this)
                .checkLocationSettings(settingsBuilder.build());

        result.addOnSuccessListener(this, onSuccess())
                .addOnCompleteListener(this, onCompleted())
                .addOnCanceledListener(this, onCanceled());
    }

    private OnCanceledListener onCanceled() {
        return () -> {
            Log.d(TAG, "onCanceled: ");
        };
    }

    private OnCompleteListener<LocationSettingsResponse> onCompleted() {
        return task -> {
            Log.d(TAG, "onCompleted: " + task.isSuccessful());
            try {
                task.getResult(ApiException.class);
            } catch (ApiException ex) {
                switch (ex.getStatusCode()) {
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            ResolvableApiException resolvableApiException =
                                    (ResolvableApiException) ex;
                            resolvableApiException
                                    .startResolutionForResult(MainActivity.this, 1000);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:

                        break;
                }
            }
        };
    }

    private OnSuccessListener<? super LocationSettingsResponse> onSuccess() {
        return (OnSuccessListener<LocationSettingsResponse>) locationSettingsResponse -> {
            Log.d(TAG, "onSuccess: ");
        };
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }
}
