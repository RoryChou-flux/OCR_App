package com.example.vision;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
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

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private FloatingActionButton captureButton;
    private MaterialButton flashButton;
    private MaterialButton switchCameraButton;
    private MaterialButton galleryButton;
    private MaterialButton closeButton;
    private boolean isFlashOn = false;
    private boolean isFrontCamera = false;

    // 使用新的 ActivityResult API
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        handleGalleryImage(selectedImageUri);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> cropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String imagePath = result.getData().getStringExtra("imagePath");
                        boolean isContinue = result.getData().getBooleanExtra(
                                DocumentPhotoManager.EXTRA_IS_CONTINUE, false);

                        // 返回到现有的DocumentActivity
                        Intent intent = new Intent(this, DocumentActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        intent.putExtra("imagePath", imagePath);
                        intent.putExtra("timestamp", System.currentTimeMillis());
                        startActivity(intent);

                        // 如果不继续拍摄，关闭相机
                        if (!isContinue) {
                            finish();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing crop result", e);
                    Toast.makeText(this, "处理裁剪结果时出错", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        initializeViews();
        setupClickListeners();
        startCamera();
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        captureButton = findViewById(R.id.capture_button);
        flashButton = findViewById(R.id.flash_button);
        switchCameraButton = findViewById(R.id.switch_camera_button);
        galleryButton = findViewById(R.id.gallery_button);
        closeButton = findViewById(R.id.closeButton);
    }

    private void setupClickListeners() {
        captureButton.setOnClickListener(v -> takePhoto());

        flashButton.setOnClickListener(v -> toggleFlash());

        switchCameraButton.setOnClickListener(v -> {
            isFrontCamera = !isFrontCamera;
            startCamera();
        });

        galleryButton.setOnClickListener(v -> openGallery());

        closeButton.setOnClickListener(v -> finish());
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        galleryLauncher.launch(intent);
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        String filename = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
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
                            handleCapturedImage(savedUri);
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage());
                        showToast(getString(R.string.capture_error));
                    }
                }
        );
    }

    private void handleCapturedImage(Uri imageUri) {
        try {
            Log.d(TAG, "Handling captured image: " + imageUri);
            String mode = getIntent().getStringExtra("mode");
            Intent intent = new Intent(this, CropActivity.class);
            intent.putExtra("sourceUri", imageUri);
            intent.putExtra("mode", mode);
            cropLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error handling captured image", e);
            Toast.makeText(this, "处理拍摄图片时出错", Toast.LENGTH_SHORT).show();
        }
    }
    private void handleGalleryImage(Uri imageUri) {
        String mode = getIntent().getStringExtra("mode");
        Intent intent = new Intent(this, CropActivity.class);
        intent.putExtra("sourceUri", imageUri);
        intent.putExtra("mode", mode);
        cropLauncher.launch(intent);
    }

    private void toggleFlash() {
        if (isFrontCamera) {
            showToast(getString(R.string.flash_not_supported));
            return;
        }
        isFlashOn = !isFlashOn;
        flashButton.setIconResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        updateFlashMode();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(isFrontCamera ?
                        CameraSelector.LENS_FACING_FRONT :
                        CameraSelector.LENS_FACING_BACK)
                .build();

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
            );
            updateFlashMode();
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private void updateFlashMode() {
        if (imageCapture != null && !isFrontCamera) {
            imageCapture.setFlashMode(isFlashOn ?
                    ImageCapture.FLASH_MODE_ON :
                    ImageCapture.FLASH_MODE_OFF);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}