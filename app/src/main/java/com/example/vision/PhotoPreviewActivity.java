package com.example.vision;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class PhotoPreviewActivity extends AppCompatActivity {
    private static final String TAG = "PhotoPreviewActivity";

    private View topBar;
    private View bottomBar;
    private TextView pageIndicator;
    private boolean areControlsVisible = true;
    private String currentPhotoPath;
    private PhotoView photoView;
    private AlertDialog progressDialog;
    private volatile boolean isProcessing = false;

    public PhotoPreviewActivity() {
        currentPhotoPath = null;  // 初始化字段
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_preview);

        setupWindowFlags();
        initializeViews();
        setupPhotoView();
        initProgressDialog();
        loadImage();
    }


    private void setupWindowFlags() {
        // 设置全屏显示
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        // 设置系统UI显示模式
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        getWindow().getDecorView().setSystemUiVisibility(flags);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
    }

    private void initializeViews() {
        photoView = findViewById(R.id.photoView);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        pageIndicator = findViewById(R.id.pageIndicator);

        currentPhotoPath = getIntent().getStringExtra("photo_path");
        int position = getIntent().getIntExtra("position", 0);
        int total = getIntent().getIntExtra("total", 0);

        if (total > 0) {
            pageIndicator.setText(String.format(getString(R.string.page_indicator),
                    position, total));
        }

        findViewById(R.id.closeButton).setOnClickListener(v -> {
            finish();
            // 添加退出动画
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        findViewById(R.id.correctButton).setOnClickListener(v -> {
            if (!isProcessing) {
                processDocument();
            }
        });
    }

    private void initProgressDialog() {
        progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(R.layout.dialog_processing)
                .setCancelable(false)
                .create();
    }

    private void setupPhotoView() {
        // 配置PhotoView
        photoView.setMaximumScale(5.0f);
        photoView.setMediumScale(2.5f);
        photoView.setMinimumScale(1.0f);

        // 设置点击事件
        photoView.setOnViewTapListener((view, x, y) -> toggleControls());

        // 设置缩放事件
        photoView.setOnScaleChangeListener((scaleFactor, focusX, focusY) -> {
            if (!areControlsVisible) {
                showControls();
            }
        });
    }

    private void loadImage() {
        try {
            String path = getIntent().getStringExtra("photo_path");
            if (path == null) {
                throw new IOException("No image path provided");
            }

            File imageFile = new File(path);
            if (!imageFile.exists() || !imageFile.canRead()) {
                throw new IOException("Cannot access image file: " + path);
            }

            RequestOptions options = new RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .fitCenter();

            if (!isFinishing() && !isDestroyed()) {
                Glide.with(this)
                        .load(imageFile)
                        .apply(options)
                        .into(photoView);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error loading image", e);
            Toast.makeText(this, "无法加载图片: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void cleanupFiles(String path) {
        try {
            if (path != null) {
                File file = new File(path);
                boolean deleted = file.delete();
                if (!deleted) {
                    Log.w(TAG, "Failed to delete file: " + path);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting file", e);
        }
    }

    private void processDocument() {
        if (isProcessing) return;

        try {
            File sourceFile = new File(currentPhotoPath);
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                throw new IOException("无法访问源图片文件");
            }

            // 复制到处理目录
            File outputDir = new File(getFilesDir(), "processing");
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IOException("无法创建处理目录");
            }

            File processingFile = new File(outputDir,
                    "processing_" + System.currentTimeMillis() + ".jpg");
            copyFile(sourceFile, processingFile);

            isProcessing = true;
            progressDialog.show();

            new Thread(() -> {
                try {
                    DocumentProcessor.DocumentResult result =
                            DocumentProcessor.processDocument(this, processingFile.getAbsolutePath());

                    // 验证处理结果
                    File processedFile = new File(result.processedPath);
                    if (!processedFile.exists() || !processedFile.canRead()) {
                        throw new IOException("处理后的文件无法访问");
                    }

                    runOnUiThread(() -> {
                        currentPhotoPath = result.processedPath;
                        loadImage();
                        Toast.makeText(this, R.string.process_success, Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error processing document", e);
                    runOnUiThread(() ->
                            Toast.makeText(this,
                                    getString(R.string.process_failed) + ": " + e.getMessage(),
                                    Toast.LENGTH_LONG).show()
                    );
                } finally {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        isProcessing = false;
                    });
                    processingFile.delete(); // 清理临时文件
                }
            }).start();
        } catch (Exception e) {
            Toast.makeText(this, "处理文档失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void finish() {
        if (photoView != null && !isFinishing() && !isDestroyed()) {
            try {
                Glide.with(getApplicationContext()).clear(photoView);
            } catch (Exception e) {
                Log.e(TAG, "Error clearing image view", e);
            }
        }
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();  // 调用父类方法
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void copyFile(File source, File dest) throws IOException {
        try (FileChannel sourceChannel = new FileInputStream(source).getChannel();
             FileChannel destChannel = new FileOutputStream(dest).getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
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
    protected void onDestroy() {
        super.onDestroy();

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        if (photoView != null && !isFinishing() && !isDestroyed()) {
            try {
                Glide.get(getApplicationContext()).clearMemory();
                new Thread(() -> {
                    try {
                        Glide.get(getApplicationContext()).clearDiskCache();
                    } catch (Exception e) {
                        Log.e(TAG, "Error clearing disk cache", e);
                    }
                }).start();
            } catch (Exception e) {
                Log.e(TAG, "Error clearing Glide cache", e);
            }
        }
    }

}