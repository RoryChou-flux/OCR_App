package com.example.vision;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.button.MaterialButton;
import java.io.File;

public class CompareActivity extends AppCompatActivity {
    private static final String TAG = "CompareActivity";
    private ImageView originalImage;
    private ImageView processedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare);

        initViews();
        loadImages();
        setupClickListeners();
    }

    private void initViews() {
        originalImage = findViewById(R.id.originalImage);
        processedImage = findViewById(R.id.processedImage);
        MaterialButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        findViewById(R.id.backButton).setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });
    }

    private void loadImages() {
        try {
            String originalPath = getIntent().getStringExtra("originalPath");
            String processedPath = getIntent().getStringExtra("processedPath");

            Log.d(TAG, "Loading images:\nCropped: " + originalPath + "\nEnhanced: " + processedPath);

            if (originalPath == null || processedPath == null) {
                throw new IllegalArgumentException("Missing image paths - Cropped: " + originalPath + ", Enhanced: " + processedPath);
            }

            // 验证文件
            File croppedFile = new File(originalPath);
            File enhancedFile = new File(processedPath);

            if (!croppedFile.exists()) {
                throw new IllegalArgumentException("Cropped file not found: " + originalPath);
            }
            if (!enhancedFile.exists()) {
                throw new IllegalArgumentException("Enhanced file not found: " + processedPath);
            }

            // 配置Glide选项 - 禁用缩放
            RequestOptions options = new RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .override(originalImage.getWidth(), originalImage.getHeight()) // 保持原始尺寸
                    .centerInside(); // 使用centerInside而不是fitCenter

            // 加载裁剪后的图片到左侧
            Glide.with(this)
                    .load(croppedFile)
                    .apply(options)
                    .into(originalImage);

            // 加载增强后的图片到右侧
            Glide.with(this)
                    .load(enhancedFile)
                    .apply(options)
                    .into(processedImage);

        } catch (Exception e) {
            Log.e(TAG, "Error loading images", e);
            // 打印更详细的错误信息
            Log.e(TAG, "Intent extras: " + getIntent().getExtras());
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}