# BTC Price Checker - Android

Android application for displaying the current Bitcoin price from Coinbase API, optimized for continuous display on dedicated devices.

## Overview

A minimalist Bitcoin price ticker designed for landscape orientation displays. Features large, readable numbers optimized for viewing from a distance. The app automatically selects one of three responsive layouts based on the device's screen aspect ratio.

## Features

- **Real-time price updates** every 30 seconds from Coinbase API
- **Three adaptive layouts** based on screen aspect ratio:
  - **Tablet** (≤ 1.6, e.g., 4:3) - 15dp orange border
  - **Wide** (1.6 - 1.9, e.g., 16:9) - 10dp orange border
  - **Ultrawide** (> 1.9, e.g., 20:9) - 10dp border with logo beside clock
- **Dynamic font sizing** based on the number of price digits
- **Day and night mode** support
- **Currency selection**: USD or EUR
- **Fullscreen display** with no system UI distractions
- **Adaptive font calculation** using binary search with Paint measurement

## Adaptive Font Sizing

The app calculates the optimal font size for the Bitcoin price based on:
- Number of price digits (4, 5, 6, 7+)
- Worst-case width measurement (using all 9s, the widest digit)
- Binary search with `Paint.measureText()` for precise fitting
- Caching to recalculate only when price magnitude changes
- Available width is 95% of actual screen dimensions

## Requirements

- Android 8.1 (API level 27) or higher
- Internet connection
- Landscape orientation recommended

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/btcpricechecker/
│   │   ├── PriceActivity.java       # Main price display activity
│   │   └── HomeActivity.java        # Currency selection screen
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_price_tablet.xml     # Tablet layout (4:3)
│   │   │   ├── activity_price_wide.xml       # Wide layout (16:9)
│   │   │   ├── activity_price_ultrawide.xml  # Ultrawide layout (20:9)
│   │   │   └── activity_home.xml             # Home/currency selection
│   │   ├── drawable/
│   │   │   ├── day_border.xml       # 10dp orange border
│   │   │   └── day_border_tablet.xml # 15dp orange border
│   │   ├── font/
│   │   │   └── abril_fatface.ttf    # Display font
│   │   └── values/
│   │       ├── colors.xml           # Color definitions
│   │       ├── strings.xml          # String resources
│   │       └── attrs.xml            # Custom attributes
│   └── AndroidManifest.xml
```

## API

The application uses Coinbase Exchange API:
- **Endpoint**: `https://api.exchange.coinbase.com/products/BTC-{currency}/stats`
- **Supported currencies**: USD, EUR
- **Update interval**: 30 seconds
- **Data caching**: 10 seconds to minimize API calls

## Building

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on a device or emulator

## Layout System

The app dynamically selects the appropriate layout file based on the calculated aspect ratio of the device screen:

```java
if (aspectRatio <= 1.6f) {
    // Use activity_price_tablet.xml
} else if (aspectRatio <= 1.9f) {
    // Use activity_price_wide.xml
} else {
    // Use activity_price_ultrawide.xml
}
```

Each layout is optimized for its specific aspect ratio while maintaining consistent visual appearance.

## License

