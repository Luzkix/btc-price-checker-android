package com.example.btcpricechecker;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
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
    private String currentCurrency = "USD";
    private boolean nightMode = false;
    private Timer timer;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isImmersive = true;

    // UI Elements
    private TextView clockTextView;
    private ImageView btcImageView;
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
    btcImageView = findViewById(R.id.btcLogo);
    priceTextView = findViewById(R.id.priceTextView);
    currencyTextView = findViewById(R.id.currencyTextView);
    changeArrow = findViewById(R.id.changeArrow);
    changeTextView = findViewById(R.id.changeTextView);
    changePercentageTextView = findViewById(R.id.changePercentageTextView);
    lastUpdateTextView = findViewById(R.id.lastUpdateTextView);
    
    // Drop shadow for price (original CSS: text-shadow: 2px 2px 4px rgba(0,0,0,0.3))
    priceTextView.setShadowLayer(4, 2, 2, Color.parseColor("#5A000000"));

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
            rootLayout.setBackgroundColor(Color.parseColor("#000000"));
            clockTextView.setTextColor(Color.parseColor("#6B6C6C"));
            priceTextView.setTextColor(Color.parseColor("#e7dfc6"));
            currencyTextView.setTextColor(Color.parseColor("#6B6C6C"));
            lastUpdateTextView.setTextColor(Color.parseColor("#292929"));
        } else {
            // Day mode colors from pricePageStyle.css
            rootLayout.setBackgroundColor(Color.parseColor("#FFFFFF"));
            clockTextView.setTextColor(Color.parseColor("#000000"));
            priceTextView.setTextColor(Color.parseColor("#000000"));
            currencyTextView.setTextColor(Color.parseColor("#000000"));
            lastUpdateTextView.setTextColor(Color.parseColor("#A9A9A9"));
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
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        int bgColor = nightMode ? Color.parseColor("#222222") : Color.parseColor("#FFFFFF");
        int textColor = nightMode ? Color.parseColor("#e7dfc6") : Color.parseColor("#1a1a2e");
        int buttonColor = Color.parseColor("#f7931a");

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
        }, 30000, 30000); // Update every 30 seconds
    }

    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        clockTextView.setText(sdf.format(new Date()));
    }

    private void fetchBitcoinPrice() {
        new Thread(() -> {
            try {
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
                    updatePriceUI(json);
                } else {
                    handler.post(() -> {
                        priceTextView.setText("ERROR");
                        changeTextView.setText("API Error");
                        changePercentageTextView.setText("");
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    priceTextView.setText("ERROR");
                    changeTextView.setText("Connection failed");
                    changePercentageTextView.setText("");
                });
            }
        }).start();
    }

    private void updatePriceUI(JSONObject json) {
        try {
            double open = json.getDouble("open");
            double last = json.getDouble("last");

            // Format price with commas (same as JavaScript: toLocaleString('en-US'))
            String priceString = String.format(Locale.US, "%,.0f", last);
            String currencySymbol = currentCurrency;

            // Calculate change
            double change = last - open;
            double changePercentage = (change / open) * 100;

    // Arrow symbols from original HTML: ▲ for up, ▼ for down
    String arrow = change >= 0 ? "▲" : "▼";
    
    // Colors from CSS:
    // Day: price-up = #4FC165, price-down = #FAA31B
    // Night: price-up = #005e0d, price-down = #966211
    String arrowColor;
    if (change >= 0) {
        arrowColor = nightMode ? "#005e0d" : "#4FC165";
    } else {
        arrowColor = nightMode ? "#966211" : "#FAA31B";
    }

            // Adjust font size based on price length
            // Original JS logic from CSS: font-size based on viewport width
            adjustFontSize(priceString);

        final String priceStr = priceString;
        final String changeStr = String.format(Locale.US, "%s%,.0f %s",
                change >= 0 ? "+" : "", change, currencySymbol);
        final String percentStr = String.format(Locale.US, "%s%.2f%%",
                change >= 0 ? "+" : "", changePercentage);
        final String arrowColorStr = arrowColor;

            handler.post(() -> {
                priceTextView.setText(priceStr);
                currencyTextView.setText(currencySymbol);

        // Arrow (▲/▼) with dynamic color
        changeArrow.setText(arrow);
        changeArrow.setTextColor(Color.parseColor(arrowColor));
        
        // Change text (no arrow, just value)
        changeTextView.setText(changeStr.replace(arrow, "").trim());
        changeTextView.setTextColor(Color.parseColor(nightMode ? "#e7dfc6" : "#000000"));

        // Percentage text (no arrow, just value)
        changePercentageTextView.setText(percentStr.replace(arrow, "").trim());
        changePercentageTextView.setTextColor(Color.parseColor(arrowColor));

                // Last update - same format as original: "Last price info: HH:mm:ss CET"
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                lastUpdateTextView.setText("Last price info: " + sdf.format(new Date()) + " CET");
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void adjustFontSize(String price) {
        // Original CSS: font-size based on 8vw
        // Get screen width and calculate relative size
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        float defaultTextSize = screenWidth * 0.08f; // 8% of screen width
        
        // Adjust based on price length to prevent overflow
        int len = price.length();
        if (len > 8) {
            defaultTextSize *= 0.8;
        } else if (len > 7) {
            defaultTextSize *= 0.9;
        } else if (len > 6) {
            defaultTextSize *= 0.95;
        }
        
        // Apply sizes (convert px to sp)
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        priceTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultTextSize);
        currencyTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultTextSize * 0.7f);
        changeTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultTextSize * 0.3f);
        changePercentageTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultTextSize * 0.3f);
        changeArrow.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultTextSize * 0.3f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}