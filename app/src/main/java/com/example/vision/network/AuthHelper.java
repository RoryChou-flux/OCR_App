package com.example.vision.network;

import android.util.Log;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class AuthHelper {
    private static final String TAG = "AuthHelper";
    private static final String CHARS = "AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz0123456789";

    public static Map<String, String> getAuthHeaders(Map<String, String> reqData, String appId, String secret) {
        Map<String, String> headers = new HashMap<>();

        String randomStr = randomStr(16);
        String timestamp = String.valueOf(
                new Date().getTime() / 1000
        );

        headers.put("app-id", appId);
        headers.put("random-str", randomStr);
        headers.put("timestamp", timestamp);

        List<String> sortedKeys = new ArrayList<>();
        sortedKeys.addAll(reqData.keySet());
        sortedKeys.add("app-id");
        sortedKeys.add("random-str");
        sortedKeys.add("timestamp");
        Collections.sort(sortedKeys);

        StringBuilder preSignString = new StringBuilder();
        boolean first = true;
        for (String key : sortedKeys) {
            if (!first) {
                preSignString.append("&");
            }
            first = false;

            String value;
            if (key.equals("app-id")) {
                value = appId;
            } else if (key.equals("random-str")) {
                value = randomStr;
            } else if (key.equals("timestamp")) {
                value = timestamp;
            } else {
                value = reqData.get(key);
            }
            preSignString.append(key).append("=").append(value);
        }

        preSignString.append("&secret=").append(secret);

        String signStr = preSignString.toString();
        Log.d(TAG, "Sign string: " + signStr);

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(signStr.getBytes());
            StringBuilder sign = new StringBuilder();
            for (byte b : bytes) {
                sign.append(String.format("%02x", b));
            }
            headers.put("sign", sign.toString());

            Log.d(TAG, "Headers: " + headers);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "MD5 calculation failed", e);
        }

        return headers;
    }

    private static String randomStr(int length) {
        StringBuilder str = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            str.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return str.toString();
    }
}
