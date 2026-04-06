# BTC Price Checker - Android Project Summary

## Project Overview

**Purpose**: Android application for displaying live Bitcoin price from Coinbase API, optimized for continuous display on dedicated devices (e-waste/e-ink readers, old phones as tickers).

**Key Characteristics**:
- Minimalist design with large, readable numbers
- Three responsive layouts based on screen aspect ratio
- Adaptive font sizing based on price digit count
- Day/night mode support
- Landscape orientation only for price display

---

## Project Structure

```
btc-price-checker-android/
├── app/
│   ├── build.gradle                           # App-level build config
│   └── src/main/
│       ├── AndroidManifest.xml               # App manifest
│       ├── java/com/example/btcpricechecker/
│       │   ├── HomeActivity.java              # Currency selection screen
│       │   └── PriceActivity.java             # Main price display (671 lines)
│       └── res/
│           ├── layout/
│           │   ├── activity_home.xml          # Home screen (portrait)
│           │   ├── activity_price_tablet.xml  # Tablet layout (4:3)
│           │   ├── activity_price_wide.xml    # Wide layout (16:9)
│           │   └── activity_price_ultrawide.xml # Ultrawide layout (20:9)
│           ├── drawable/
│           │   ├── day_border.xml             # 10dp orange border
│           │   ├── day_border_tablet.xml      # 15dp orange border
│           │   ├── btc_logo.xml               # Bitcoin logo vector
│           │   ├── btc_logo_circle.xml        # Circular BTC logo
│           │   ├── night_link_bg.xml          # Night mode button bg
│           │   ├── ic_launcher_foreground.xml # App icon foreground
│           │   └── ic_launcher_background.xml # App icon background
│           ├── font/
│           │   └── abril_fatface.ttf          # Display font
│           ├── values/
│           │   ├── colors.xml                 # Color definitions
│           │   ├── strings.xml                # String resources
│           │   ├── styles.xml                 # App themes
│           │   ├── dimens.xml                 # Dimension values
│           │   └── attrs.xml                  # Custom attributes
│           └── mipmap-anydpi-v26/
│               ├── ic_launcher.xml            # Adaptive icon
│               └── ic_launcher_round.xml        # Rounded adaptive icon
└── build.gradle                               # Project-level config
```

---

## Core Components

### 1. Activities

#### HomeActivity.java (51 lines)
**Purpose**: Entry point - currency and mode selection

**Key Elements**:
- `CardView usdCard` / `eurCard`: Day mode currency selection
- `TextView nightUsdLink` / `nightEurLink`: Night mode quick links
- `startPriceActivity(String currency, boolean isNight)`: Launches PriceActivity with extras

**Flow**: User selects currency → PriceActivity launched with currency + nightMode flags

#### PriceActivity.java (671 lines)
**Purpose**: Main price display with fullscreen ticker

**State Management**:
```java
String currentCurrency = "USD";           // USD or EUR
boolean nightMode = false;                // Day/night mode
Timer timer;                             // 30-second update timer
Handler handler;                         // Main thread communication

// Caching
JSONObject cachedUsdData / cachedEurData;
long usdLastUpdate / eurLastUpdate;      // 10-second cache

// Font size caching
int cachedDigitCount = -1;               // -1 = not calculated
float cachedFontSizeSp = -1f;
```

**UI Elements**:
```java
FrameLayout rootLayout;                  // Main container
View borderView;                         // Orange border (day mode only)
TextView clockTextView;                  // 🕑HH:MM format
ImageView btcTextImage;                  // Bitcoin text logo
ImageView btcLogoImage;                  // Logo for ultrawide layout
TextView priceTextView;                  // Main price display
TextView changeTextView;                 // ▲+123$
TextView changePercentageTextView;       // ▲+3.42%
TextView lastUpdateTextView;            // Last price info: HH:MM:SS CET
TextView errorMessageTextView;          // ERROR / LOST CONNECTION
```

---

### 2. Layout System (3 Responsive Variants)

**Aspect Ratio Detection** (in onCreate):
```java
float aspectRatio = (float) actualWidth / actualHeight;

if (aspectRatio <= 1.6f) {
    layoutRes = R.layout.activity_price_tablet;      // TABLET
} else if (aspectRatio <= 1.9f) {
    layoutRes = R.layout.activity_price_wide;        // WIDE
} else {
    layoutRes = R.layout.activity_price_ultrawide;    // ULTRAWIDE
}
```

#### Layout Weight Distribution

**TABLET (activity_price_tablet.xml)**:
| Element | Weight | Notes |
|---------|--------|-------|
| clockSpacer | 15 | Space for overlaid clock |
| btcTextBlock | 15 | Bitcoin text logo |
| priceContainer | 49 | Main price display |
| changeBlock | 17 | Change indicators |
| lastUpdateBlock | 4 | Timestamp in border |
| **TOTAL** | **100** | |

**WIDE (activity_price_wide.xml)**:
| Element | Weight | Notes |
|---------|--------|-------|
| clockSpacer | 15 | Space for overlaid clock |
| btcTextBlock | 10 | Bitcoin text logo |
| priceContainer | 49 | Main price display |
| changeBlock | 19 | Change indicators |
| lastUpdateBlock | 7 | Timestamp in border |
| **TOTAL** | **100** | |

**ULTRAWIDE (activity_price_ultrawide.xml)**:
| Element | Weight | Notes |
|---------|--------|-------|
| clockSpacer | 13 | Space for clock + logo |
| btcTextBlock | 9 | Hidden (gone), logo in clockBlock |
| priceContainer | 55 | Main price (enlarged) |
| changeBlock | 16 | Change indicators |
| lastUpdateBlock | 7 | Timestamp in border |
| **TOTAL** | **100** | |

**Key Differences**:
- **Tablet**: 15dp orange border, Bitcoin logo top-left
- **Wide**: 10dp border, Bitcoin logo top-left, clock top-right
- **Ultrawide**: Bitcoin logo in clock row (space-between), clock on right

---

### 3. Dynamic Font Sizing System

**Problem**: Price must fit screen width regardless of digit count (4-7+ digits)

**Solution**: Binary search with Paint.measureText()

```java
private float calculateMaxFontSizeForDigitCount(int digitCount, int availableWidthPx) {
    // Build "worst case" text with all 9s (widest digit)
    String worstCaseText = buildWorstCasePrice(digitCount); // e.g., "999,999$"
    
    // Binary search for max font size
    float lowSizePx = 1f;
    float highSizePx = screenHeight * 0.70f;
    float bestSizePx = lowSizePx;
    
    for (int i = 0; i < 25; i++) {
        float midSizePx = (lowSizePx + highSizePx) / 2f;
        paint.setTextSize(midSizePx);
        paint.getTextBounds(worstCaseText, 0, worstCaseText.length(), textBounds);
        
        if (textBounds.width() <= availableWidthPx) {
            bestSizePx = midSizePx;
            lowSizePx = midSizePx;
        } else {
            highSizePx = midSizePx;
        }
    }
    
    // Apply safety margins
    bestSizePx *= 0.95f;                    // 5% safety margin
    bestSizePx = min(bestSizePx, screenHeight * 0.60f);  // Max 60vh
    
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

---

### 4. Data Layer

**API**: Coinbase Exchange API
- **Endpoint**: `https://api.exchange.coinbase.com/products/BTC-{currency}/stats`
- **Response Fields**:
  - `open`: Opening price (24h)
  - `last`: Current price
  - `high`: 24h high
  - `low`: 24h low
  - `volume`: Trading volume

**Caching Logic**:
```java
static final int PRICE_UPDATE_INTERVAL = 10; // seconds

// Check cache before fetching
if (cachedData != null && (currentTime - lastUpdate) < PRICE_UPDATE_INTERVAL) {
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

---

### 5. Theme System

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

---

### 6. Layout Adjustment Methods

Each layout has programmatic adjustments after inflation:

#### applyTabletLayout()
- BTC text: 4vw left margin
- Clock: 1.5vw top padding, 3vw right
- Last update: Margins in XML

#### applyWideLayout()
- Same as tablet but with 10dp border
- Clock: 1.5vw top, 3vw right

#### applyUltraWideLayout()
- Bitcoin logo height matches clock text height
- Clock block: 3vw horizontal, 1.5vw top padding
- Logo positioned via space-between in LinearLayout

---

### 7. Error Handling

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

---

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

---

## Refactoring Opportunities

### 1. Architecture Patterns
- **Current**: All logic in single Activity (671 lines)
- **Suggested**: MVVM with ViewModel + Repository pattern
- **Split**: Data layer, UI layer, Domain layer

### 2. Network Layer
- **Current**: Raw HttpURLConnection in Activity
- **Suggested**: Retrofit + OkHttp with coroutines/RxJava
- **Benefits**: Better error handling, caching, testability

### 3. Layout System
- **Current**: 3 separate XML files with weight-based layouts
- **Suggested**: ConstraintLayout with dimension ratios
- **Benefits**: Flatten hierarchy, better performance

### 4. Font Calculation
- **Current**: Binary search with Paint (25 iterations)
- **Suggested**: Pre-calculate sizes or use AutoSizeTextView
- **Benefits**: Simpler code, native optimization

### 5. State Management
- **Current**: Multiple boolean flags and manual caching
- **Suggested**: StateFlow/LiveData with data classes
- **Benefits**: Reactive UI, lifecycle-aware

### 6. Theming
- **Current**: Manual color setting in applyTheme()
- **Suggested**: Theme/Style system with attributes
- **Benefits**: Easier maintenance, consistent design

### 7. Testing
- **Current**: No tests
- **Suggested**: Unit tests for calculation logic, UI tests with Espresso
- **Mock**: Fake API responses for testing

### 8. Accessibility
- **Current**: Minimal accessibility support
- **Suggested**: Content descriptions, screen reader support

### 9. Internationalization
- **Current**: Hardcoded English strings
- **Suggested**: Full strings.xml localization

### 10. Code Organization
- **Current**: Single package, single activity responsibility
- **Suggested**:
  ```
  com.example.btcpricechecker/
  ├── ui/
  │   ├── home/
  │   ├── price/
  │   └── common/
  ├── data/
  │   ├── api/
  │   ├── repository/
  │   └── model/
  ├── domain/
  │   ├── usecase/
  │   └── model/
  └── utils/
  ```

---

## Build Configuration

**minSdk**: 27 (Android 8.1)
**targetSdk**: 34 (Android 14)
**compileSdk**: 34

**Permissions**:
- `INTERNET` - API calls
- `ACCESS_NETWORK_STATE` - Connection checking

---

## Notes for Refactoring

1. **Preserve exact behavior**: Font calculation must remain pixel-perfect
2. **Maintain responsive layouts**: All 3 aspect ratios must work
3. **Keep caching**: 10-second cache + 30-second updates
4. **Night mode parity**: Visual appearance must match current
5. **Fullscreen immersive**: FLAG_KEEP_SCREEN_ON + SYSTEM_UI_FLAG_IMMERSIVE_STICKY
6. **Error recovery**: Current error→success flow must be preserved
