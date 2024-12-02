package com.example.vision;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.net.Uri;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.io.IOException;

public class PhotoPreviewActivity extends AppCompatActivity {
    private static final String TAG = "PhotoPreviewActivity";
    private static final int REQUEST_CROP = 1002;

    private View topBar;
    private View bottomBar;
    private TextView pageIndicator;
    private boolean areControlsVisible = true;
    private String currentPhotoPath;
    private String originalPhotoPath;
    private int currentPosition; // 当前图片的位置
    private int totalItems; // 图片总数
    private PhotoView photoView;
    private AlertDialog progressDialog;
    private volatile boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_preview);

        // 获取 Intent 中的参数
        currentPhotoPath = getIntent().getStringExtra("photo_path");
        originalPhotoPath = getIntent().getStringExtra("original_path");
        currentPosition = getIntent().getIntExtra("position", -1);
        totalItems = getIntent().getIntExtra("total", 0);

        if (currentPhotoPath == null || originalPhotoPath == null || currentPosition == -1) {
            Log.e(TAG, "Invalid parameters");
            finish();
            return;
        }

        setupWindowFlags();
        initializeViews();
        setupPhotoView();
        initProgressDialog();
        loadImage();
    }

    private void setupWindowFlags() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        getWindow().getDecorView().setSystemUiVisibility(flags);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
    }

    private void initializeViews() {
        photoView = findViewById(R.id.photoView);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        pageIndicator = findViewById(R.id.pageIndicator);

        // 显示页码，索引从0开始，所以需要加1
        if (totalItems > 0) {
            pageIndicator.setText(String.format(getString(R.string.page_indicator),
                    currentPosition + 1, totalItems));
        } else {
            pageIndicator.setText(String.format(getString(R.string.page_indicator_single),
                    currentPosition + 1));
        }

        findViewById(R.id.correctButton).setOnClickListener(v -> {
            if (!isProcessing) {
                processDocument();
            }
        });
    }

    private void setupPhotoView() {
        photoView.setMaximumScale(5.0f);
        photoView.setMediumScale(2.5f);
        photoView.setMinimumScale(1.0f);
        photoView.setOnViewTapListener((view, x, y) -> toggleControls());
        photoView.setOnScaleChangeListener((scaleFactor, focusX, focusY) -> {
            if (!areControlsVisible) {
                showControls();
            }
        });
    }

    private void initProgressDialog() {
        progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(R.layout.dialog_processing)
                .setCancelable(false)
                .create();
    }

    private void showProcessingDialog() {
        runOnUiThread(() -> {
            if (!isFinishing() && progressDialog != null) {
                progressDialog.show();
            }
        });
    }

    private void hideProcessingDialog() {
        runOnUiThread(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        });
    }

    private void processDocument() {
        if (isProcessing || currentPhotoPath == null) return;

        Intent cropIntent = new Intent(this, DocumentCropActivity.class);
        cropIntent.putExtra("sourceUri", Uri.fromFile(new File(currentPhotoPath)));
        startActivityForResult(cropIntent, REQUEST_CROP);
    }

    // 在 PhotoPreviewActivity 中：
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CROP && resultCode == RESULT_OK && data != null) {
            String processedPath = data.getStringExtra("imagePath");
            String thumbnailPath = data.getStringExtra("thumbnailPath");

            if (processedPath != null) {
                Log.d(TAG, "Received crop result: " + processedPath);
                // 更新当前路径
                currentPhotoPath = processedPath;
                // 重新加载图片
                loadImage();
                // 返回处理结果
                Intent resultIntent = new Intent();
                resultIntent.putExtra("processed_path", processedPath);
                resultIntent.putExtra("thumbnail_path", thumbnailPath);
                resultIntent.putExtra("original_path", originalPhotoPath);
                setResult(RESULT_OK, resultIntent);
            } else {
                Log.e(TAG, "No processed path in result");
                Toast.makeText(this, R.string.process_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadImage() {
        try {
            if (currentPhotoPath == null) {
                throw new IOException("No image path provided");
            }

            Glide.with(this)
                    .load(new File(currentPhotoPath))
                    .apply(new RequestOptions()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true))
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Image load failed", e);
                            Toast.makeText(PhotoPreviewActivity.this,
                                    "无法加载图片", Toast.LENGTH_SHORT).show();
                            finish();
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model,
                                                       Target<Drawable> target, DataSource dataSource,
                                                       boolean isFirstResource) {
                            // 图片加载成功
                            return false;
                        }
                    })
                    .into(photoView);

        } catch (Exception e) {
            Log.e(TAG, "Error loading image", e);
            Toast.makeText(this, "无法加载图片: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void toggleControls() {
        if (areControlsVisible) {
            hideControls();
        } else {
            showControls();
        }
        areControlsVisible = !areControlsVisible;
    }

    private void showControls() {
        topBar.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(200)
                .start();
        bottomBar.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(200)
                .start();
    }

    private void hideControls() {
        topBar.animate()
                .alpha(0f)
                .translationY(-topBar.getHeight())
                .setDuration(200)
                .start();
        bottomBar.animate()
                .alpha(0f)
                .translationY(bottomBar.getHeight())
                .setDuration(200)
                .start();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        if (photoView != null && !isFinishing()) {
            Glide.with(this).clear(photoView);
        }
    }

}
