package com.example.vision;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import androidx.annotation.NonNull;

public class DocumentHistoryManager {
    private static final String TAG = "DocumentHistoryManager";
    private static final String HISTORY_FILE = "document_history.json";

    public static class HistoryItem {
        private final String originalPath;
        private final String processedPath;
        private final String thumbnailPath;
        private final long timestamp;

        public HistoryItem(String originalPath, String processedPath, String thumbnailPath, long timestamp) {
            this.originalPath = originalPath;
            this.processedPath = processedPath;
            this.thumbnailPath = thumbnailPath;
            this.timestamp = timestamp;
        }

        public String getOriginalPath() { return originalPath; }
        public String getProcessedPath() { return processedPath; }
        public String getThumbnailPath() { return thumbnailPath; }
        public long getTimestamp() { return timestamp; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof HistoryItem)) return false;
            HistoryItem other = (HistoryItem) obj;
            return originalPath.equals(other.originalPath) &&
                    processedPath.equals(other.processedPath) &&
                    thumbnailPath.equals(other.thumbnailPath);
        }

        private JSONObject toJson() throws Exception {
            JSONObject json = new JSONObject();
            json.put("originalPath", originalPath);
            json.put("processedPath", processedPath);
            json.put("thumbnailPath", thumbnailPath);
            json.put("timestamp", timestamp);
            return json;
        }

        private static HistoryItem fromJson(JSONObject json) throws Exception {
            return new HistoryItem(
                    json.getString("originalPath"),
                    json.getString("processedPath"),
                    json.getString("thumbnailPath"),
                    json.getLong("timestamp")
            );
        }
    }

    public static boolean addHistory(@NonNull Context context, @NonNull HistoryItem item) {
        try {
            Log.d(TAG, "Starting addHistory...");
            List<HistoryItem> items = getAllHistory(context);
            Log.d(TAG, "Current history size: " + items.size());
            items.add(item);
            saveHistory(context, items);
            Log.d(TAG, "History saved. New size: " + items.size());
            Log.d(TAG, "History file location: " + new File(context.getFilesDir(), HISTORY_FILE).getAbsolutePath());
            return true;  // Success case
        } catch (Exception e) {
            Log.e(TAG, "Error adding history", e);
            return false;  // Failure case
        }
    }

    public static List<HistoryItem> getAllHistory(Context context) {
        try {
            File file = new File(context.getFilesDir(), HISTORY_FILE);
            Log.d(TAG, "Looking for history file at: " + file.getAbsolutePath());
            if (!file.exists()) {
                Log.d(TAG, "History file not found, returning empty list.");
                return new ArrayList<>();
            }

            StringBuilder jsonStr = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                jsonStr.append(line);
            }
            reader.close();

            Log.d(TAG, "File read successfully, length: " + jsonStr.length());

            List<HistoryItem> items = new ArrayList<>();
            JSONArray array = new JSONArray(jsonStr.toString());
            for (int i = 0; i < array.length(); i++) {
                items.add(HistoryItem.fromJson(array.getJSONObject(i)));
            }

            Collections.sort(items, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
            return items;

        } catch (Exception e) {
            Log.e(TAG, "Error getting history", e);
            return new ArrayList<>();
        }
    }

    public static void removeHistory(Context context, HistoryItem itemToRemove) {
        try {
            List<HistoryItem> items = getAllHistory(context);
            items.removeIf(item -> item.equals(itemToRemove));
            saveHistory(context, items);
        } catch (Exception e) {
            Log.e(TAG, "Error removing history", e);
        }
    }


    private static void saveHistory(Context context, List<HistoryItem> items) {
        try {
            JSONArray array = new JSONArray();
            for (HistoryItem item : items) {
                array.put(item.toJson());
            }

            File file = new File(context.getFilesDir(), HISTORY_FILE);
            Log.d(TAG, "Saving history to: " + file.getAbsolutePath());
            Log.d(TAG, "JSON content: " + array.toString());

            // 如果文件不存在，创建新文件
            if (!file.exists()) {
                Log.d(TAG, "History file does not exist, creating new file.");
            } else {
                Log.d(TAG, "History file exists, updating file.");
            }

            FileWriter writer = new FileWriter(file);
            writer.write(array.toString());
            writer.flush();
            writer.close();

            Log.d(TAG, "History file saved successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Error saving history", e);
        }
    }
}