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
import java.util.ArrayList;
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

    // 添加照片列表管理
    private ArrayList<DocumentPhotoManager.PhotoItem> photoItems = new ArrayList<>();

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
        captureButton.setOnClickListener(v -> takePhoto());

        flashButton.setOnClickListener(v -> {
            if (isFrontCamera) {
                Toast.makeText(this, "前置摄像头不支持闪光灯", Toast.LENGTH_SHORT).show();
                return;
            }
            isFlashOn = !isFlashOn;
            flashButton.setIconResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
            if (imageCapture != null) {
                imageCapture.setFlashMode(isFlashOn ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
            }
        });

        switchCameraButton.setOnClickListener(v -> {
            isFrontCamera = !isFrontCamera;
            if (isFrontCamera && isFlashOn) {
                isFlashOn = false;
                flashButton.setIconResource(R.drawable.ic_flash_off);
            }
            startCamera();
        });

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
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        CameraSelector cameraSelector = isFrontCamera ?
                CameraSelector.DEFAULT_FRONT_CAMERA :
                CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
            );

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

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(System.currentTimeMillis());
        String fileName = "IMG_" + timestamp + ".jpg";

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
                .build();

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

                            String mode = getIntent().getStringExtra("mode");
                            Intent intent = new Intent(CameraActivity.this, CropActivity.class);
                            intent.putExtra("sourceUri", savedUri);
                            intent.putExtra("mode", mode);

                            // 根据模式决定是否需要返回结果
                            if ("document".equals(mode)) {
                                startActivityForResult(intent, DocumentPhotoManager.REQUEST_CODE_CROP);
                            } else {
                                startActivity(intent);
                                finish();
                            }
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
                String mode = getIntent().getStringExtra("mode");
                Intent intent = new Intent(this, CropActivity.class);
                intent.putExtra("sourceUri", selectedImageUri);
                intent.putExtra("mode", mode);

                if ("document".equals(mode)) {
                    startActivityForResult(intent, DocumentPhotoManager.REQUEST_CODE_CROP);
                } else {
                    startActivity(intent);
                    finish();
                }
            }
        }
        // 处理裁剪返回结果
        else if (requestCode == DocumentPhotoManager.REQUEST_CODE_CROP && resultCode == RESULT_OK && data != null) {
            String imagePath = data.getStringExtra("imagePath");
            String thumbnailPath = data.getStringExtra("thumbnailPath");
            long timestamp = data.getLongExtra("timestamp", System.currentTimeMillis());

            // 添加新的照片项
            photoItems.add(new DocumentPhotoManager.PhotoItem(imagePath, thumbnailPath, timestamp));

            boolean isContinue = data.getBooleanExtra(DocumentPhotoManager.EXTRA_IS_CONTINUE, false);
            if (isContinue) {
                // 继续拍摄
                startCamera();
            } else {
                // 完成拍摄，进入文档处理界面
                Intent intent = new Intent(this, DocumentActivity.class);
                intent.putParcelableArrayListExtra(DocumentPhotoManager.EXTRA_PHOTO_ITEMS, photoItems);
                startActivity(intent);
                finish();
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
        if (imageCapture != null) {
            imageCapture = null;
        }
    }
}