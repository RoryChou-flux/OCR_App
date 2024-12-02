package com.example.vision.network;

import android.util.Log;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SimpletexApiManager {
    private static final String TAG = "SimpletexApiManager";
    private static final String BASE_URL = "https://server.simpletex.cn";
    private static final String APP_ID = "ilVieOcIosU2amiW01Edxzmv";
    private static final String APP_SECRET = "KGfooNXHFAAbXbXW4v6x8LCEcWQYWgGm";

    private static SimpletexApiManager instance;
    private final SimpletexApi api;
    private final OkHttpClient client;

    public interface ApiCallback {
        void onSuccess(String response);
        void onFailure(String error);
    }

    private SimpletexApiManager() {
        client = createOkHttpClient();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .build();

        api = retrofit.create(SimpletexApi.class);
    }

    private OkHttpClient createOkHttpClient() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message ->
                Log.d(TAG, "OkHttp: " + message));
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build();
    }

    public static SimpletexApiManager getInstance() {
        if (instance == null) {
            synchronized (SimpletexApiManager.class) {
                if (instance == null) {
                    instance = new SimpletexApiManager();
                }
            }
        }
        return instance;
    }

    public void recognizeLatex(File imageFile, ApiCallback callback) {
        try {
            Log.d(TAG, "Starting LaTeX recognition for file: " + imageFile.getAbsolutePath());

            File compressedFile = compressImage(imageFile);
            if (compressedFile == null) {
                callback.onFailure("图片压缩失败");
                return;
            }

            Map<String, String> reqData = new HashMap<>();
            reqData.put("use_batch", "false");

            Map<String, String> headers = AuthHelper.getAuthHeaders(reqData, APP_ID, APP_SECRET);

            Map<String, RequestBody> formData = new HashMap<>();
            for (Map.Entry<String, String> entry : reqData.entrySet()) {
                formData.put(entry.getKey(), RequestBody.create(MediaType.parse("text/plain"), entry.getValue()));
            }

            RequestBody requestFile = RequestBody.create(
                    MediaType.parse("image/*"),
                    compressedFile
            );

            MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                    "file",
                    compressedFile.getName(),
                    requestFile
            );

            api.img2tex(headers, formData, filePart).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            String responseStr = response.body().string();
                            Log.d(TAG, "Response: " + responseStr);

                            JSONObject jsonResponse = new JSONObject(responseStr);
                            if (jsonResponse.getBoolean("status")) {
                                JSONObject res = jsonResponse.getJSONObject("res");
                                String latex = res.getString("latex");
                                callback.onSuccess(latex);
                            } else {
                                String message = jsonResponse.optString("message", "Unknown error");
                                callback.onFailure("识别失败: " + message);
                            }
                        } else {
                            String errorBody = response.errorBody() != null ?
                                    response.errorBody().string() : "Unknown error";
                            Log.e(TAG, "Error response: " + errorBody);
                            callback.onFailure("请求失败: " + response.code() + " " + errorBody);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Response parsing failed", e);
                        callback.onFailure("解析响应失败: " + e.getMessage());
                    } finally {
                        if (compressedFile.exists()) {
                            compressedFile.delete();
                        }
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e(TAG, "Network request failed", t);
                    callback.onFailure("网络请求失败: " + t.getMessage());
                    if (compressedFile.exists()) {
                        compressedFile.delete();
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Request preparation failed", e);
            callback.onFailure("请求准备失败: " + e.getMessage());
        }
    }

    public void recognizePdf(File imageFile, ApiCallback callback) {
        try {
            Log.d(TAG, "Starting PDF recognition for file: " + imageFile.getAbsolutePath());

            File compressedFile = compressImage(imageFile);
            if (compressedFile == null) {
                callback.onFailure("图片压缩失败");
                return;
            }

            Map<String, String> reqData = new HashMap<>();
            // 可以添加自定义包裹符号，如果需要的话
            reqData.put("inline_formula_wrapper", "[\"$\",\"$\"]");
            reqData.put("isolated_formula_wrapper", "[\"$$\",\"$$\"]");

            Map<String, String> headers = AuthHelper.getAuthHeaders(reqData, APP_ID, APP_SECRET);

            Map<String, RequestBody> formData = new HashMap<>();
            for (Map.Entry<String, String> entry : reqData.entrySet()) {
                formData.put(entry.getKey(), RequestBody.create(MediaType.parse("text/plain"), entry.getValue()));
            }

            RequestBody requestFile = RequestBody.create(
                    MediaType.parse("image/*"),
                    compressedFile
            );

            MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                    "file",
                    compressedFile.getName(),
                    requestFile
            );

            api.img2pdf(headers, formData, filePart).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            String responseStr = response.body().string();
                            Log.d(TAG, "Response: " + responseStr);

                            JSONObject jsonResponse = new JSONObject(responseStr);
                            if (jsonResponse.getBoolean("status")) {
                                JSONObject res = jsonResponse.getJSONObject("res");
                                String content = res.getString("content");
                                callback.onSuccess(content);
                            } else {
                                String message = jsonResponse.optString("message", "Unknown error");
                                callback.onFailure("识别失败: " + message);
                            }
                        } else {
                            String errorBody = response.errorBody() != null ?
                                    response.errorBody().string() : "Unknown error";
                            Log.e(TAG, "Error response: " + errorBody);
                            callback.onFailure("请求失败: " + response.code() + " " + errorBody);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Response parsing failed", e);
                        callback.onFailure("解析响应失败: " + e.getMessage());
                    } finally {
                        if (compressedFile.exists()) {
                            compressedFile.delete();
                        }
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e(TAG, "Network request failed", t);
                    callback.onFailure("网络请求失败: " + t.getMessage());
                    if (compressedFile.exists()) {
                        compressedFile.delete();
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Request preparation failed", e);
            callback.onFailure("请求准备失败: " + e.getMessage());
        }
    }

    private File compressImage(File imageFile) {
        // 如果需要压缩图片，可以在这里实现压缩逻辑
        // 当前直接返回原始文件
        return imageFile;
    }
}