package com.example.vision;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
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
            outputDir.mkdirs();
        }

        // 清理旧文件
        File[] files = outputDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                    file.delete();
                }
            }
        }

        destinationUri = Uri.fromFile(new File(outputDir, "cropped_" + System.currentTimeMillis() + ".jpg"));
    }

    private void startCrop() {
        UCrop.Options options = new UCrop.Options();

        // 基本样式设置
        options.setStatusBarColor(Color.BLACK);
        options.setToolbarColor(Color.BLACK);
        options.setToolbarWidgetColor(Color.WHITE);
        options.setToolbarTitle("裁剪图片");

        // 裁剪网格设置
        options.setShowCropGrid(true);
        options.setCropGridColor(Color.WHITE);
        options.setCropGridColumnCount(3);
        options.setCropGridRowCount(3);

        // 裁剪框设置
        options.setCropFrameColor(Color.WHITE);
        options.setDimmedLayerColor(Color.parseColor("#99000000"));

        // 手势设置
        options.setCircleDimmedLayer(false);
        options.setFreeStyleCropEnabled(true);  // 允许自由调整裁剪框
        options.setHideBottomControls(false);
        options.setShowCropFrame(true);

        // 压缩设置
        options.setCompressionQuality(90);
        options.setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG);

        // 开始裁剪
        UCrop uCrop = UCrop.of(sourceUri, destinationUri)
                .withOptions(options);

        // 根据模式设置不同的裁剪比例
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
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            final Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                String mode = getIntent().getStringExtra("mode");

                // 记录日志但暂不跳转
                if ("latex".equals(mode)) {
                    Log.d(TAG, "将跳转到LaTeX识别活动: " + resultUri);
                    // TODO: 后续添加 LatexActivity 跳转
                } else if ("document".equals(mode)) {
                    Log.d(TAG, "将跳转到文档处理活动: " + resultUri);
                    // TODO: 后续添加 DocumentActivity 跳转
                } else {
                    Log.w(TAG, "未知模式: " + mode);
                }

                // 显示成功提示
                Toast.makeText(this, "裁剪成功: " + mode + "模式", Toast.LENGTH_SHORT).show();
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            final Throwable cropError = UCrop.getError(data);
            String errorMessage = cropError != null ? cropError.getMessage() : "未知错误";
            Toast.makeText(this, "裁剪失败: " + errorMessage, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "裁剪失败: " + errorMessage);
        } else {
            Log.w(TAG, "裁剪取消");
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理临时文件
        if (isFinishing() && sourceUri != null) {
            String scheme = sourceUri.getScheme();
            if (scheme != null && scheme.equals("file")) {
                String path = sourceUri.getPath();
                if (path != null) {
                    new File(path).delete();
                }
            }
        }
    }
}