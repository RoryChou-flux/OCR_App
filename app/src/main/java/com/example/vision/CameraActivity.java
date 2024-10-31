package com.example.vision;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import android.util.Log;

import com.google.common.util.concurrent.ListenableFuture;

public class CameraActivity extends AppCompatActivity {

    private PreviewView previewView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);  // 连接到 activity_camera.xml 布局

        previewView = findViewById(R.id.previewView);  // 连接 PreviewView

        startCamera();  // 启动相机
    }

    private void startCamera() {
        Log.d("MainActivity", "开始启动相机");

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                Log.d("MainActivity", "Camera provider future received");

                // 获取 CameraProvider 实例
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Log.d("MainActivity", "Camera provider obtained");

                // 设置预览
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                Log.d("MainActivity", "Preview set");

                // 选择相机（后置相机）
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // 解绑之前的所有相机操作
                cameraProvider.unbindAll();
                Log.d("MainActivity", "Unbind all cameras");

                // 绑定生命周期到相机预览
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);
                Log.d("MainActivity", "Camera bind successful");

            } catch (Exception e) {
                Log.e("MainActivity", "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));  // 在主线程中执行
    }

}
