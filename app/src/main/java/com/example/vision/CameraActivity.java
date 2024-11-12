package com.example.vision;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final int REQUEST_CODE_GALLERY = 123;

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private FloatingActionButton captureButton;
    private MaterialButton flashButton;
    private MaterialButton switchCameraButton;
    private MaterialButton galleryButton;
    private boolean isFlashOn = false;
    private boolean isFrontCamera = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        String mode = getIntent().getStringExtra("mode");
        Log.d(TAG, "当前模式: " + mode);

        initializeViews();
        setClickListeners();
        startCamera();
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        captureButton = findViewById(R.id.capture_button);
        flashButton = findViewById(R.id.flash_button);
        switchCameraButton = findViewById(R.id.switch_camera_button);
        galleryButton = findViewById(R.id.gallery_button);
    }

    private void setClickListeners() {
        // 拍照按钮监听器
        captureButton.setOnClickListener(v -> takePhoto());

        // 闪光灯按钮监听器
        flashButton.setOnClickListener(v -> {
            if (isFrontCamera) {
                Toast.makeText(this, "前置摄像头不支持闪光灯", Toast.LENGTH_SHORT).show();
                return;
            }
            // 切换闪光灯状态
            isFlashOn = !isFlashOn;
            // 更新闪光灯图标
            flashButton.setIconResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
            // 根据闪光灯状态配置相机闪光灯
            if (imageCapture != null) {
                imageCapture.setFlashMode(isFlashOn ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
            }
        });

        // 切换前后摄像头按钮监听器
        switchCameraButton.setOnClickListener(v -> {
            isFrontCamera = !isFrontCamera;
            // 切换摄像头时关闭闪光灯
            if (isFrontCamera && isFlashOn) {
                isFlashOn = false;
                flashButton.setIconResource(R.drawable.ic_flash_off);
            }
            startCamera();
        });

        // 相册导入按钮监听器
        galleryButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_CODE_GALLERY);
        });
    }

    private void startCamera() {
        Log.d(TAG, "Starting camera...");

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera: " + e.getMessage());
                showError("无法启动相机: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        // 预览配置
        Preview preview = new Preview.Builder()
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // 图片捕获配置
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        // 相机选择器
        CameraSelector cameraSelector = isFrontCamera ?
                CameraSelector.DEFAULT_FRONT_CAMERA :
                CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            // 解绑之前的用例
            cameraProvider.unbindAll();

            // 绑定用例到生命周期
            cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
            );

            // 设置闪光灯模式，仅在后置摄像头模式下有效
            if (imageCapture != null && !isFrontCamera) {
                imageCapture.setFlashMode(isFlashOn ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
            }

            Log.d(TAG, "Camera use cases bound successfully");
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed: " + e.getMessage());
            showError("相机绑定失败: " + e.getMessage());
        }
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is null");
            return;
        }

        // 创建时间戳文件名
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(System.currentTimeMillis());
        String fileName = "IMG_" + timestamp + ".jpg";

        // 创建输出选项对象
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        // 创建输出选项
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
                .build();

        // 设置拍照回调
        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        Uri savedUri = output.getSavedUri();
                        if (savedUri != null) {
                            Log.d(TAG, "Photo saved: " + savedUri);
                            showSuccess("照片保存成功");

                            // 获取模式并传递到裁剪界面
                            String mode = getIntent().getStringExtra("mode");
                            Intent intent = new Intent(CameraActivity.this, CropActivity.class);
                            intent.putExtra("sourceUri", savedUri);
                            intent.putExtra("mode", mode);
                            startActivity(intent);
                            finish();  // 完成后关闭相机界面
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage());
                        showError("拍照失败: " + exception.getMessage());
                    }
                }
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_GALLERY && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                Log.d(TAG, "Selected image from gallery: " + selectedImageUri);

                // 获取模式并传递到裁剪界面
                String mode = getIntent().getStringExtra("mode");
                Intent intent = new Intent(this, CropActivity.class);
                intent.putExtra("sourceUri", selectedImageUri);
                intent.putExtra("mode", mode);
                startActivity(intent);
                finish();  // 完成后关闭相机界面
            }
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private void showSuccess(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放相机资源
        if (imageCapture != null) {
            imageCapture = null;
        }
    }
}