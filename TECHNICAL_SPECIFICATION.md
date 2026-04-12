# BTC Price Checker - Technical Specification

## Project Overview

**Purpose**: Android application for displaying live Bitcoin price from Coinbase API, optimized for continuous display on dedicated devices (e-waste/e-ink readers, old phones as tickers).

**Key Characteristics**:
- Minimalist design with large, readable numbers
- Three responsive layouts based on screen aspect ratio
- Adaptive font sizing based on price digit count
- Day/night mode support
- Landscape orientation only for price display

## Core Components

### Activities

#### HomeActivity.java
**Purpose**: Entry point - currency and mode selection

**Key Elements**:
- `CardView usdCard` / `eurCard`: Day mode currency selection
- `TextView nightUsdLink` / `nightEurLink`: Night mode quick links
- `startPriceActivity(String currency, boolean isNight)`: Launches PriceActivity with extras

#### PriceActivity.java
**Purpose**: Main price display with fullscreen ticker

**State Management**:
```java
String currentCurrency = "USD";           // USD or EUR
boolean nightMode = false;             // Day/night mode
Timer timer;                         // 30-second update timer
Handler handler;                     // Main thread communication
BitcoinApiService apiService;         // API communication

// Config (loaded from integers.xml)
int priceUpdateIntervalSeconds;
int timerIntervalMs;
int minLoadingDurationMs;
int fontBinarySearchIterations;

// Caching
JSONObject cachedUsdData / cachedEurData;
long usdLastUpdate / eurLastUpdate;   // 10-second cache

// Font size caching
int cachedDigitCount = -1;          // -1 = not calculated
float cachedFontSizeSp = -1f;
```

**UI Elements**:
```java
FrameLayout rootLayout;               // Main container
View borderView;                    // Orange border (day mode only)
TextView clockTextView;                 // 🕑HH:MM format
ImageView btcTextImage;               // Bitcoin text logo
ImageView btcLogoImage;               // Logo for ultrawide layout
TextView priceTextView;                // Main price display
TextView changeTextView;                // ▲123$
TextView changePercentageTextView;      // ▲0.42%
TextView lastUpdateTextView;         // Last price info: HH:MM:SS CET
TextView errorMessageTextView;        // ERROR / LOST CONNECTION
```

#### BitcoinApiService.java
**Purpose**: Network communication layer - fetches price data from Coinbase API

**Key Methods**:
- `fetchPrice(String currency, PriceCallback callback)`: Async API call
- `PriceCallback`: Interface with `onSuccess(JSONObject)` and `onError(String)`

### Layout System (3 Responsive Variants)

**Aspect Ratio Detection** (configurable in `integers.xml`):
```java
float aspectRatio = (float) actualWidth / actualHeight;

// Values from integers.xml: tablet_max_aspect_ratio_x10=16, wide_max_aspect_ratio_x10=19
if (aspectRatio <= getTabletMaxAspectRatio()) {      // 1.6
    layoutRes = R.layout.activity_price_tablet;      // TABLET
} else if (aspectRatio <= getWideMaxAspectRatio()) { // 1.9
    layoutRes = R.layout.activity_price_wide;         // WIDE
} else {
    layoutRes = R.layout.activity_price_ultrawide;   // ULTRAWIDE
}
```

### Dynamic Font Sizing System

**Problem**: Price must fit screen width regardless of digit count (4-7+ digits)

**Solution**: Binary search with Paint.measureText() (configurable via `integers.xml`)

```java
private float calculateMaxFontSizeForDigitCount(int digitCount, int availableWidthPx) {
    // Build "worst case" text with all 9s (widest digit)
    String worstCaseText = buildWorstCasePrice(digitCount); // e.g., "999,999$"
    
    // Values from integers.xml: font_search_max_height_percent=70, font_safety_margin=95, font_max_height_percent=60
    float highSizePx = screenHeight * getFontSearchMaxHeightPercent(); // 70%
    float bestSizePx = 1f;
    
    for (int i = 0; i < fontBinarySearchIterations; i++) { // 25 iterations
        float midSizePx = (1f + highSizePx) / 2f;
        paint.setTextSize(midSizePx);
        paint.getTextBounds(worstCaseText, 0, worstCaseText.length(), textBounds);
        
        if (textBounds.width() <= availableWidthPx) {
            bestSizePx = midSizePx;
            lowSizePx = midSizePx;
        } else {
            highSizePx = midSizePx;
        }
    }
    
    // Apply safety margins (95%) and max height (60%)
    bestSizePx *= getFontSafetyMargin();                    // 0.95f
    bestSizePx = min(bestSizePx, screenHeight * getFontMaxHeightPercent()); // 60%
    
    return bestSizePx / density;             // Convert to SP
}
```

**Caching Strategy**:
- Font size recalculated ONLY when `digitCount` changes (4→5, 5→6, etc.)
- Avoids expensive calculation on every price update

**Usage in updatePriceUI()**:
```java
priceTextView.post(() -> {
    int currentDigitCount = String.valueOf(finalActualPrice).length();
    if (currentDigitCount != cachedDigitCount) {
        cachedDigitCount = currentDigitCount;
        cachedFontSizeSp = calculateMaxFontSizeForDigitCount(currentDigitCount, availableWidth);
    }
    priceTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, cachedFontSizeSp);
});
```

### Data Layer

**API**: Coinbase Exchange API
**Endpoint**: `https://api.exchange.coinbase.com/products/BTC-{currency}/stats`

**Response Fields**:
  - `open`: Opening price (24h)
  - `last`: Current price
  - `high`: 24h high
  - `low`: 24h low
  - `volume`: Trading volume

**Caching Logic** (values from `integers.xml`):
```java
// Loaded in loadConfig() from integers.xml: price_update_interval_seconds=10
// Check cache before fetching
if (cachedData != null && (currentTime - lastUpdate) < priceUpdateIntervalSeconds) {
    updatePriceUI(cachedData);
    return;
}
```

**Price Calculation**:
```java
double open = json.getDouble("open");
double last = json.getDouble("last");
int actualPrice = (int) last;                                    // Truncate decimals
int priceChange = (int) (last - open);
double changePercentage = ((last / open) - 1) * 100;
```

### Theme System

**Day Mode Colors**:
| Element | Color | Hex |
|---------|-------|-----|
| Background | White | #FFFFFF |
| Price text | Black | #000000 |
| Border | Bitcoin Orange | #F7931A |
| Price up | Lime Green | #32CD32 |
| Price down | Orange | #FAA31B |
| Last update | White on border | #FFFFFF |

**Night Mode Colors**:
| Element | Color | Hex |
|---------|-------|-----|
| Background | Black | #000000 |
| Price text | Cream | #e7dfc6 |
| Border | Hidden (gone) | - |
| Clock | Dark Gray | #6B6C6C |
| Price up | Dark Green | #005e0d |
| Price down | Brown/Gold | #966211 |
| Error bg | Dark Brown | #966211 |

**Theme Application** (applyTheme()):
- Sets background color
- Shows/hides border view
- Applies text colors to all elements
- Sets alpha on logos (1.0 day, 0.7 night)

### Layout Adjustment Methods

Each layout has programmatic adjustments after inflation (values from `integers.xml`):

#### applyTabletLayout() / applyWideLayout()
- Uses shared `applySharedLayoutAdjustments()`:
  - BTC text: `btc_text_margin_start_permil` (40‰) left margin
  - Clock: `clock_top_padding_permil` (15‰) top, `clock_horizontal_padding_permil` (30‰) right

#### applyUltraWideLayout()
- Bitcoin logo height matches clock text height (`clock_size_percent` = 14%)
- Clock block: Uses same padding values as other layouts
- Logo positioned via space-between in LinearLayout

#### Helper Methods
All configurable via `integers.xml`:
```java
getClockSizePercent()           // 14 - clock text size
getChangeSizePercent()          // 15 - change text size
getAvailableWidthPercent()       // 95 - available width margin
getNightModeLogoAlpha()         // 70 - logo opacity in night mode
getDayModeLogoAlpha()           // 100 - logo opacity in day mode
```

### Error Handling

**Error Types**:
1. **API Error**: HTTP response != 200 → "COINBASE API"
2. **Connection Error**: Exception during fetch → "LOST CONNECTION"

**Error Display** (showError()):
```java
priceTextView.setText("ERROR");
changeTextView.setVisibility(View.GONE);
changePercentageTextView.setVisibility(View.GONE);
errorMessageTextView.setVisibility(View.VISIBLE);
errorMessageTextView.setText(message);  // "COINBASE API" or "LOST CONNECTION"
```

**Recovery**: On next successful fetch, error views are hidden and change views restored.

## Key Technical Details

### Font
- **Primary**: Abril Fatface (display font for prices)
- **Fallback**: sans-serif (last update timestamp)

### Screen Dimensions
- Uses `getRealMetrics()` to get physical screen size
- Accounts for display cutouts/notches
- Available width: 95% of screen width (5% safety margin)

### Threading
- Network requests: Background thread (`new Thread()`)
- UI updates: Main thread via `Handler` + `runOnUiThread()`
- Timer: 30-second intervals on background thread

### Dependencies
```gradle
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.9.0'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
```

## Build Configuration

**minSdk**: 27 (Android 8.1)
**targetSdk**: 34 (Android 14)
**compileSdk**: 34

**Permissions**:
- `INTERNET` - API calls
- `ACCESS_NETWORK_STATE` - Connection checking

## Notes for Refactoring

1. **Config parameters**: Most values are now in `integers.xml` - check there first before modifying code
2. **Preserve exact behavior**: Font calculation must remain pixel-perfect
3. **Maintain responsive layouts**: All 3 aspect ratios must work
4. **Keep caching**: 10-second cache + 30-second updates
5. **Night mode parity**: Visual appearance must match current
6. **Fullscreen immersive**: FLAG_KEEP_SCREEN_ON + SYSTEM_UI_FLAG_IMMERSIVE_STICKY
7. **Error recovery**: Current error→success flow must be preserved
8. **API separation**: Network logic is in `BitcoinApiService.java`, keep it decoupled
