package com.example.vision;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import com.github.chrisbanes.photoview.PhotoView;
import com.bumptech.glide.Glide;

public class PhotoPreviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_preview);

        String photoPath = getIntent().getStringExtra("photo_path");
        if (photoPath == null) {
            finish();
            return;
        }

        // 初始化视图
        PhotoView photoView = findViewById(R.id.photoView);
        ImageButton closeButton = findViewById(R.id.closeButton);

        // 加载图片
        Glide.with(this)
                .load(photoPath)
                .into(photoView);

        // 设置关闭按钮
        closeButton.setOnClickListener(v -> finish());

        // 设置点击事件（点击照片切换UI可见性）
        View decorView = getWindow().getDecorView();
        photoView.setOnClickListener(v -> {
            int uiOptions = decorView.getSystemUiVisibility();
            if ((uiOptions & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                hideSystemUI();
            } else {
                showSystemUI();
            }
        });

        // 默认全屏显示
        hideSystemUI();
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }
}