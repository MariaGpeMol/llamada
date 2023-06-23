package com.example.actividad_llamar;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

public class BackgroundService extends Service implements LocationListener {
    private LocationManager locationManager;
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private String incomingPhoneNumber;
    private CountDownTimer messageTimer;
    private Toast toast;
    private boolean isCallIncoming;

    @Override
    public void onCreate() {
        super.onCreate();
        startCallDetection();
        startLocationUpdates();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            incomingPhoneNumber = intent.getStringExtra("numero_guardado");
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCallDetection();
        stopLocationUpdates();
        cancelMessageTimer();
    }

    private void startCallDetection() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_RINGING:
                        // Handle incoming call
                        if (phoneNumber.equals(incomingPhoneNumber)) {
                            isCallIncoming = true;
                            startMessageTimer();
                        } else {
                            isCallIncoming = false;
                            showNumberMismatchNotification();
                        }
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        // Handle call answered
                        silenceCall();
                        cancelMessageTimer();
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Handle call ended
                        cancelMessageTimer();
                        break;
                }
            }
        };
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void stopCallDetection() {
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    private void startMessageTimer() {
        cancelMessageTimer();
        messageTimer = new CountDownTimer(7000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // No se requiere acción durante el conteo
            }

            @Override
            public void onFinish() {
                if (isCallIncoming) {
                    sendDelayedMessage();
                }
            }
        };
        messageTimer.start();
    }

    private void cancelMessageTimer() {
        if (messageTimer != null) {
            messageTimer.cancel();
            messageTimer = null;
        }
    }

    private void sendDelayedMessage() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null) {
                double latitude = lastKnownLocation.getLatitude();
                double longitude = lastKnownLocation.getLongitude();
                sendMessageWithCoordinates(incomingPhoneNumber, latitude, longitude);
            }
        }
    }

    private void sendMessageWithCoordinates(String phoneNumber, double latitude, double longitude) {
        String message = "Mis coordenadas son: " + latitude + ", " + longitude;
        String mapUrl = "https://www.google.com/maps?q=" + latitude + "," + longitude;

        try {
            String messageWithMapLink = message + "\n" + "Ver en Google Maps: " + mapUrl;
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, messageWithMapLink, null, null);

            // Envío de mensaje de prueba
            String testMessage = "Llamada detectada - Número: " + phoneNumber;
            smsManager.sendTextMessage(phoneNumber, null, testMessage, null, null);
            showToast("Mensaje de prueba enviado");
        } catch (Exception e) {
            e.printStackTrace();
            showToast("Error al enviar el mensaje");
        }
    }

    private void silenceCall() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0);
        }
    }

    private void startLocationUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
        }
    }

    private void stopLocationUpdates() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        // Handle location updates if needed
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void showNumberMismatchNotification() {
        // Show notification or handle number mismatch as needed
    }

    private void showToast(String message) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
    }
}

