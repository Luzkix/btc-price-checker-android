# BTC Price Checker - Android

Android aplikace pro zobrazení aktuální ceny Bitcoinu z Coinbase API.

## Funkce

- Zobrazení aktuální ceny Bitcoinu v USD nebo EUR
- Automatická aktualizace každých 30 sekund
- Zobrazení změny ceny a procentuálního nárůstu/poklesu
- Podpora nočního režimu
- Orientace na šířku (landscape)

## Požadavky

- Android 8.1 (API level 27) nebo vyšší
- Přístup k internetu

## Sestavení

1. Otevřete projekt v Android Studiu
2. Synchronizujte Gradle
3. Sestavte a spusťte aplikaci

## Struktura projektu

```
app/
├── src/main/
│   ├── java/com/example/btcpricechecker/
│   │   └── MainActivity.java          # Hlavní aktivita
│   ├── res/
│   │   ├── layout/
│   │   │   └── activity_main.xml      # Layout pro denní režim
│   │   ├── layout-night/
│   │   │   └── activity_main.xml      # Layout pro noční režim
│   │   ├── values/
│   │   │   ├── colors.xml             # Barvy
│   │   │   ├── strings.xml            # Texty
│   │   │   ├── styles.xml             # Styly
│   │   │   ├── dimens.xml             # Rozměry
│   │   │   └── attrs.xml              # Vlastní atributy
│   │   ├── drawable/                  # Ikony
│   │   └── mipmap*/                   # Ikony aplikace
│   └── AndroidManifest.xml           # Manifest aplikace
```

## API

Aplikace používá Coinbase API:
- Endpoint: `https://api.exchange.coinbase.com/products/BTC-{currency}/stats`
- Měny: USD, EUR

## License