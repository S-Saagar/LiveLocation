package com.example.sc_96.fosside;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.support.v4.app.NotificationManagerCompat.IMPORTANCE_LOW;

/**
 * Created by sc-96 on 03-Jan-18.
 */

public class TrackerService extends Service {

    public static String EXTRA_LOCATION = "EXTRA_LOCATION";
    public static String EXTRA_PROVIDER = "EXTRA_PROVIDER";
    public static String EXTRA_GPS_STATUS = "EXTRA_GPS_STATUS";

    public static String BROADCAST_ACTION_LOCATION = "LOCATION";
    public static String BROADCAST_ACTION_GPS_STATUS = "GPS_STATUS";

    private static final String TAG = TrackerService.class.getSimpleName();
    private LocationCallback locationCallBack;
    private FusedLocationProviderClient client;
    private NotificationCompat.Builder notification;
    private MyLocationListener locationUpdateListener;
    private LocationManager locationManager;
    //    private float suitableMeterGPS = 250;
    private float suitableMeterGPS = 25;
    //    private float suitableMeterFused = 2500;
    private float suitableMeterFused = 25;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        requestLocationUpdates();
        registerReceiver(broadcastReceiver, new IntentFilter("STOP_THIS"));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
        client.removeLocationUpdates(locationCallBack);
        locationManager.removeUpdates(locationUpdateListener);
    }

    private void createNotification() {
        String CHANNEL_ID = "location_update";
        NotificationChannel channel;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                channel = new NotificationChannel(CHANNEL_ID, "location_update", NotificationManager.IMPORTANCE_LOW);
                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
            }
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        PendingIntent stopthis = PendingIntent.getBroadcast(this, 1000, new Intent("STOP_THIS"), 0);
        NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.bike, "STOP", stopthis);

        notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("You are live now!")
                .setPriority(IMPORTANCE_LOW)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentText("Location updating...")
                .setContentIntent(pendingIntent)
                .addAction(action);

        startForeground(1, notification.build());
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equalsIgnoreCase("STOP_THIS")) {
                stopForeground(true);
                stopSelf();
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void requestLocationUpdates() {
        int permission = ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION);

        LocationRequest request = new LocationRequest();
        request.setInterval(500);
        request.setFastestInterval(500);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        client = LocationServices.getFusedLocationProviderClient(this);

        if (permission == PERMISSION_GRANTED) {
            createNotification();
            // Request location updates and when an update is received, store the location in Firebase
            locationCallBack = onLocationCallBack();
            client.requestLocationUpdates(request, locationCallBack, null);
        }

//--------------------------------------------------------------------------------------------------------//

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (permission == PERMISSION_GRANTED) {
            locationUpdateListener = new MyLocationListener();
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0.01f, locationUpdateListener);
        }
    }

    private LocationCallback onLocationCallBack() {
        locationCallBack = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                location.setAccuracy(5);

                Log.e(TAG, "onReceive Fused: Location");
                Log.d(TAG, "onReceive Fused: Latitude " + location.getLatitude());
                Log.d(TAG, "onReceive Fused: Longitude " + location.getLongitude());
                Log.d(TAG, "onReceive Fused: Accuracy " + location.getAccuracy());
                Log.d(TAG, "onReceive Fused: Bearing " + location.getBearing());

                if (location.hasAccuracy() && location.getAccuracy() <= suitableMeterFused) {
                    // This is your most accurate location.
                    updateNotification(location, "Fused");
                    sendLocalBroadcastOnLocationUpdate(location, "Fused");
                }
            }

            @Override
            public void onLocationAvailability(LocationAvailability locationAvailability) {
                super.onLocationAvailability(locationAvailability);
                Log.e(TAG, "onLocationAvailability: " + locationAvailability.isLocationAvailable());
            }
        };
        return locationCallBack;
    }

    private void sendLocalBroadcastOnLocationUpdate(Location location, String provider) {
        Intent intent = new Intent(BROADCAST_ACTION_LOCATION)
                .putExtra(EXTRA_LOCATION, location)
                .putExtra(EXTRA_PROVIDER, provider);
        LocalBroadcastManager.getInstance(TrackerService.this).sendBroadcast(intent);
    }

    private void sendBroadcastOnGPSStateChange(boolean gpsStatus) {
        Intent intent = new Intent(BROADCAST_ACTION_GPS_STATUS)
                .putExtra(EXTRA_GPS_STATUS, gpsStatus);
        LocalBroadcastManager.getInstance(TrackerService.this).sendBroadcast(intent);
    }

    private void updateNotification(Location location, String text) {
        if (location != null) {
            notification.setContentText(text + " Location: Lat " + location.getLatitude() + ",Long " + location.getLongitude());
        } else {
            notification.setContentText("Location: " + text);
        }
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(1, notification.build());
    }

    private class MyLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            client.removeLocationUpdates(locationCallBack);
            location.setAccuracy(5);

            Log.e(TAG, "onReceive GPS: Location");
            Log.d(TAG, "onReceive GPS: Latitude " + location.getLatitude());
            Log.d(TAG, "onReceive GPS: Longitude " + location.getLongitude());
            Log.d(TAG, "onReceive GPS: Accuracy " + location.getAccuracy());
            Log.d(TAG, "onReceive GPS: Bearing " + location.getBearing());
            if (location.hasAccuracy() && location.getAccuracy() <= suitableMeterGPS) {
                // This is your most accurate location.
                updateNotification(location, "GPS");
                sendLocalBroadcastOnLocationUpdate(location, "GPS");
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.d(TAG, "onStatusChanged: " + provider + " Status: " + status);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.e(TAG, "onProviderEnabled: " + provider);
            updateNotification(null, "Location updating...");
            sendBroadcastOnGPSStateChange(true);
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.e(TAG, "onProviderDisabled: " + provider);
            updateNotification(null, "No GPS Signal");
            sendBroadcastOnGPSStateChange(false);
        }
    }
}
