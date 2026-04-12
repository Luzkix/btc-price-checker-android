package com.example.btcpricechecker;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class BitcoinApiService {
    private static final String BASE_URL = "https://api.exchange.coinbase.com/products/BTC-";
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;

    public interface PriceCallback {
        void onSuccess(JSONObject data);
        void onError(String message);
    }

    public void fetchPrice(String currency, PriceCallback callback) {
        new Thread(() -> {
            try {
                String urlString = BASE_URL + currency + "/stats";
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);

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
                    callback.onSuccess(json);
                } else {
                    callback.onError("COINBASE API");
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                callback.onError("LOST CONNECTION");
            }
        }).start();
    }
}
