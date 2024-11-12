package com.example.vision;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            // Android 13 (API 33)及以上版本的权限
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU ?
                    Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 先检查权限
        if (checkPermissions()) {
            initViews();
        }
    }

    private void initViews() {
        // 历史记录按钮
        ImageButton historyButton = findViewById(R.id.historyButton);
        historyButton.setOnClickListener(v -> {
            Toast.makeText(this, "历史记录功能开发中", Toast.LENGTH_SHORT).show();
        });

        // 数学公式识别卡片
        CardView latexCard = findViewById(R.id.latexCard);
        latexCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CameraActivity.class);
            intent.putExtra("mode", "latex");  // 设置为LaTeX模式
            startActivity(intent);
        });

        // 文档矫正卡片
        CardView documentCard = findViewById(R.id.documentCard);
        documentCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CameraActivity.class);
            intent.putExtra("mode", "document");  // 设置为文档模式
            startActivity(intent);
        });
    }

    private boolean checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13及以上版本
            for (String permission : REQUIRED_PERMISSIONS) {
                if (permission == null) continue; // 跳过可能的null权限
                if (ContextCompat.checkSelfPermission(this, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
                    return false;
                }
            }
        } else {
            // Android 12及以下版本
            for (String permission : REQUIRED_PERMISSIONS) {
                if (permission == null) continue;
                if (!permission.equals(Manifest.permission.READ_MEDIA_IMAGES) &&
                        ContextCompat.checkSelfPermission(this, permission)
                                != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "已获得所需权限", Toast.LENGTH_SHORT).show();
                initViews();
            } else {
                Toast.makeText(this, "需要相关权限才能使用此功能", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 双击退出功能
    private long lastBackPressTime = 0;
    @Override
    public void onBackPressed() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBackPressTime > 2000) {
            Toast.makeText(this, "再按一次退出应用", Toast.LENGTH_SHORT).show();
            lastBackPressTime = currentTime;
        } else {
            super.onBackPressed();
        }
    }
}