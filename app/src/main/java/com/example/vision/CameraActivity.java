package com.example.vision;
import java.util.Queue;
import java.util.LinkedList;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import android.os.Environment;
import java.io.File;
import java.util.Date;

public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";
    private Queue<Uri> pendingImages; // 添加等待处理的图片队列
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
                    Intent data = result.getData();
                    pendingImages = new LinkedList<>(); // 初始化队列

                    // 收集所有选中的图片URI
                    if (data.getClipData() != null) {
                        ClipData clipData = data.getClipData();
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            pendingImages.add(clipData.getItemAt(i).getUri());
                        }
                    } else if (data.getData() != null) {
                        pendingImages.add(data.getData());
                    }

                    // 开始处理第一张图片
                    processNextImage();
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

                        // 处理当前图片
                        if (DocumentActivity.getInstance() != null) {
                            DocumentActivity.addNewPhoto(imagePath);
                        } else {
                            Intent intent = new Intent(this, DocumentActivity.class);
                            intent.putExtra("imagePath", imagePath);
                            startActivity(intent);
                        }

                        // 处理队列中的下一张图片
                        if (!pendingImages.isEmpty()) {
                            processNextImage();
                        } else if (!isContinue) {
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
        pendingImages = new LinkedList<>(); // 初始化队列
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
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        galleryLauncher.launch(Intent.createChooser(intent, "选择图片"));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        // 创建照片文件
        File photoFile;
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
                    .format(new Date());
            String fileName = "IMG" + timeStamp + ".jpg";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            photoFile = new File(storageDir, fileName);
        } catch (Exception e) {
            Log.e(TAG, "Error creating photo file", e);
            showToast(getString(R.string.save_error));
            return;
        }

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        Uri savedUri = Uri.fromFile(photoFile);
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

    private void processNextImage() {
        if (!pendingImages.isEmpty()) {
            Uri imageUri = pendingImages.poll(); // 获取并移除队列头部的URI
            String mode = getIntent().getStringExtra("mode");
            Intent intent = new Intent(this, CropActivity.class);
            intent.putExtra("sourceUri", imageUri);
            intent.putExtra("mode", mode);
            cropLauncher.launch(intent);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}