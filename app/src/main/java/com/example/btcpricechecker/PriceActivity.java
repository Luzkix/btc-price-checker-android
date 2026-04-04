package com.example.btcpricechecker;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
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
    private FrameLayout rootLayout;
    private View borderView;
    private TextView clockTextView;
    private ImageView btcTextImage;
    private LinearLayout btcTextBlock;
    private TextView priceTextView;
    private TextView currencyTextView;
    private TextView changeTextView;
    private TextView changePercentageTextView;
    private TextView lastUpdateTextView;
    private LinearLayout lastUpdateBlock;

    // Font
    private Typeface abrilFatfaceFont;

    // Screen dimensions
    private int screenHeight;
    private int screenWidth;
    private float density;

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

        // Get screen dimensions
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenHeight = metrics.heightPixels;
        screenWidth = metrics.widthPixels;
        density = metrics.density;

        // Load font
        abrilFatfaceFont = ResourcesCompat.getFont(this, R.font.abril_fatface);

        // Extract parameters
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            currentCurrency = extras.getString("currency", "USD");
            nightMode = extras.getBoolean("nightMode", false);
        }

        // Initialize UI elements
        rootLayout = findViewById(R.id.priceRootLayout);
        borderView = findViewById(R.id.borderView);
        clockTextView = findViewById(R.id.clockTextView);
        btcTextImage = findViewById(R.id.btcText);
        btcTextBlock = findViewById(R.id.btcTextBlock);
        priceTextView = findViewById(R.id.priceTextView);
        currencyTextView = findViewById(R.id.currencyTextView);
        changeTextView = findViewById(R.id.changeTextView);
        changePercentageTextView = findViewById(R.id.changePercentageTextView);
        lastUpdateTextView = findViewById(R.id.lastUpdateTextView);
        lastUpdateBlock = findViewById(R.id.lastUpdateBlock);

        // Apply font to all text views
        applyFont();

        // Apply night/day theme
        applyTheme();

        // Set fullscreen immersive sticky
        setImmersiveMode();

        // Check aspect ratio and adjust layout
        adjustLayoutForAspectRatio();

        // Touch listener - show dialog to return home
        rootLayout.setOnClickListener(v -> showBackToHomeDialog());

        // Start price updates
        startPriceUpdates();
    }

    private void applyFont() {
        // Apply Abril Fatface font to price-related text views
        if (abrilFatfaceFont != null) {
            clockTextView.setTypeface(abrilFatfaceFont);
            priceTextView.setTypeface(abrilFatfaceFont);
            currencyTextView.setTypeface(abrilFatfaceFont);
            changeTextView.setTypeface(abrilFatfaceFont);
            changePercentageTextView.setTypeface(abrilFatfaceFont);
        }
    }

    private void applyTheme() {
        if (nightMode) {
            // Night mode colors
            rootLayout.setBackgroundColor(Color.parseColor("#000000"));
            borderView.setVisibility(View.GONE);

            clockTextView.setTextColor(Color.parseColor("#6B6C6C"));
            priceTextView.setTextColor(Color.parseColor("#e7dfc6"));
            currencyTextView.setTextColor(Color.parseColor("#6B6C6C"));
            lastUpdateTextView.setTextColor(Color.parseColor("#292929"));

            // BTC text image is dimmed in night mode
            btcTextImage.setAlpha(0.7f);
        } else {
            // Day mode colors
            rootLayout.setBackgroundColor(Color.parseColor("#FFFFFF"));
            borderView.setVisibility(View.VISIBLE);

            clockTextView.setTextColor(Color.parseColor("#000000"));
            priceTextView.setTextColor(Color.parseColor("#000000"));
            currencyTextView.setTextColor(Color.parseColor("#000000"));
            lastUpdateTextView.setTextColor(Color.parseColor("#A9A9A9"));

            // Full opacity for BTC text in day mode
            btcTextImage.setAlpha(1.0f);
        }
    }

    private void adjustLayoutForAspectRatio() {
        // Check if screen is wider (aspect ratio >= 16/8 = 2.0)
        float aspectRatio = (float) screenWidth / screenHeight;

        // Apply layout margins based on original CSS specs
        // BTC text: padding-left: 4vw
        LinearLayout.LayoutParams btcParams = (LinearLayout.LayoutParams) btcTextBlock.getLayoutParams();
        btcParams.setMarginStart((int) (screenWidth * 0.04f)); // 4vw from left
        btcTextBlock.setLayoutParams(btcParams);

        // Clock block: padding-right: 3vw, padding-top: 5vh
        LinearLayout clockBlock = findViewById(R.id.clockBlock);
        if (clockBlock != null) {
            clockBlock.setPadding(
                0,  // left
                (int) (screenHeight * 0.05f),  // top: 5vh
                (int) (screenWidth * 0.03f),   // right: 3vw
                0   // bottom
            );
        }

        // Last update block: padding-right: 2vw, with bottom margin
        LinearLayout.LayoutParams lastUpdateParams = (LinearLayout.LayoutParams) lastUpdateBlock.getLayoutParams();
        lastUpdateParams.setMarginEnd((int) (screenWidth * 0.02f)); // 2vw from right
        // Add bottom margin to shift text up from bottom edge
        lastUpdateParams.bottomMargin = (int) (screenHeight * 0.01f); // Small offset
        lastUpdateBlock.setLayoutParams(lastUpdateParams);

        if (aspectRatio >= 2.0f) {
            // Hide BTC text block on wider screens
            btcTextBlock.setVisibility(View.GONE);

            // Adjust last update block position for wider screens
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) lastUpdateBlock.getLayoutParams();
            if (nightMode) {
                // Shift down in night mode
                params.topMargin = (int) (screenHeight * 0.02f);
            } else {
                // Shift up in day mode
                params.topMargin = (int) (-screenHeight * 0.01f);
            }
            lastUpdateBlock.setLayoutParams(params);
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

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Return to Home");
        builder.setMessage("Do you want to go back to the homepage?");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            Intent intent = new Intent(PriceActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            setImmersiveMode();
            dialog.dismiss();
        });
        builder.setOnCancelListener(dialog -> {
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
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = sdf.format(new Date());
        // Add clock emoji/icon before time
        clockTextView.setText("🕑" + timeStr);

        // Set clock text size to 14vh
        float clockSizeSp = (screenHeight * 0.14f) / density;
        clockTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, clockSizeSp);
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
                    runOnUiThread(() -> showError("COINBASE API"));
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showError("LOST CONNECTION"));
            }
        }).start();
    }

    private void showError(String message) {
        priceTextView.setText("ERROR");
        changeTextView.setText(message);
        changePercentageTextView.setText("");

        // Error styling
        priceTextView.setTextColor(Color.parseColor("#FAA31B"));
        changeTextView.setTextColor(Color.parseColor("#FAA31B"));
        changePercentageTextView.setTextColor(Color.parseColor("#FAA31B"));
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
            String currencySymbol = currentCurrency.equals("USD") ? "$" : "€";

            // Final variables for use in inner class
            final String finalCurrencySymbol = currencySymbol;
            final int finalPriceChange = priceChange;
            final String finalPriceChangePercentage = priceChangePercentage;
            final int finalActualPrice = actualPrice;
            final boolean isPriceUp = finalPriceChange >= 0;

            // Update the UI elements
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Format price with commas
                    String priceString = String.format(Locale.US, "%,d", finalActualPrice);
                    priceTextView.setText(priceString);
                    currencyTextView.setText(finalCurrencySymbol);

                    // Calculate dynamic font size for price
                    int priceLength = priceString.length();
                    float priceFontSize = calculateDynamicFontSize(priceLength, screenWidth);
                    priceTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, priceFontSize);
                    currencyTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, priceFontSize);

                    // Calculate change direction and colors
                    String arrow = isPriceUp ? "▲" : "▼";
                    int color;
                    if (isPriceUp) {
                        color = nightMode ? Color.parseColor("#005e0d") : Color.parseColor("#32CD32");
                    } else {
                        color = nightMode ? Color.parseColor("#966211") : Color.parseColor("#FAA31B");
                    }

                    // Set change text with arrow: ▲ +123$
                    String changeText = arrow + (isPriceUp ? "+" : "") + String.format(Locale.US, "%,d", Math.abs(finalPriceChange)) + finalCurrencySymbol;
                    changeTextView.setText(changeText);
                    changeTextView.setTextColor(color);

                    // Set percentage text: ▲ +0.42%
                    String changePercentageText = arrow + (isPriceUp ? "+" : "") + finalPriceChangePercentage + "%";
                    changePercentageTextView.setText(changePercentageText);
                    changePercentageTextView.setTextColor(color);

                    // Set change text sizes to 15vh
                    float changeSizeSp = (screenHeight * 0.15f) / density;
                    changeTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, changeSizeSp);
                    changePercentageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, changeSizeSp);

                    // Update last update time
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    lastUpdateTextView.setText("Last price info: " + sdf.format(new Date()) + " CET");

                    // Set last update text size to 3vh
                    float updateSizeSp = (screenHeight * 0.03f) / density;
                    lastUpdateTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, updateSizeSp);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Calculate dynamic font size based on text length to fit screen width
     * Similar to the JavaScript: const charWidth = 0.59; const maxFontSize = 100 / (len * charWidth);
     * The original web version uses viewport width units (vw)
     * We convert to Android SP units
     */
    private float calculateDynamicFontSize(int charCount, int screenWidthPx) {
        // Character width approximation for Abril Fatface
        // This is slightly wider than average to ensure text fits
        float charWidthRatio = 0.62f;

        // Calculate max font size in vw units (viewport width percentage)
        // Formula from original: maxFontSize = 100 / (len * charWidth)
        float maxFontSizeVw = 100f / (charCount * charWidthRatio);

        // Convert vw to pixels: (screenWidth * percentage) / 100
        float maxFontSizePx = (screenWidthPx * maxFontSizeVw) / 100f;

        // Limit font size to max 60vh (leave some room for padding)
        float maxFontSizeFromHeight = screenHeight * 0.60f;

        // Take the smaller of the two to ensure text fits both width and height
        float limitedSizePx = Math.min(maxFontSizePx, maxFontSizeFromHeight);

        // Convert to SP (taking into account screen density)
        return limitedSizePx / density;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}
