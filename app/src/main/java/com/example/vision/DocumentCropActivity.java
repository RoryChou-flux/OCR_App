package com.example.vision;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class DocumentCropActivity extends AppCompatActivity {
    private static final String TAG = "CropActivity";
    private static final String CROP_OUTPUT_DIR = "crop_output";
    private static final int REQUEST_PERMISSION_CODE = 1001;
    private Bitmap currentBitmap;

    private Uri sourceUri;
    private PolygonCropView cropView;
    private AlertDialog progressDialog;
    private MaterialButton doneButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        checkPermissions();
        initViews();
        handleIntent();
        setupButtons();
        initProgressDialog();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION_CODE);
        }
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        cropView = findViewById(R.id.crop_view);
        doneButton = findViewById(R.id.done_button);

        doneButton.setEnabled(false);

        cropView.setOnPointsChangeListener(pointCount -> {
            Log.d(TAG, "Points changed: " + pointCount);
            doneButton.setEnabled(pointCount == 4);
        });
    }

    private void initProgressDialog() {
        progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(R.layout.dialog_processing)
                .setCancelable(false)
                .create();
    }

    private void handleIntent() {
        sourceUri = getIntent().getParcelableExtra("sourceUri");
        if (sourceUri == null) {
            Log.e(TAG, "No source URI provided");
            Toast.makeText(this, R.string.crop_error_no_image, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            loadImage();
        } catch (Exception e) {
            Log.e(TAG, "Error loading image", e);
            Toast.makeText(this, R.string.crop_error_loading, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadImage() throws Exception {
        try (InputStream is = getContentResolver().openInputStream(sourceUri)) {
            if (is == null) throw new Exception("Cannot open input stream");
            Log.d(TAG, "Loading image from URI: " + sourceUri.toString());

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            currentBitmap = BitmapFactory.decodeStream(is, null, options);

            if (currentBitmap == null) throw new Exception("Failed to decode image");
            cropView.setBitmap(currentBitmap);
        }
    }


    private void setupButtons() {
        MaterialButton resetButton = findViewById(R.id.reset_button);

        doneButton.setOnClickListener(view -> {
            Log.d(TAG, "完成按钮被点击");

            // 验证裁剪形状
            if (!cropView.isValidShape()) {
                Log.e(TAG, "无效的裁剪形状");
                Toast.makeText(this, R.string.crop_error_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取并验证角点
            PointF[] corners = cropView.getScaledPoints();
            if (corners == null || corners.length != 4) {
                Log.e(TAG, "无效的角点数据");
                Toast.makeText(this, R.string.crop_error_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            showProcessingDialog();

            // 在新线程中处理文档
            new Thread(() -> {
                try {
                    Log.d(TAG, "开始处理文档...");

                    // 1. 保存裁剪后的图片作为初始图片
                    File originalDir = new File(getFilesDir(), "cropped");  // 改为 cropped 目录
                    if (!originalDir.exists() && !originalDir.mkdirs()) {
                        throw new IOException("无法创建裁剪图片目录");
                    }

                    String timestamp = String.valueOf(System.currentTimeMillis());
                    File croppedFile = new File(originalDir, "CROP_" + timestamp + ".jpg");  // 改为 CROP_ 前缀

                    // 将当前图片保存为裁剪后的图片
                    try (InputStream is = getContentResolver().openInputStream(sourceUri)) {
                        if (is == null) throw new IOException("无法打开输入流");
                        try (FileOutputStream fos = new FileOutputStream(croppedFile)) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                        }
                    }

                    Log.d(TAG, "裁剪后图片已保存: " + croppedFile.getAbsolutePath());

                    // 2. 处理文档增强
                    DocumentProcessor.DocumentResult result =
                            DocumentProcessor.processDocument(this, Uri.fromFile(croppedFile), corners);  // 使用裁剪后的图片而不是sourceUri

                    // 验证处理结果
                    if (result == null || result.processedPath == null) {
                        throw new Exception("处理失败：结果为空");
                    }

                    // 验证文件是否存在
                    if (!new File(result.processedPath).exists()) {
                        throw new Exception("处理后的文件不存在: " + result.processedPath);
                    }

                    if (result.thumbnailPath != null && !new File(result.thumbnailPath).exists()) {
                        throw new Exception("缩略图文件不存在: " + result.thumbnailPath);
                    }

                    // 记录处理结果
                    Log.d(TAG, "文档处理完成:\n" +
                            "裁剪后图片路径: " + croppedFile.getAbsolutePath() + "\n" +
                            "增强后路径: " + result.processedPath + "\n" +
                            "缩略图路径: " + result.thumbnailPath);

                    // 在主线程中处理结果
                    runOnUiThread(() -> {
                        Log.d(TAG, "处理完成，准备返回结果");
                        Intent resultIntent = new Intent();

                        // 更新返回路径
                        resultIntent.putExtra("imagePath", result.processedPath);          // 保持向后兼容
                        resultIntent.putExtra("cropped_file", croppedFile.getAbsolutePath());  // 裁剪后的图片路径
                        resultIntent.putExtra("enhanced_file", result.processedPath);     // 增强后的图片路径
                        resultIntent.putExtra("thumbnail_path", result.thumbnailPath);     // 缩略图路径
                        resultIntent.putExtra("timestamp", timestamp);                     // 添加时间戳

                        Log.d(TAG, String.format("返回Intent数据:\n" +
                                        "裁剪后文件: %s\n" +
                                        "增强后文件: %s\n" +
                                        "缩略图: %s",
                                croppedFile.getAbsolutePath(),
                                result.processedPath,
                                result.thumbnailPath));

                        setResult(RESULT_OK, resultIntent);
                        finish();
                    });

                } catch (Exception e) {
                    Log.e(TAG, "文档处理错误", e);
                    runOnUiThread(() -> {
                        String errorMessage = getString(R.string.crop_error_failed) +
                                ": " + e.getMessage();
                        Toast.makeText(DocumentCropActivity.this,
                                errorMessage, Toast.LENGTH_SHORT).show();
                    });
                } finally {
                    runOnUiThread(() -> hideProcessingDialog());
                }
            }).start();
        });

        // 重置按钮点击事件
        resetButton.setOnClickListener(view -> {
            Log.d(TAG, "重置按钮被点击");
            cropView.reset();
            doneButton.setEnabled(false);
        });
    }

    private void showProcessingDialog() {
        if (!isFinishing() && progressDialog != null) {
            progressDialog.show();
        }
    }

    private void hideProcessingDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handleIntent();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        if (cropView != null) {
            cropView.setBitmap(null);
        }
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }
    }
}