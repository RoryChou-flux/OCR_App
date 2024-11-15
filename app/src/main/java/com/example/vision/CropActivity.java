package com.example.vision;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yalantis.ucrop.UCrop;
import java.io.File;

public class CropActivity extends AppCompatActivity {
    private static final String TAG = "CropActivity";
    private static final String CROP_OUTPUT_DIR = "crop_output";
    private Uri sourceUri;
    private Uri destinationUri;
    private String currentMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentMode = getIntent().getStringExtra("mode");
        handleIntent();
    }

    private void handleIntent() {
        sourceUri = getIntent().getParcelableExtra("sourceUri");
        if (sourceUri == null) {
            showError(getString(R.string.image_not_found));
            finish();
            return;
        }

        try {
            createOutputUri();
            startCrop();
        } catch (Exception e) {
            Log.e(TAG, "Crop preparation failed", e);
            showError(getString(R.string.crop_prep_failed));
            finish();
        }
    }

    private void createOutputUri() {
        File outputDir = new File(getCacheDir(), CROP_OUTPUT_DIR);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            Log.w(TAG, "Failed to create output directory");
        }

        // 清理旧文件
        File[] files = outputDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                    if (!file.delete()) {
                        Log.w(TAG, "Failed to delete old file: " + file.getPath());
                    }
                }
            }
        }

        destinationUri = Uri.fromFile(new File(outputDir,
                "cropped_" + System.currentTimeMillis() + ".jpg"));
    }

    private void startCrop() {
        UCrop.Options options = new UCrop.Options();
        setupCropOptions(options);

        UCrop uCrop = UCrop.of(sourceUri, destinationUri)
                .withOptions(options);

        // 设置裁剪比例
        if ("document".equals(currentMode)) {
            uCrop.withAspectRatio(210, 297); // A4纸比例
        }

        uCrop.start(this);
    }

    private void setupCropOptions(UCrop.Options options) {
        // 设置UI颜色
        options.setStatusBarColor(Color.BLACK);
        options.setToolbarColor(Color.BLACK);
        options.setToolbarWidgetColor(Color.WHITE);
        options.setToolbarTitle(getString(R.string.crop_image));

        // 设置网格
        options.setShowCropGrid(true);
        options.setCropGridColor(Color.WHITE);
        options.setCropGridColumnCount(3);
        options.setCropGridRowCount(3);
        options.setCropFrameColor(Color.WHITE);

        // 设置暗色背景
        options.setDimmedLayerColor(Color.parseColor("#99000000"));

        // 其他设置
        options.setCircleDimmedLayer(false);
        options.setFreeStyleCropEnabled(true);
        options.setHideBottomControls(false);
        options.setShowCropFrame(true);

        // 压缩设置
        options.setCompressionQuality(90);
        options.setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP && data != null) {
            final Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                processCroppedImage(resultUri);
            } else {
                showError(getString(R.string.crop_failed));
                finish();
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            final Throwable cropError = UCrop.getError(data);
            showError(getString(R.string.crop_failed) + ": " +
                    (cropError != null ? cropError.getMessage() : getString(R.string.unknown_error)));
            finish();
        } else {
            finish();
        }
    }

    private void processCroppedImage(Uri croppedUri) {
        try {
            String croppedPath = croppedUri.getPath();
            if (croppedPath == null) {
                throw new Exception(getString(R.string.invalid_crop_path));
            }

            // 直接返回结果，默认不继续拍摄（false）
            returnResult(croppedPath, false);

        } catch (Exception e) {
            Log.e(TAG, "Failed to process cropped image", e);
            showError(getString(R.string.process_failed) + ": " + e.getMessage());
            finish();
        }
    }

    private void showContinueDialog(String croppedPath) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.continue_capture_title)
                .setItems(new String[]{
                        getString(R.string.continue_capture),
                        getString(R.string.finish_capture)
                }, (dialog, which) -> {
                    // 确保返回到现有的DocumentActivity而不是创建新的
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("imagePath", croppedPath);
                    resultIntent.putExtra(DocumentPhotoManager.EXTRA_IS_CONTINUE, which == 0);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void processDocument(String imagePath) {
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(R.layout.dialog_processing)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            try {
                DocumentProcessor.DocumentResult result =
                        DocumentProcessor.processDocument(this, imagePath);

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    returnResult(result.processedPath, false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showError(getString(R.string.process_failed) + ": " + e.getMessage());
                });
            }
        }).start();
    }

    private void returnResult(String imagePath, boolean isContinue) {
        try {
            Log.d(TAG, "Returning crop result: " + imagePath + ", continue: " + isContinue);
            Intent resultIntent = new Intent();
            resultIntent.putExtra("imagePath", imagePath);
            resultIntent.putExtra("timestamp", System.currentTimeMillis());
            resultIntent.putExtra(DocumentPhotoManager.EXTRA_IS_CONTINUE, isContinue);
            setResult(RESULT_OK, resultIntent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error returning result", e);
            Toast.makeText(this, "返回裁剪结果时出错", Toast.LENGTH_SHORT).show();
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupTempFiles();
    }

    private void cleanupTempFiles() {
        if (sourceUri != null && "file".equals(sourceUri.getScheme())) {
            String path = sourceUri.getPath();
            if (path != null) {
                new File(path).delete();
            }
        }

        if (destinationUri != null) {
            String path = destinationUri.getPath();
            if (path != null) {
                new File(path).delete();
            }
        }
    }
}