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

            if (originalPath == null || processedPath == null) {
                throw new IllegalArgumentException("Missing image paths");
            }

            // 验证文件
            File originalFile = new File(originalPath);
            File processedFile = new File(processedPath);

            if (!originalFile.exists() || !processedFile.exists()) {
                throw new IllegalArgumentException("Image files not found");
            }

            // 配置Glide选项
            RequestOptions options = new RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .fitCenter();

            // 加载图片
            Glide.with(this)
                    .load(originalFile)
                    .apply(options)
                    .into(originalImage);

            Glide.with(this)
                    .load(processedFile)
                    .apply(options)
                    .into(processedImage);

        } catch (Exception e) {
            Log.e(TAG, "Error loading images", e);
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
        // 不在onDestroy中使用Glide
        super.onDestroy();
    }
}