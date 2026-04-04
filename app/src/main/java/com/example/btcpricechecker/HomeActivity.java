package com.example.btcpricechecker;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;

public class HomeActivity extends AppCompatActivity {
    private boolean nightMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize UI elements
        CardView usdCard = findViewById(R.id.usdCard);
        CardView eurCard = findViewById(R.id.eurCard);
        TextView nightUsdLink = findViewById(R.id.nightUsdLink);
        TextView nightEurLink = findViewById(R.id.nightEurLink);

        // USD card click → day mode USD
        usdCard.setOnClickListener(v -> startPriceActivity("USD", false));

        // EUR card click → day mode EUR
        eurCard.setOnClickListener(v -> startPriceActivity("EUR", false));

        // Night mode links
        nightUsdLink.setOnClickListener(v -> startPriceActivity("USD", true));
        nightEurLink.setOnClickListener(v -> startPriceActivity("EUR", true));
    }

    private void startPriceActivity(String currency, boolean isNight) {
        Intent intent = new Intent(HomeActivity.this, PriceActivity.class);
        intent.putExtra("currency", currency);
        intent.putExtra("nightMode", isNight);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reset to day mode when returning to home
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }
}