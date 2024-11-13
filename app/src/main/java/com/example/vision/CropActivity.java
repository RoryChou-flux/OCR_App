package com.example.vision;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.vision.utils.ImageProcessorUtils;
import com.example.vision.utils.ImageProcessorUtils.ProcessedImage;
import com.yalantis.ucrop.UCrop;
import java.io.File;

public class CropActivity extends AppCompatActivity {

    private static final String TAG = "CropActivity";
    private static final String CROP_OUTPUT_DIR = "crop_output";
    private Uri sourceUri;
    private Uri destinationUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent();
        startCrop();
    }

    private void handleIntent() {
        sourceUri = getIntent().getParcelableExtra("sourceUri");
        if (sourceUri == null) {
            Toast.makeText(this, "未找到图片", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            createOutputUri();
        } catch (Exception e) {
            Toast.makeText(this, "准备裁剪失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void createOutputUri() {
        File outputDir = new File(getCacheDir(), CROP_OUTPUT_DIR);
        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (!created) {
                Log.w(TAG, "无法创建输出目录");
            }
        }

        // 清理旧文件
        File[] files = outputDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        Log.w(TAG, "无法删除旧文件: " + file.getPath());
                    }
                }
            }
        }

        destinationUri = Uri.fromFile(new File(outputDir, "cropped_" + System.currentTimeMillis() + ".jpg"));
    }

    private void startCrop() {
        UCrop.Options options = new UCrop.Options();

        options.setStatusBarColor(Color.BLACK);
        options.setToolbarColor(Color.BLACK);
        options.setToolbarWidgetColor(Color.WHITE);
        options.setToolbarTitle("裁剪图片");
        options.setShowCropGrid(true);
        options.setCropGridColor(Color.WHITE);
        options.setCropGridColumnCount(3);
        options.setCropGridRowCount(3);
        options.setCropFrameColor(Color.WHITE);
        options.setDimmedLayerColor(Color.parseColor("#99000000"));
        options.setCircleDimmedLayer(false);
        options.setFreeStyleCropEnabled(true);
        options.setHideBottomControls(false);
        options.setShowCropFrame(true);
        options.setCompressionQuality(90);
        options.setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG);

        UCrop uCrop = UCrop.of(sourceUri, destinationUri)
                .withOptions(options);

        String mode = getIntent().getStringExtra("mode");
        if ("document".equals(mode)) {
            uCrop.withAspectRatio(210, 297);  // A4纸比例
        } else {
            uCrop.withAspectRatio(1, 1);  // 默认正方形
        }

        uCrop.start(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP && data != null) {
            final Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                String mode = getIntent().getStringExtra("mode");
                handleCropResult(resultUri, mode);
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            final Throwable cropError = UCrop.getError(data);
            String errorMessage = cropError != null ? cropError.getMessage() : "未知错误";
            Toast.makeText(this, "裁剪失败: " + errorMessage, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "裁剪失败: " + errorMessage);
            finish();
        } else {
            Log.w(TAG, "裁剪取消");
            finish();
        }
    }

    private void handleCropResult(Uri croppedUri, String mode) {
        try {
            ProcessedImage processedImage = ImageProcessorUtils.processAndSaveThumbnail(this, croppedUri);

            if ("latex".equals(mode)) {
                // LaTeX 模式保持不变
                Intent intent = new Intent(this, LatexActivity.class);
                intent.putExtra("mode", "LATEX");
                intent.putExtra("imagePath", processedImage.originalPath);
                intent.putExtra("thumbnailPath", processedImage.thumbnailPath);
                intent.putExtra("timestamp", System.currentTimeMillis());
                startActivity(intent);
                finish();
            } else if ("document".equals(mode)) {
                // 在文档模式下显示确认对话框
                showDocumentContinueDialog(processedImage);
            }

        } catch (Exception e) {
            Log.e(TAG, "处理裁剪图片失败", e);
            Toast.makeText(this, "处理图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showDocumentContinueDialog(ProcessedImage processedImage) {
        new AlertDialog.Builder(this)
                .setTitle("继续拍摄？")
                .setMessage("是否需要继续拍摄下一页？")
                .setPositiveButton("继续拍摄", (dialog, which) -> {
                    // 返回到相机界面继续拍摄
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("imagePath", processedImage.originalPath);
                    resultIntent.putExtra("thumbnailPath", processedImage.thumbnailPath);
                    resultIntent.putExtra("timestamp", System.currentTimeMillis());
                    resultIntent.putExtra(DocumentPhotoManager.EXTRA_IS_CONTINUE, true);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                })
                .setNegativeButton("完成拍摄", (dialog, which) -> {
                    // 返回结果，表示完成拍摄
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("imagePath", processedImage.originalPath);
                    resultIntent.putExtra("thumbnailPath", processedImage.thumbnailPath);
                    resultIntent.putExtra("timestamp", System.currentTimeMillis());
                    resultIntent.putExtra(DocumentPhotoManager.EXTRA_IS_CONTINUE, false);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            cleanupTempFiles();
        }
    }

    private void cleanupTempFiles() {
        // 删除源文件（如果是临时文件）
        if (sourceUri != null) {
            String scheme = sourceUri.getScheme();
            if (scheme != null && scheme.equals("file")) {
                String path = sourceUri.getPath();
                if (path != null) {
                    boolean deleted = new File(path).delete();
                    if (!deleted) {
                        Log.w(TAG, "无法删除源文件: " + path);
                    }
                }
            }
        }

        // 删除裁剪输出文件（因为已经复制到应用私有目录）
        if (destinationUri != null) {
            String path = destinationUri.getPath();
            if (path != null) {
                boolean deleted = new File(path).delete();
                if (!deleted) {
                    Log.w(TAG, "无法删除裁剪输出文件: " + path);
                }
            }
        }
    }
}