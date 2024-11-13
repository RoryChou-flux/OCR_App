package com.example.vision;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;

public class DocumentActivity extends AppCompatActivity {
    private static final String TAG = "DocumentActivity";

    private RecyclerView photoGrid;
    private PhotoAdapter photoAdapter;
    private final ArrayList<DocumentPhotoManager.PhotoItem> photoItems = new ArrayList<>();
    private TextView photoCountText;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document);

        // 恢复保存的状态
        if (savedInstanceState != null) {
            ArrayList<DocumentPhotoManager.PhotoItem> savedItems =
                    savedInstanceState.getParcelableArrayList("photoItems");
            if (savedItems != null) {
                photoItems.addAll(savedItems);
            }
        }

        // 获取Intent传递的数据
        handleIntentData(getIntent());

        // 初始化视图
        initViews();

        // 设置点击事件
        setupClickListeners();

        // 初始化进度对话框
        initProgressDialog();
    }

    private void handleIntentData(@NonNull Intent intent) {
        String imagePath = intent.getStringExtra("imagePath");
        String thumbnailPath = intent.getStringExtra("thumbnailPath");
        long timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis());

        if (imagePath != null && thumbnailPath != null) {
            Log.d(TAG, "接收到新图片: " + imagePath);
            photoItems.add(new DocumentPhotoManager.PhotoItem(imagePath, thumbnailPath, timestamp));
        }
    }

    private void initViews() {
        // 初始化RecyclerView
        photoGrid = findViewById(R.id.photoGrid);
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 && photoItems.size() == 1 ? 2 : 1;
            }
        });
        photoGrid.setLayoutManager(layoutManager);

        // 初始化适配器
        photoAdapter = new PhotoAdapter(photoItems, new PhotoAdapter.PhotoActionListener() {
            @Override
            public void onPhotoClick(int position) {
                showPhotoPreview(position);
            }

            @Override
            public void onDeleteClick(int position) {
                confirmDeletePhoto(position);
            }
        });

        photoGrid.setAdapter(photoAdapter);

        // 初始化计数文本
        photoCountText = findViewById(R.id.photoCountText);
        updatePhotoCount();
    }

    private void setupClickListeners() {
        // 返回按钮
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> onBackPressed());

        // 添加照片按钮
        FloatingActionButton addButton = findViewById(R.id.addPhotoButton);
        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CameraActivity.class);
            intent.putExtra("mode", "document");
            startActivity(intent);
        });

        // 导出PDF按钮
        findViewById(R.id.exportPdfButton).setOnClickListener(v -> {
            if (photoItems.isEmpty()) {
                Toast.makeText(this, R.string.no_photos, Toast.LENGTH_SHORT).show();
                return;
            }
            exportToPdf();
        });

        // 导出PNG按钮
        findViewById(R.id.exportPngButton).setOnClickListener(v -> {
            if (photoItems.isEmpty()) {
                Toast.makeText(this, R.string.no_photos, Toast.LENGTH_SHORT).show();
                return;
            }
            exportToPng();
        });
    }

    private void initProgressDialog() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(false);
        progressDialog.setMessage(getString(R.string.exporting));
    }

    private void updatePhotoCount() {
        photoCountText.setText(String.format(getString(R.string.photo_count_format), photoItems.size()));
    }

    private void showPhotoPreview(int position) {
        DocumentPhotoManager.PhotoItem item = photoItems.get(position);
        Intent previewIntent = new Intent(this, PhotoPreviewActivity.class);
        previewIntent.putExtra("photo_path", item.getOriginalPath());
        previewIntent.putExtra("position", position + 1);
        previewIntent.putExtra("total", photoItems.size());
        startActivity(previewIntent);
    }

    private void confirmDeletePhoto(int position) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    photoItems.remove(position);
                    photoAdapter.notifyItemRemoved(position);
                    photoAdapter.notifyItemRangeChanged(position, photoItems.size());
                    updatePhotoCount();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void exportToPdf() {
        showExportProgress(true);
        new Thread(() -> {
            try {
                String outputPath = DocumentExporter.exportToPdf(this, photoItems);
                runOnUiThread(() -> {
                    showExportProgress(false);
                    showExportSuccess("PDF", outputPath);
                });
            } catch (Exception e) {
                Log.e(TAG, "PDF导出失败", e);
                runOnUiThread(() -> {
                    showExportProgress(false);
                    Toast.makeText(this, getString(R.string.export_failed) + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void exportToPng() {
        showExportProgress(true);
        new Thread(() -> {
            try {
                String outputPath = DocumentExporter.exportToPng(this, photoItems);
                runOnUiThread(() -> {
                    showExportProgress(false);
                    showExportSuccess("PNG", outputPath);
                });
            } catch (Exception e) {
                Log.e(TAG, "PNG导出失败", e);
                runOnUiThread(() -> {
                    showExportProgress(false);
                    Toast.makeText(this, getString(R.string.export_failed) + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showExportProgress(boolean show) {
        if (show) {
            progressDialog.show();
        } else {
            progressDialog.dismiss();
        }
    }

    private void showExportSuccess(String type, String path) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.export_success)
                .setMessage(getString(R.string.export_success_message, type))
                .setPositiveButton(R.string.share, (dialog, which) -> shareFile(path, type))
                .setNegativeButton(R.string.confirm, null)
                .show();
    }

    private void shareFile(String path, String type) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(type.equals("PDF") ? "application/pdf" : "image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + path));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_file)));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList("photoItems", photoItems);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntentData(intent);
        photoAdapter.notifyDataSetChanged();
        updatePhotoCount();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}