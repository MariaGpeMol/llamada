package com.example.actividad_llamar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import android.os.PowerManager;
import android.media.AudioManager;
import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity2 extends AppCompatActivity implements LocationListener {

    private boolean callCancelled = false;

    private boolean callEnded = false;

    private TextView numeroGuardadoTextView;
    private TextView coordenadasTextView;
    private LocationManager locationManager;
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private Handler handler;
    private boolean callAnswered = false;
    private String incomingPhoneNumber;

    private boolean callLost = false;

    private boolean callStarted = false;
    private boolean messageSent = false;

    private PowerManager.WakeLock wakeLock;
    private PowerManager powerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "MainActivity2:ProximityWakeLock");
        }

        startCallDetection();

        numeroGuardadoTextView = findViewById(R.id.numero_guardado);
        coordenadasTextView = findViewById(R.id.coordenadasTextView);

        Intent intent = getIntent();
        if (intent != null) {
            incomingPhoneNumber = intent.getStringExtra("numero_guardado");
            numeroGuardadoTextView.setText(incomingPhoneNumber);
        }

        handler = new Handler();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        createForegroundNotification();
        startService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isGPSEnabled()) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
        stopCallDetection();

        // Liberar el WakeLock al pausar la actividad
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void startService() {
        Intent serviceIntent = new Intent(this, BackgroundService.class);
        serviceIntent.putExtra("numero_guardado", incomingPhoneNumber);
        startService(serviceIntent);
    }

    private void startLocationUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 10, this);
        }
    }

    private void stopLocationUpdates() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    private boolean isGPSEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void stopCallDetection() {
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    private void sendMessageWithCoordinates(double latitude, double longitude) {
        String message = "Mis coordenadas son: " + latitude + ", " + longitude;

        // Obtener la URL del mapa de Google Maps con las coordenadas
        String mapUrl = "https://www.google.com/maps?q=" + latitude + "," + longitude;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, 3);
        } else {
            sendSMSWithMapLink(incomingPhoneNumber, message, mapUrl);
        }
    }

    private void sendSMSWithMapLink(String phoneNumber, String message, String mapUrl) {
        try {
            // Agregar el enlace al mapa al mensaje
            String messageWithMapLink = message + "\n" + "Ver en Google Maps: " + mapUrl;

            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, messageWithMapLink, null, null);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al enviar el mensaje", Toast.LENGTH_SHORT).show();
        }
    }


    private void sendSMS(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al enviar el mensaje", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        coordenadasTextView.setText("Latitud: " + latitude + ", Longitud: " + longitude);

        if (!callAnswered) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!callAnswered) {
                        double currentLatitude = location.getLatitude();
                        double currentLongitude = location.getLongitude();

                        if (latitude != currentLatitude || longitude != currentLongitude) {
                            sendMessageWithCoordinates(currentLatitude, currentLongitude);
                        }
                    }
                }
            }, 10000);
        }
    }

    private void startCallDetection() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (!callStarted) {
                            Toast.makeText(MainActivity2.this, "Llamada entrante: " + phoneNumber, Toast.LENGTH_SHORT).show();
                            callStarted = true;
                            callEnded = false;
                            callCancelled = false;
                            String numeroGuardado = numeroGuardadoTextView.getText().toString();
                            if (phoneNumber.equals(numeroGuardado)) {
                                handler.postDelayed(() -> {
                                    if (!callEnded && !callCancelled) {
                                        if (ActivityCompat.checkSelfPermission(MainActivity2.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                                            if (lastKnownLocation != null) {
                                                double currentLatitude = lastKnownLocation.getLatitude();
                                                double currentLongitude = lastKnownLocation.getLongitude();
                                                sendMessageWithCoordinates(currentLatitude, currentLongitude);
                                                messageSent = true;
                                            }
                                        }
                                        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                                        if (audioManager != null) {
                                            audioManager.setMicrophoneMute(true);
                                        }
                                    }
                                }, 7000);
                            } else {
                                showNumberMismatchNotification();
                            }
                        }
                        break;

                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        if (callStarted) {
                            Toast.makeText(MainActivity2.this, "Llamada saliente: " + phoneNumber, Toast.LENGTH_SHORT).show();
                            if (!messageSent) {
                                silenceCall();
                            }
                        }
                        break;

                    case TelephonyManager.CALL_STATE_IDLE:
                        if (callStarted && !callEnded && !callCancelled) {
                            Toast.makeText(MainActivity2.this, "Llamada finalizada", Toast.LENGTH_SHORT).show();
                            callEnded = true;
                            handler.removeCallbacksAndMessages(null);

                            String numeroGuardado = numeroGuardadoTextView.getText().toString();
                            if (phoneNumber.equals(numeroGuardado)) {
                                Toast.makeText(MainActivity2.this, "Llamada perdida. Llamando de nuevo...", Toast.LENGTH_SHORT).show();
                                makeCall(numeroGuardado);
                            } else {
                                showNumberMismatchNotification();
                            }
                        }

                        callStarted = false;
                        messageSent = false;
                        callEnded = false;
                        break;
                }
            }
        };
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void silenceCall() {
        if (powerManager != null && wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "MainActivity2:ProximityWakeLock");
            wakeLock.acquire();
        }

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_CALL);
            audioManager.setSpeakerphoneOn(false);
        }
    }

    private void makeCall(String phoneNumber) {
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + phoneNumber));
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(callIntent);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 4);
        }
    }

    private void showNumberMismatchNotification() {
        Toast.makeText(MainActivity2.this, "El número no coincide", Toast.LENGTH_SHORT).show();
    }

    private void createForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            String channelId = "location_channel";
            CharSequence channelName = "Location";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel notificationChannel = new NotificationChannel(channelId, channelName, importance);
            notificationManager.createNotificationChannel(notificationChannel);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                    .setContentTitle("Aplicación en segundo plano")
                    .setContentText("La aplicación está utilizando la ubicación en segundo plano")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setPriority(NotificationCompat.PRIORITY_LOW);

            Notification notification = builder.build();
            startForeground(1, notification);
        }
    }

    private void startForeground(int notificationId, Notification notification) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(notificationId, notification);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCallDetection();
        }
    }
}