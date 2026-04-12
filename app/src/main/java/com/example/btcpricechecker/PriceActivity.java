package com.example.btcpricechecker;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
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
    private ImageView btcLogoImage; // For ultra-wide layout
    private LinearLayout btcTextBlock;
    private TextView priceTextView;
    private TextView currencyTextView;
    private TextView changeTextView;
    private TextView changePercentageTextView;
    private TextView lastUpdateTextView;
    private LinearLayout lastUpdateBlock;
    private LinearLayout clockBlock;

    // Font
    private Typeface abrilFatfaceFont;

    // Screen dimensions
    private int screenHeight;
    private int screenWidth;
    private float density;

// Error message TextView
private TextView errorMessageTextView;

 // Font size caching variables
    private int cachedDigitCount = -1; // -1 = not calculated yet
    private float cachedFontSizeSp = -1f; // cached font size in SP

    // Loading state variables
    private boolean isFirstLoad = true;
    private long loadingStartTime = 0;
    private boolean isLoadingStateVisible = false;
    private static final long MIN_LOADING_DURATION_MS = 4000; // Minimum 4 seconds for loading state

    // Aspect ratio category
    private enum AspectRatioCategory {
        TABLET, // <= 1.6
        WIDE, // > 1.6 && <= 1.9
        ULTRAWIDE // > 1.9
    }
    private AspectRatioCategory aspectRatioCategory = AspectRatioCategory.WIDE; // Default fallback

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen mode
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // Keep screen on - prevent screen from turning off
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Get REAL screen dimensions BEFORE setting content view to determine layout
        DisplayMetrics realMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(realMetrics);
        int actualWidth = realMetrics.widthPixels;
        int actualHeight = realMetrics.heightPixels;
        float aspectRatio = (float) actualWidth / actualHeight;

        // Determine aspect ratio category and select appropriate layout
        int layoutRes;
        if (aspectRatio <= 1.6f) {
            aspectRatioCategory = AspectRatioCategory.TABLET;
            layoutRes = R.layout.activity_price_tablet;
        } else if (aspectRatio <= 1.9f) {
            aspectRatioCategory = AspectRatioCategory.WIDE;
            layoutRes = R.layout.activity_price_wide;
        } else {
            aspectRatioCategory = AspectRatioCategory.ULTRAWIDE;
            layoutRes = R.layout.activity_price_ultrawide;
        }

        android.util.Log.d("PriceActivity", "Aspect ratio: " + aspectRatio + ", selected layout: " + aspectRatioCategory);

        setContentView(layoutRes);

        // Use the REAL screen dimensions we already obtained before setContentView
        // These are already fullscreen values, no need to get them again
        screenWidth = actualWidth;
        screenHeight = actualHeight;
        density = realMetrics.density;

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
        errorMessageTextView = findViewById(R.id.errorMessageTextView);
        lastUpdateTextView = findViewById(R.id.lastUpdateTextView);
        lastUpdateBlock = findViewById(R.id.lastUpdateBlock);
        clockBlock = findViewById(R.id.clockBlock);

        // Optional: bitcoin logo for ultra-wide layout
        btcLogoImage = findViewById(R.id.btcLogo);

        // Apply font to all text views
        applyFont();

        // Apply night/day theme
        applyTheme();

        // Set fullscreen immersive sticky
        setImmersiveMode();

        // Wait for layout to be fully drawn before calculating dimensions
        rootLayout.post(() -> {
            // Check aspect ratio and adjust layout
            adjustLayoutForAspectRatio();
        });

        // Start price updates
        startPriceUpdates();
    }

    private void applyFont() {
        // Apply Abril Fatface font to price-related text views
        if (abrilFatfaceFont != null) {
            clockTextView.setTypeface(abrilFatfaceFont, Typeface.BOLD);
            priceTextView.setTypeface(abrilFatfaceFont, Typeface.BOLD);
            currencyTextView.setTypeface(abrilFatfaceFont, Typeface.BOLD);
            changeTextView.setTypeface(abrilFatfaceFont, Typeface.BOLD);
            changePercentageTextView.setTypeface(abrilFatfaceFont, Typeface.BOLD);
        }
    }

    private void applyTheme() {
        if (nightMode) {
            // Night mode colors
            rootLayout.setBackgroundColor(Color.parseColor("#000000"));
            if (borderView != null) {
                borderView.setVisibility(View.GONE);
            }

            clockTextView.setTextColor(Color.parseColor("#6B6C6C"));
            priceTextView.setTextColor(Color.parseColor("#e7dfc6"));
            currencyTextView.setTextColor(Color.parseColor("#6B6C6C"));
            lastUpdateTextView.setTextColor(Color.parseColor("#292929"));

            // BTC text image is dimmed in night mode
            if (btcTextImage != null) {
                btcTextImage.setAlpha(0.7f);
            }

            // Bitcoin logo dimmed in night mode
            if (btcLogoImage != null) {
                btcLogoImage.setAlpha(0.7f);
            }
        } else {
            // Day mode colors
            rootLayout.setBackgroundColor(Color.parseColor("#FFFFFF"));
            if (borderView != null) {
                borderView.setVisibility(View.VISIBLE);
            }

            clockTextView.setTextColor(Color.parseColor("#000000"));
            priceTextView.setTextColor(Color.parseColor("#000000"));
            currencyTextView.setTextColor(Color.parseColor("#000000"));
            lastUpdateTextView.setTextColor(Color.parseColor("#FFFFFF"));

            // Full opacity for BTC text in day mode
            if (btcTextImage != null) {
                btcTextImage.setAlpha(1.0f);
            }

            // Full opacity for bitcoin logo in day mode
            if (btcLogoImage != null) {
                btcLogoImage.setAlpha(1.0f);
            }
        }
    }

    private void adjustLayoutForAspectRatio() {
        // Get REAL screen dimensions (physical screen size, not window size)
        // This is crucial because aspect ratio must be calculated from physical screen,
        // not from window which may have different sizes depending on system bars
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int actualWidth = metrics.widthPixels;
        int actualHeight = metrics.heightPixels;
        float actualDensity = metrics.density;

        // Update stored values
        screenWidth = actualWidth;
        screenHeight = actualHeight;
        density = actualDensity;

        // Invalidate font size cache when screen dimensions change
        // This ensures font is recalculated for the actual available width
        cachedDigitCount = -1;
        cachedFontSizeSp = -1f;

        // Log dimensions for debugging
        android.util.Log.d("PriceActivity", "Real screen dimensions: " + actualWidth + "x" + actualHeight +
            ", aspect ratio: " + ((float) actualWidth / actualHeight) + ", category: " + aspectRatioCategory);

        // Apply layout based on aspect ratio category
        switch (aspectRatioCategory) {
            case TABLET:
                applyTabletLayout();
                break;
            case WIDE:
                applyWideLayout();
                break;
            case ULTRAWIDE:
                applyUltraWideLayout();
                break;
        }
    }

    private void applyTabletLayout() {
        // Tablet layout (aspect ratio <= 1.6):
        // - 50% wider border (handled by day_border_tablet.xml - 15dp instead of 10dp)
        // - Clock: same top/right padding (3vw right, matching top padding)
        // - Last price info: positioned in XML with proper margins

        // BTC text: padding-left: 4vw
        if (btcTextBlock != null) {
            LinearLayout.LayoutParams btcParams = (LinearLayout.LayoutParams) btcTextBlock.getLayoutParams();
            btcParams.setMarginStart((int) (screenWidth * 0.04f)); // 4vw from left
            btcTextBlock.setLayoutParams(btcParams);
        }

        // Clock block: reduced top padding (1.5vw instead of 3vw)
        if (clockBlock != null) {
            int topPadding = (int) (screenWidth * 0.015f); // 1.5vw from top (50% of previous)
            int rightPadding = (int) (screenWidth * 0.03f); // 3vw from right
            clockBlock.setPadding(0, topPadding, rightPadding, 0);
        }

        // Last update block: positioned in XML with proper margins for 15dp border
        // Margins are already set in activity_price_tablet.xml
    }

    private void applyWideLayout() {
        // Wide layout (1.6 < aspect ratio <= 1.9):
        // - Clock: reduced top padding (1.5vw), right padding unchanged
        // - Last price info: positioned in XML with proper margins

        // BTC text: padding-left: 4vw
        if (btcTextBlock != null) {
            LinearLayout.LayoutParams btcParams = (LinearLayout.LayoutParams) btcTextBlock.getLayoutParams();
            btcParams.setMarginStart((int) (screenWidth * 0.04f)); // 4vw from left
            btcTextBlock.setLayoutParams(btcParams);
        }

        // Clock block: reduced top padding (1.5vw instead of 3vw)
        if (clockBlock != null) {
            int topPadding = (int) (screenWidth * 0.015f); // 1.5vw from top (50% of previous)
            int rightPadding = (int) (screenWidth * 0.03f); // 3vw from right
            clockBlock.setPadding(0, topPadding, rightPadding, 0);
        }

        // Last update block: positioned in XML with proper margins for 10dp border
        // Margins are already set in activity_price_wide.xml
    }

    private void applyUltraWideLayout() {
        // Ultra-wide layout (aspect ratio > 1.9):
        // - Bitcoin logo on same row as clock (space-between)
        // - Clock: reduced top padding (1.5vw), horizontal padding unchanged (3vw)
        // - Last price info: properly positioned inside border

        // BTC text block is already hidden in XML (visibility="gone")

        // Setup bitcoin logo if present
        if (btcLogoImage != null && clockBlock != null) {
            // Set logo height to match clock height exactly (100%)
            float clockSizeSp = (screenHeight * 0.14f) / density;
            float clockSizePx = clockSizeSp * density;
            int logoHeight = (int) clockSizePx; // 100% of clock height

            LinearLayout.LayoutParams logoParams = (LinearLayout.LayoutParams) btcLogoImage.getLayoutParams();
            logoParams.height = logoHeight;
            logoParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            btcLogoImage.setLayoutParams(logoParams);

            // Set padding for clock block (1.5vw top, 3vw horizontal)
            int horizontalPadding = (int) (screenWidth * 0.03f);
            int topPadding = (int) (screenWidth * 0.015f); // 1.5vw from top (50% of previous)
            clockBlock.setPadding(horizontalPadding, topPadding, horizontalPadding, 0);
        }

        // Last update block - already positioned in XML with margins
        if (lastUpdateBlock != null) {
            // Note: lastUpdateBlock is inside a FrameLayout (lastUpdateBlockContainer)
            // Margins are set in XML to match border width
            // Text size is set programmatically based on screen dimensions
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

    private void showLoadingState() {
        runOnUiThread(() -> {
            // Set placeholder price text - using em dash to avoid zero values
            priceTextView.setText(""); // em dash instead of numeric 0

            // Hide change section, show loading message
            changeTextView.setVisibility(View.GONE);
            changePercentageTextView.setVisibility(View.GONE);
            errorMessageTextView.setVisibility(View.VISIBLE);

            // Set loading text with proper size (15vh) to match error display
            float changeSizeSp = (screenHeight * 0.15f) / density;
            errorMessageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, changeSizeSp);
            errorMessageTextView.setText("LOADING");

            // Set colors based on theme
            if (nightMode) {
                errorMessageTextView.setTextColor(Color.parseColor("#6B6C6C")); // Gray color for night mode
            } else {
                errorMessageTextView.setTextColor(Color.parseColor("#000000")); // Black color for day mode
            }

            // Mark loading state as visible
            isLoadingStateVisible = true;
        });
    }

    private void hideLoadingState() {
        isFirstLoad = false; // First load completed

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - loadingStartTime;
        long remainingTime = MIN_LOADING_DURATION_MS - elapsedTime;

        if (isLoadingStateVisible) {
            if (elapsedTime >= MIN_LOADING_DURATION_MS) {
                // Minimum duration already elapsed, hide immediately
                isLoadingStateVisible = false;
                runOnUiThread(() -> {
                    errorMessageTextView.setVisibility(View.GONE);
                    changeTextView.setVisibility(View.VISIBLE);
                    changePercentageTextView.setVisibility(View.VISIBLE);
                });
            } else {
                // Schedule hide after the minimum duration
                handler.postDelayed(() -> {
                    isLoadingStateVisible = false;
                    runOnUiThread(() -> {
                        errorMessageTextView.setVisibility(View.GONE);
                        changeTextView.setVisibility(View.VISIBLE);
                        changePercentageTextView.setVisibility(View.VISIBLE);
                    });
                }, remainingTime);
            }
        }
    }

    private void startPriceUpdates() {
        updateClock();

        // Show loading state on first load
        if (isFirstLoad) {
            loadingStartTime = System.currentTimeMillis();
            showLoadingState();
        }

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

                // Show loading state when cache is not available (first load or cache expired)
                if (isFirstLoad) {
                    runOnUiThread(() -> {
                        loadingStartTime = System.currentTimeMillis();
                        showLoadingState();
                    });
                } else if (cachedData == null) {
                    // For subsequent loads with no cache, show loading too
                    runOnUiThread(() -> {
                        showLoadingState();
                    });
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
        runOnUiThread(() -> {
            // Hide loading state when showing error
            //hideLoadingState();

            // Set ERROR as price
            priceTextView.setText("ERROR");
            
            // Update font size for ERROR text to match the price text size calculation logic
            updatePriceTextSizeForError();

            // Hide change views, show error message
            changeTextView.setVisibility(View.GONE);
            changePercentageTextView.setVisibility(View.GONE);
            errorMessageTextView.setVisibility(View.VISIBLE);

            // Set error message text with proper size (15vh)
            errorMessageTextView.setText(message);
            float changeSizeSp = (screenHeight * 0.15f) / density;
            errorMessageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, changeSizeSp);

            // Set colors based on theme
            if (nightMode) {
                // ERROR - same color as price in night mode
                priceTextView.setTextColor(Color.parseColor("#e7dfc6"));
                // LOST CONNECTION - color for price down in night mode
                errorMessageTextView.setTextColor(Color.parseColor("#966211"));
            } else {
                // ERROR - same color as price in day mode
                priceTextView.setTextColor(Color.parseColor("#000000"));
                // LOST CONNECTION - color for price down in day mode
                errorMessageTextView.setTextColor(Color.parseColor("#FAA31B"));
            }
        });
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
                    // Hide loading state and show normal UI
                    hideLoadingState();

                    // Restore visibility: hide error message, show change views
                    errorMessageTextView.setVisibility(View.GONE);
                    changeTextView.setVisibility(View.VISIBLE);
                    changePercentageTextView.setVisibility(View.VISIBLE);

                        // Restore price text color based on theme
                        if (nightMode) {
                            priceTextView.setTextColor(Color.parseColor("#e7dfc6"));
                        } else {
                            priceTextView.setTextColor(Color.parseColor("#000000"));
                        }

                        // Format price with commas and add currency symbol
                        String priceString = String.format(Locale.US, "%,d", finalActualPrice);
                        String priceWithCurrency = priceString + finalCurrencySymbol;
                        priceTextView.setText(priceWithCurrency);

                        // Currency is now part of price text, so hide the separate currency view
                        currencyTextView.setVisibility(View.GONE);

                        // Defer font size calculation until view is measured
                        priceTextView.post(() -> {
                            int currentDigitCount = String.valueOf(finalActualPrice).length();
                            // Only recalculate font size when digit count changes (price crosses 10K, 100K, etc.)
                            if (currentDigitCount != cachedDigitCount) {
                                cachedDigitCount = currentDigitCount;
                                int availableWidth = getAvailableWidthForPriceText();
                                cachedFontSizeSp = calculateMaxFontSizeForDigitCount(currentDigitCount, availableWidth);
                            }
                            priceTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, cachedFontSizeSp);
                        });

                    // Calculate change direction and colors
                    String arrow = isPriceUp ? "▲" : "▼";
                    int color;
                    if (isPriceUp) {
                        color = nightMode ? Color.parseColor("#005e0d") : Color.parseColor("#32CD32");
                    } else {
                        color = nightMode ? Color.parseColor("#966211") : Color.parseColor("#FAA31B");
                    }

                    // Set change text with arrow: ▲ 123$
                    String changeText = arrow + String.format(Locale.US, "%,d", Math.abs(finalPriceChange)) + finalCurrencySymbol;
                    changeTextView.setText(changeText);
                    changeTextView.setTextColor(color);

                    // Set percentage text: ▲ 0.42%
                    String changePercentageText = arrow + finalPriceChangePercentage + "%";
                    changePercentageTextView.setText(changePercentageText);
                    changePercentageTextView.setTextColor(color);

                    // Set change text sizes to 15vh
                    float changeSizeSp = (screenHeight * 0.15f) / density;
                    changeTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, changeSizeSp);
                    changePercentageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, changeSizeSp);

                    // Update last update time
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    lastUpdateTextView.setText("Last price info: " + sdf.format(new Date()) + " CET");

                    // Note: Text size is defined in XML layouts (8sp) for consistent appearance across devices
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Calculate maximum font size that fits the price text using binary search.
     * Uses actual measurement with Paint to ensure text never overflows.
     * @param digitCount number of digits before decimal point in price
     * @param availableWidthPx available width in pixels for the text
     * @return maximum font size in SP units
     */
    private float calculateMaxFontSizeForDigitCount(int digitCount, int availableWidthPx) {
        // Build "worst case" text - all 9s to account for widest digits
        StringBuilder worstCasePrice = new StringBuilder();
        for (int i = 0; i < digitCount; i++) {
            worstCasePrice.append('9');
        }
        // Insert commas for thousands separators (e.g., 999999 -> 999,999)
        if (digitCount > 3) {
            worstCasePrice.insert(digitCount - 3, ',');
        }
        worstCasePrice.append('$');

        String worstCaseText = worstCasePrice.toString();

        android.util.Log.d("PriceActivity", "Calculating font size for digitCount=" + digitCount +
            ", worstCaseText='" + worstCaseText + "', availableWidth=" + availableWidthPx +
            ", screenHeight=" + screenHeight + ", density=" + density);

        // Create Paint object with Abril Fatface font for measurement
        Paint paint = new Paint();
        paint.setTypeface(abrilFatfaceFont);
        paint.setAntiAlias(true);

        // Binary search for maximum font size
        float lowSizePx = 1f; // Start with 1px
        float highSizePx = screenHeight * 0.70f; // Max 70% of screen height
        float bestSizePx = lowSizePx;

        Rect textBounds = new Rect();

        // Binary search with 25 iterations for precision
        for (int i = 0; i < 25; i++) {
            float midSizePx = (lowSizePx + highSizePx) / 2f;
            paint.setTextSize(midSizePx);
            paint.getTextBounds(worstCaseText, 0, worstCaseText.length(), textBounds);

            float measuredWidth = textBounds.width();

            if (measuredWidth <= availableWidthPx) {
                // This size fits, try larger
                bestSizePx = midSizePx;
                lowSizePx = midSizePx;
            } else {
                // This size is too big, try smaller
                highSizePx = midSizePx;
            }
        }

        android.util.Log.d("PriceActivity", "Before margins: bestSizePx=" + bestSizePx);

        // Add small safety margin (95% of calculated size)
        bestSizePx *= 0.95f;

        // Limit to max 60vh (leave room for other UI elements)
        float maxSizeFromHeight = screenHeight * 0.60f;
        bestSizePx = Math.min(bestSizePx, maxSizeFromHeight);

        android.util.Log.d("PriceActivity", "Final font size: " + (bestSizePx / density) + "sp");

        // Convert pixels to SP units
        return bestSizePx / density;
    }

    /**
     * Get available width for price text considering screen width, padding and margins.
     * This gives the actual width that text can occupy.
     */
    private int getAvailableWidthForPriceText() {
        // Use real screen width with minimal 5% safety margin
        // Border is handled by the layout margins, not subtracted here
        int availableWidth = (int) (screenWidth * 0.95f); // 5% safety margin

        android.util.Log.d("PriceActivity", "getAvailableWidth: screenWidth=" + screenWidth +
            ", available=" + availableWidth + " (95% of screen)");

        return Math.max(availableWidth, 300); // Minimum 300px fallback
    }
    
    private void updatePriceTextSizeForError() {
        // Use the same font size calculation logic as for normal prices
        // Calculate font size for "ERROR" text (5 characters)
        int availableWidth = getAvailableWidthForPriceText();
        float fontSize = calculateMaxFontSizeForError(availableWidth);
        
        // Reduce font size by 5% for ERROR text
        fontSize *= 0.95f;
        
        // Apply the font size to priceTextView
        priceTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
    }
    
    private float calculateMaxFontSizeForError(int availableWidthPx) {
        // For "ERROR" text (5 characters)
        Paint paint = new Paint();
        paint.setTypeface(abrilFatfaceFont);
        paint.setAntiAlias(true);
        
        // Binary search for maximum font size
        float lowSizePx = 1f; // Start with 1px
        float highSizePx = screenHeight * 0.70f; // Max 70% of screen height
        float bestSizePx = lowSizePx;
        
        Rect textBounds = new Rect();
        
        // Binary search with 25 iterations for precision
        for (int i = 0; i < 25; i++) {
            float midSizePx = (lowSizePx + highSizePx) / 2f;
            paint.setTextSize(midSizePx);
            paint.getTextBounds("ERROR", 0, 5, textBounds);
            
            float measuredWidth = textBounds.width();
            
            if (measuredWidth <= availableWidthPx) {
                // This size fits, try larger
                bestSizePx = midSizePx;
                lowSizePx = midSizePx;
            } else {
                // This size is too big, try smaller
                highSizePx = midSizePx;
            }
        }
        
        // Add small safety margin (95% of calculated size)
        bestSizePx *= 0.95f;
        
        // Limit to max 60vh (leave room for other UI elements)
        float maxSizeFromHeight = screenHeight * 0.60f;
        bestSizePx = Math.min(bestSizePx, maxSizeFromHeight);
        
        // Convert pixels to SP units
        return bestSizePx / density;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}
