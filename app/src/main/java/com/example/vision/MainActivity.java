package com.example.vision;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private PreviewView previewView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 设置顶部工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 设置用户头像
        ImageView profileImage = findViewById(R.id.profileImage);
        profileImage.setImageResource(R.drawable.profile_pic);

        // 相机预览
        previewView = findViewById(R.id.previewView);

        // 开始识别按钮点击事件
        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "开始识别", Toast.LENGTH_SHORT).show();
            // 检查权限后启动相机
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                startCamera();  // 启动相机
            } else {
                // 请求相机权限
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            }
        });
    }

    private void startCamera() {
        // 使用 ListenableFuture 获取 ProcessCameraProvider 实例
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        // 监听异步任务完成
        cameraProviderFuture.addListener(() -> {
            try {
                // 获取 CameraProvider 实例
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // 设置预览
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // 选择相机（后置相机）
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // 解绑之前的所有相机操作
                cameraProvider.unbindAll();

                // 绑定生命周期到相机预览
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);

            } catch (Exception e) {
                Log.e("CameraActivity", "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));  // 在主线程中执行
    }

    // 请求权限结果处理
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限授予后启动相机
                startCamera();
            } else {
                // 权限被拒绝，提示用户
                Toast.makeText(this, "相机权限被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
