package com.example.vision.network;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;
import java.util.Map;

public interface SimpletexApi {
    @Multipart
    @POST("/api/latex_ocr")
    Call<ResponseBody> img2tex(
            @HeaderMap Map<String, String> headers,
            @PartMap Map<String, RequestBody> formData,
            @Part MultipartBody.Part file
    );

    @Multipart
    @POST("/api/doc_ocr")
    Call<ResponseBody> img2pdf(
            @HeaderMap Map<String, String> headers,
            @PartMap Map<String, RequestBody> formData,
            @Part MultipartBody.Part file
    );
}