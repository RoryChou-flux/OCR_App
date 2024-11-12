package com.example.vision.network;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class NetworkManager {
    private static final String TAG = "NetworkManager";
    private static NetworkManager instance;
    private final OkHttpClient client;

    // API endpoints
    private static final String LATEX_API_URL = "https://api.example.com/latex/recognize";
    private static final String DOCUMENT_API_URL = "https://api.example.com/document/correct";

    private NetworkManager() {
        // 配置OkHttpClient，设置超时时间等
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    // 单例模式获取实例
    public static NetworkManager getInstance() {
        if (instance == null) {
            synchronized (NetworkManager.class) {
                if (instance == null) {
                    instance = new NetworkManager();
                }
            }
        }
        return instance;
    }

    // 识别LaTeX公式
    public void recognizeLatex(File imageFile, NetworkCallback callback) {
        // 构建多部分请求体，包含图片文件
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", imageFile.getName(),
                        RequestBody.create(imageFile, MediaType.parse("image/*")))
                .build();

        // 构建请求
        Request request = new Request.Builder()
                .url(LATEX_API_URL)
                .post(requestBody)
                .build();

        // 发送异步请求
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "LaTeX recognition failed", e);
                callback.onFailure("LaTeX识别失败: " + e.getMessage());
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        // 解析返回的LaTeX公式
                        String latex = response.body().string();
                        callback.onSuccess(latex);
                    } else {
                        callback.onFailure("请求失败: " + response.code());
                    }
                } catch (IOException e) {
                    callback.onFailure("响应解析失败: " + e.getMessage());
                }
            }
        });
    }

    // 文档矫正
    public void correctDocument(File imageFile, NetworkCallback callback) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("document", imageFile.getName(),
                        RequestBody.create(imageFile, MediaType.parse("image/*")))
                .build();

        Request request = new Request.Builder()
                .url(DOCUMENT_API_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "Document correction failed", e);
                callback.onFailure("文档矫正失败: " + e.getMessage());
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        // 处理矫正后的文档
                        String result = response.body().string();
                        callback.onSuccess(result);
                    } else {
                        callback.onFailure("请求失败: " + response.code());
                    }
                } catch (IOException e) {
                    callback.onFailure("响应解析失败: " + e.getMessage());
                }
            }
        });
    }

    // 回调接口
    public interface NetworkCallback {
        void onSuccess(String response);
        void onFailure(String error);
    }
}