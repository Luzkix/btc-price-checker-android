package com.example.btcpricechecker;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class PriceActivity extends AppCompatActivity {
    private static final int PRICE_UPDATE_INTERVAL = 10; // 10 seconds cache
    private String currentCurrency = "USD";
    private boolean nightMode = false;
    private Timer timer;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    // Caching variables
    private JSONObject cachedUsdData = null;
    private JSONObject cachedEurData = null;
    private long usdLastUpdate = 0;
    private long eurLastUpdate = 0;
    
    // UI Elements
    private TextView clockTextView;
    private TextView priceTextView;
    private TextView currencyTextView;
    private TextView changeArrow;
    private TextView changeTextView;
    private TextView changePercentageTextView;
    private TextView lastUpdateTextView;
    private View rootLayout;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen mode
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_price);

        // Extract parameters
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            currentCurrency = extras.getString("currency", "USD");
            nightMode = extras.getBoolean("nightMode", false);
        }

        // Initialize UI elements
        rootLayout = findViewById(R.id.priceRootLayout);
        clockTextView = findViewById(R.id.clockTextView);
        priceTextView = findViewById(R.id.priceTextView);
        currencyTextView = findViewById(R.id.currencyTextView);
        changeArrow = findViewById(R.id.changeArrow);
        changeTextView = findViewById(R.id.changeTextView);
        changePercentageTextView = findViewById(R.id.changePercentageTextView);
        lastUpdateTextView = findViewById(R.id.lastUpdateTextView);

        // Apply night/day theme colors
        applyTheme();

        // Set fullscreen immersive sticky
        setImmersiveMode();

        // Touch listener - show dialog to return home
        rootLayout.setOnClickListener(v -> showBackToHomeDialog());

        // Start price updates
        startPriceUpdates();
    }

    private void applyTheme() {
        if (nightMode) {
            // Night mode colors from pricePageStyleNight.css
            if (rootLayout != null) {
                rootLayout.setBackgroundColor(Color.parseColor("#000000"));
            }
            if (clockTextView != null) {
                clockTextView.setTextColor(Color.parseColor("#6B6C6C"));
            }
            if (priceTextView != null) {
                priceTextView.setTextColor(Color.parseColor("#e7dfc6"));
            }
            if (currencyTextView != null) {
                currencyTextView.setTextColor(Color.parseColor("#6B6C6C"));
            }
            if (lastUpdateTextView != null) {
                lastUpdateTextView.setTextColor(Color.parseColor("#292929"));
            }
        } else {
            // Day mode colors from pricePageStyle.css
            if (rootLayout != null) {
                rootLayout.setBackgroundColor(Color.parseColor("#FFFFFF"));
            }
            if (clockTextView != null) {
                clockTextView.setTextColor(Color.parseColor("#000000"));
            }
            if (priceTextView != null) {
                priceTextView.setTextColor(Color.parseColor("#000000"));
            }
            if (currencyTextView != null) {
                currencyTextView.setTextColor(Color.parseColor("#000000"));
            }
            if (lastUpdateTextView != null) {
                lastUpdateTextView.setTextColor(Color.parseColor("#A9A9A9"));
            }
        }
    }

    private void setImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    private void showBackToHomeDialog() {
        // Exit immersive mode first so dialog is visible
        if (rootLayout != null) {
            rootLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Return to Home");
        builder.setMessage("Do you want to go back to the homepage?");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            Intent intent = new Intent(PriceActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            // Return to immersive mode
            setImmersiveMode();
            dialog.dismiss();
        });
        builder.setOnCancelListener(dialog -> {
            // Return to immersive mode
            setImmersiveMode();
            dialog.dismiss();
        });
        builder.show();
    }

    private void startPriceUpdates() {
        updateClock();
        fetchBitcoinPrice();

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handler.post(() -> {
                    updateClock();
                    fetchBitcoinPrice();
                });
            }
        }, 0, 30000); // Update every 30 seconds
    }

private void updateClock() {
        if (clockTextView != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            clockTextView.setText(sdf.format(new Date()));
        }
    }

    private void fetchBitcoinPrice() {
        new Thread(() -> {
            try {
                // Check if we should use cached data
                JSONObject cachedData = null;
                long currentTime = System.currentTimeMillis() / 1000;
                long lastUpdate = 0;
                
                if (currentCurrency.equals("USD")) {
                    cachedData = cachedUsdData;
                    lastUpdate = usdLastUpdate;
                } else if (currentCurrency.equals("EUR")) {
                    cachedData = cachedEurData;
                    lastUpdate = eurLastUpdate;
                }
                
                // If we have cached data and it's not expired, use it
                if (cachedData != null && (currentTime - lastUpdate) < PRICE_UPDATE_INTERVAL) {
                    updatePriceUI(cachedData);
                    return;
                }
                
                // Otherwise fetch new data
                String urlString = "https://api.exchange.coinbase.com/products/BTC-" + currentCurrency + "/stats";
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        content.append(line);
                    }
                    in.close();

                    JSONObject json = new JSONObject(content.toString());
                    
                    // Cache the data
                    if (currentCurrency.equals("USD")) {
                        cachedUsdData = json;
                        usdLastUpdate = currentTime;
                    } else if (currentCurrency.equals("EUR")) {
                        cachedEurData = json;
                        eurLastUpdate = currentTime;
                    }
                    
                    updatePriceUI(json);
                } else {
                    runOnUiThread(() -> {
                        if (priceTextView != null) {
                            priceTextView.setText("ERROR");
                            changeTextView.setText("API Error");
                            changePercentageTextView.setText("");
                        }
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    if (priceTextView != null) {
                        priceTextView.setText("ERROR");
                        changeTextView.setText("Connection failed");
                        changePercentageTextView.setText("");
                    }
                });
            }
        }).start();
    }

private void updatePriceUI(JSONObject json) {
        try {
            // Parse the JSON response from Coinbase API
            double open = json.getDouble("open");
            double last = json.getDouble("last");
            
            // Calculate price change values
            int actualPrice = (int) last;
            int priceChange = (int) (last - open);
            
            // Format the price change percentage
            double changePercentage = ((last / open) - 1) * 100;
            String priceChangePercentage = String.format("%.2f", Math.abs(changePercentage));
            
            // Format the currency symbol
            String currencySymbol = currentCurrency;
            if (currentCurrency.equals("USD")) {
                currencySymbol = "$";
            } else if (currentCurrency.equals("EUR")) {
                currencySymbol = "€";
            }
            
            // Final variables for use in inner class
            final String finalCurrencySymbol = currencySymbol;
            final int finalPriceChange = priceChange;
            final String finalPriceChangePercentage = priceChangePercentage;
            final int finalActualPrice = actualPrice;
            
            // Update the UI elements
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Format price with commas
                    String priceString = String.format(Locale.US, "%,d", finalActualPrice);
                    if (priceTextView != null) {
                        priceTextView.setText(priceString);
                        currencyTextView.setText(finalCurrencySymbol);
                    }
                    
                    // Calculate change direction
                    String arrow = finalPriceChange >= 0 ? "▲" : "▼";
                    int color;
                    if (finalPriceChange >= 0) {
                        color = nightMode ? Color.parseColor("#005e0d") : Color.parseColor("#4FC165");
                    } else {
                        color = nightMode ? Color.parseColor("#966211") : Color.parseColor("#FAA31B");
                    }
                    
                    // Set the arrow symbol and color
                    if (changeArrow != null) {
                        changeArrow.setText(arrow);
                        changeArrow.setTextColor(color);
                    }
                    
                    // Set change text without arrow
                    String changeText = String.format(Locale.US, "%s%,d %s", 
                        finalPriceChange >= 0 ? "+" : "", Math.abs(finalPriceChange), finalCurrencySymbol);
                    if (changeTextView != null) {
                        changeTextView.setText(changeText);
                    }
                    
                    // Set percentage text
                    String changePercentageText = String.format(Locale.US, "%s%s%%", 
                        finalPriceChange >= 0 ? "+" : "", finalPriceChangePercentage);
                    if (changePercentageTextView != null) {
                        changePercentageTextView.setText(changePercentageText);
                        changePercentageTextView.setTextColor(color);
                    }
                    
                    // Update last update time
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    if (lastUpdateTextView != null) {
                        lastUpdateTextView.setText("Last price info: " + sdf.format(new Date()) + " CET");
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}