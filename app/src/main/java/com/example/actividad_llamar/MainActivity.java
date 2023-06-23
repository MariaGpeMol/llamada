package com.example.actividad_llamar;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    EditText numero;
    Button guardar;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        numero = findViewById(R.id.numero);
        guardar = findViewById(R.id.guardar);

        // Inicializa las preferencias compartidas
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);

        // Verificar si hay un dato guardado en las preferencias
        if (sharedPreferences.contains("numero_guardado")) {
            String numeroGuardado = sharedPreferences.getString("numero_guardado", "");
            if (!numeroGuardado.isEmpty()) {
                Intent intent = new Intent(MainActivity.this, MainActivity2.class);
                intent.putExtra("numero_guardado", numeroGuardado);
                startActivity(intent);
                finish(); // Finalizar la actividad actual para que no se pueda volver a ella con el botón "Atrás"
            }
        }

        guardar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String numeroTexto = numero.getText().toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("numero_guardado", numeroTexto);
                editor.apply();
                Toast.makeText(MainActivity.this, "Número guardado en preferencias", Toast.LENGTH_SHORT).show();

                // Iniciar el servicio BackgroundService
                Intent intent = new Intent(MainActivity.this, BackgroundService.class);
                intent.putExtra("numero_guardado", numeroTexto);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }

                // Iniciar la actividad MainActivity2
                Intent intent2 = new Intent(MainActivity.this, MainActivity2.class);
                intent2.putExtra("numero_guardado", numeroTexto);
                startActivity(intent2);
            }
        });

    }
}