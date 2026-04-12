package com.example.btcpricechecker;

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
    private BitcoinApiService apiService;

    private JSONObject cachedUsdData = null;
    private JSONObject cachedEurData = null;
    private long usdLastUpdate = 0;
    private long eurLastUpdate = 0;

    // UI Elements
    private FrameLayout rootLayout;
    private View borderView;
    private TextView clockTextView;
    private ImageView btcTextImage;
    private ImageView btcLogoImage;
    private LinearLayout btcTextBlock;
    private TextView priceTextView;
    private TextView currencyTextView;
    private TextView changeTextView;
    private TextView changePercentageTextView;
    private TextView lastUpdateTextView;
    private LinearLayout lastUpdateBlock;
    private LinearLayout clockBlock;
    private TextView errorMessageTextView;

    // Font
    private Typeface abrilFatfaceFont;

    // Screen dimensions
    private int screenHeight;
    private int screenWidth;
    private float density;

    // Config values (loaded from resources)
    private int priceUpdateIntervalSeconds;
    private int timerIntervalMs;
    private int minLoadingDurationMs;
    private int fontBinarySearchIterations;
    private int minAvailableWidthPx;

    // Font size caching
    private int cachedDigitCount = -1;
    private float cachedFontSizeSp = -1f;

    // Loading state
    private boolean isFirstLoad = true;
    private long loadingStartTime = 0;
    private boolean isLoadingStateVisible = false;

    // Aspect ratio category
    private enum AspectRatioCategory { TABLET, WIDE, ULTRAWIDE }
    private AspectRatioCategory aspectRatioCategory = AspectRatioCategory.WIDE;

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
        if (aspectRatio <= getTabletMaxAspectRatio()) {
            aspectRatioCategory = AspectRatioCategory.TABLET;
            layoutRes = R.layout.activity_price_tablet;
        } else if (aspectRatio <= getWideMaxAspectRatio()) {
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

        // Initialize API service
        apiService = new BitcoinApiService();

        // Load config from resources
        loadConfig();

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
        if (abrilFatfaceFont != null) {
            clockTextView.setTypeface(abrilFatfaceFont, Typeface.BOLD);
            priceTextView.setTypeface(abrilFatfaceFont, Typeface.BOLD);
            currencyTextView.setTypeface(abrilFatfaceFont, Typeface.BOLD);
            changeTextView.setTypeface(abrilFatfaceFont, Typeface.BOLD);
            changePercentageTextView.setTypeface(abrilFatfaceFont, Typeface.BOLD);
        }
    }

    private void loadConfig() {
        priceUpdateIntervalSeconds = getResources().getInteger(R.integer.price_update_interval_seconds);
        timerIntervalMs = getResources().getInteger(R.integer.timer_interval_ms);
        minLoadingDurationMs = getResources().getInteger(R.integer.min_loading_duration_ms);
        fontBinarySearchIterations = getResources().getInteger(R.integer.font_binary_search_iterations);
        minAvailableWidthPx = getResources().getInteger(R.integer.min_available_width_px);
    }

    private float getClockSizePercent() {
        return getResources().getInteger(R.integer.clock_size_percent) / 100f;
    }

    private float getChangeSizePercent() {
        return getResources().getInteger(R.integer.change_size_percent) / 100f;
    }

    private float getAvailableWidthPercent() {
        return getResources().getInteger(R.integer.available_width_percent) / 100f;
    }

    private float getFontSafetyMargin() {
        return getResources().getInteger(R.integer.font_safety_margin) / 100f;
    }

    private float getFontMaxHeightPercent() {
        return getResources().getInteger(R.integer.font_max_height_percent) / 100f;
    }

    private float getFontSearchMaxHeightPercent() {
        return getResources().getInteger(R.integer.font_search_max_height_percent) / 100f;
    }

    private float getBtcTextMarginPermil() {
        return getResources().getInteger(R.integer.btc_text_margin_start_permil) / 1000f;
    }

    private float getClockTopPaddingPermil() {
        return getResources().getInteger(R.integer.clock_top_padding_permil) / 1000f;
    }

    private float getClockHorizontalPaddingPermil() {
        return getResources().getInteger(R.integer.clock_horizontal_padding_permil) / 1000f;
    }

    private float getTabletMaxAspectRatio() {
        return getResources().getInteger(R.integer.tablet_max_aspect_ratio_x10) / 10f;
    }

    private float getWideMaxAspectRatio() {
        return getResources().getInteger(R.integer.wide_max_aspect_ratio_x10) / 10f;
    }

    private float getNightModeLogoAlpha() {
        return getResources().getInteger(R.integer.night_mode_logo_alpha) / 100f;
    }

    private float getDayModeLogoAlpha() {
        return getResources().getInteger(R.integer.day_mode_logo_alpha) / 100f;
    }

    private void applyTheme() {
        if (nightMode) {
            rootLayout.setBackgroundColor(getColor(R.color.price_night_bg));
            if (borderView != null) borderView.setVisibility(View.GONE);

            clockTextView.setTextColor(getColor(R.color.clock_night_color));
            priceTextView.setTextColor(getColor(R.color.price_night_color));
            currencyTextView.setTextColor(getColor(R.color.currency_night_color));
            lastUpdateTextView.setTextColor(getColor(R.color.last_update_night));

            setLogoAlpha(getNightModeLogoAlpha());
        } else {
            rootLayout.setBackgroundColor(getColor(R.color.price_day_bg));
            if (borderView != null) borderView.setVisibility(View.VISIBLE);

            clockTextView.setTextColor(getColor(R.color.clock_day_color));
            priceTextView.setTextColor(getColor(R.color.price_day_color));
            currencyTextView.setTextColor(getColor(R.color.currency_day_color));
            lastUpdateTextView.setTextColor(getColor(R.color.last_update_day));

            setLogoAlpha(getDayModeLogoAlpha());
        }
    }

    private void setLogoAlpha(float alpha) {
        if (btcTextImage != null) btcTextImage.setAlpha(alpha);
        if (btcLogoImage != null) btcLogoImage.setAlpha(alpha);
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
        applySharedLayoutAdjustments();
    }

    private void applyWideLayout() {
        applySharedLayoutAdjustments();
    }

    private void applySharedLayoutAdjustments() {
        if (btcTextBlock != null) {
            LinearLayout.LayoutParams btcParams = (LinearLayout.LayoutParams) btcTextBlock.getLayoutParams();
            btcParams.setMarginStart((int) (screenWidth * getBtcTextMarginPermil()));
            btcTextBlock.setLayoutParams(btcParams);
        }

        if (clockBlock != null) {
            int topPadding = (int) (screenWidth * getClockTopPaddingPermil());
            int rightPadding = (int) (screenWidth * getClockHorizontalPaddingPermil());
            clockBlock.setPadding(0, topPadding, rightPadding, 0);
        }
    }

    private void applyUltraWideLayout() {
        if (btcLogoImage != null && clockBlock != null) {
            float clockSizeSp = (screenHeight * getClockSizePercent()) / density;
            float clockSizePx = clockSizeSp * density;
            int logoHeight = (int) clockSizePx;

            LinearLayout.LayoutParams logoParams = (LinearLayout.LayoutParams) btcLogoImage.getLayoutParams();
            logoParams.height = logoHeight;
            logoParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            btcLogoImage.setLayoutParams(logoParams);

            int horizontalPadding = (int) (screenWidth * getClockHorizontalPaddingPermil());
            int topPadding = (int) (screenWidth * getClockTopPaddingPermil());
            clockBlock.setPadding(horizontalPadding, topPadding, horizontalPadding, 0);
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
            priceTextView.setText("");

            changeTextView.setVisibility(View.GONE);
            changePercentageTextView.setVisibility(View.GONE);
            errorMessageTextView.setVisibility(View.VISIBLE);

            float changeSizeSp = (screenHeight * getChangeSizePercent()) / density;
            errorMessageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, changeSizeSp);
            errorMessageTextView.setText("LOADING");

            if (nightMode) {
                errorMessageTextView.setTextColor(getColor(R.color.clock_night_color));
            } else {
                errorMessageTextView.setTextColor(getColor(R.color.price_day_color));
            }

            isLoadingStateVisible = true;
        });
    }

    private void hideLoadingState() {
        isFirstLoad = false;

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - loadingStartTime;
        long remainingTime = minLoadingDurationMs - elapsedTime;

        if (isLoadingStateVisible) {
            if (elapsedTime >= minLoadingDurationMs) {
                isLoadingStateVisible = false;
                runOnUiThread(() -> {
                    errorMessageTextView.setVisibility(View.GONE);
                    changeTextView.setVisibility(View.VISIBLE);
                    changePercentageTextView.setVisibility(View.VISIBLE);
                });
            } else {
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
        }, 0, timerIntervalMs);
    }

    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = sdf.format(new Date());
        clockTextView.setText("🕑" + timeStr);

        float clockSizeSp = (screenHeight * getClockSizePercent()) / density;
        clockTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, clockSizeSp);
    }

    private void fetchBitcoinPrice() {
        long currentTime = System.currentTimeMillis() / 1000;
        long lastUpdate = currentCurrency.equals("USD") ? usdLastUpdate : eurLastUpdate;
        JSONObject cachedData = currentCurrency.equals("USD") ? cachedUsdData : cachedEurData;

        if (cachedData != null && (currentTime - lastUpdate) < priceUpdateIntervalSeconds) {
            updatePriceUI(cachedData);
            return;
        }

        if (isFirstLoad || cachedData == null) {
            loadingStartTime = System.currentTimeMillis();
            showLoadingState();
        }

        apiService.fetchPrice(currentCurrency, new BitcoinApiService.PriceCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (currentCurrency.equals("USD")) {
                    cachedUsdData = json;
                    usdLastUpdate = currentTime;
                } else {
                    cachedEurData = json;
                    eurLastUpdate = currentTime;
                }
                updatePriceUI(json);
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> showError(message));
            }
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            priceTextView.setText("ERROR");
            updatePriceTextSizeForError();

            changeTextView.setVisibility(View.GONE);
            changePercentageTextView.setVisibility(View.GONE);
            errorMessageTextView.setVisibility(View.VISIBLE);

            errorMessageTextView.setText(message);
            float changeSizeSp = (screenHeight * getChangeSizePercent()) / density;
            errorMessageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, changeSizeSp);

            if (nightMode) {
                priceTextView.setTextColor(getColor(R.color.price_night_color));
                errorMessageTextView.setTextColor(getColor(R.color.price_down_night));
            } else {
                priceTextView.setTextColor(getColor(R.color.price_day_color));
                errorMessageTextView.setTextColor(getColor(R.color.price_down_day));
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

                        if (nightMode) {
                            priceTextView.setTextColor(getColor(R.color.price_night_color));
                        } else {
                            priceTextView.setTextColor(getColor(R.color.price_day_color));
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

                    String arrow = isPriceUp ? "▲" : "▼";
                    int changeColor = isPriceUp
                        ? (nightMode ? getColor(R.color.price_up_night) : getColor(R.color.price_up_day))
                        : (nightMode ? getColor(R.color.price_down_night) : getColor(R.color.price_down_day));

                    // Set change text with arrow: ▲ 123$
                    String changeText = arrow + String.format(Locale.US, "%,d", Math.abs(finalPriceChange)) + finalCurrencySymbol;
                    changeTextView.setText(changeText);
                    changeTextView.setTextColor(changeColor);

                    // Set percentage text: ▲ 0.42%
                    String changePercentageText = arrow + finalPriceChangePercentage + "%";
                    changePercentageTextView.setText(changePercentageText);
                    changePercentageTextView.setTextColor(changeColor);

                    float changeSizeSp = (screenHeight * getChangeSizePercent()) / density;
                    changeTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, changeSizeSp);
                    changePercentageTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, changeSizeSp);

                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    lastUpdateTextView.setText("Last price info: " + sdf.format(new Date()) + " CET");
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
        StringBuilder worstCasePrice = new StringBuilder();
        for (int i = 0; i < digitCount; i++) {
            worstCasePrice.append('9');
        }
        if (digitCount > 3) {
            worstCasePrice.insert(digitCount - 3, ',');
        }
        worstCasePrice.append('$');

        android.util.Log.d("PriceActivity", "Calculating font size for digitCount=" + digitCount +
            ", worstCaseText='" + worstCasePrice + "', availableWidth=" + availableWidthPx);

        return calculateFontSize(worstCasePrice.toString(), availableWidthPx);
    }

    /**
     * Get available width for price text considering screen width, padding and margins.
     * This gives the actual width that text can occupy.
     */
    private int getAvailableWidthForPriceText() {
        int availableWidth = (int) (screenWidth * getAvailableWidthPercent());

        android.util.Log.d("PriceActivity", "getAvailableWidth: screenWidth=" + screenWidth +
            ", available=" + availableWidth);

        return Math.max(availableWidth, minAvailableWidthPx);
    }
    
    private void updatePriceTextSizeForError() {
        int availableWidth = getAvailableWidthForPriceText();
        float fontSize = calculateMaxFontSizeForError(availableWidth);
        fontSize *= getFontSafetyMargin();
        priceTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
    }
    
    private float calculateMaxFontSizeForError(int availableWidthPx) {
        return calculateFontSize("ERROR", availableWidthPx);
    }

    private float calculateFontSize(String text, int availableWidthPx) {
        Paint paint = new Paint();
        paint.setTypeface(abrilFatfaceFont);
        paint.setAntiAlias(true);

        float lowSizePx = 1f;
        float highSizePx = screenHeight * getFontSearchMaxHeightPercent();
        float bestSizePx = lowSizePx;

        Rect textBounds = new Rect();

        for (int i = 0; i < fontBinarySearchIterations; i++) {
            float midSizePx = (lowSizePx + highSizePx) / 2f;
            paint.setTextSize(midSizePx);
            paint.getTextBounds(text, 0, text.length(), textBounds);

            if (textBounds.width() <= availableWidthPx) {
                bestSizePx = midSizePx;
                lowSizePx = midSizePx;
            } else {
                highSizePx = midSizePx;
            }
        }

        bestSizePx *= getFontSafetyMargin();

        float maxSizeFromHeight = screenHeight * getFontMaxHeightPercent();
        bestSizePx = Math.min(bestSizePx, maxSizeFromHeight);

        android.util.Log.d("PriceActivity", "Final font size: " + (bestSizePx / density) + "sp");

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
