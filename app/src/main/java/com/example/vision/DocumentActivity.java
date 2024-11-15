package com.example.vision;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

public class DocumentActivity extends AppCompatActivity {
    private static final String TAG = "DocumentActivity";

    private RecyclerView photoGrid;
    private PhotoAdapter photoAdapter;
    private final ArrayList<DocumentPhotoManager.PhotoItem> photoItems = new ArrayList<>();
    private TextView photoCountText;
    private AlertDialog progressDialog;
    private AlertDialog processingDialog;
    private View processingProgress;
    private volatile boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document);

        if (savedInstanceState != null) {
            ArrayList<DocumentPhotoManager.PhotoItem> savedItems =
                    savedInstanceState.getParcelableArrayList("photoItems");
            if (savedItems != null) {
                photoItems.addAll(savedItems);
            }
        }

        initViews();
        setupClickListeners();
        initProgressDialogs();

        if (getIntent().hasExtra("imagePath")) {
            handleNewPhoto(getIntent());
        }
    }

    private void initViews() {
        photoGrid = findViewById(R.id.photoGrid);
        processingProgress = findViewById(R.id.processingProgress);

        // 使用LinearLayoutManager替代GridLayoutManager，实现单列滚动列表
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        photoGrid.setLayoutManager(layoutManager);

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
        photoCountText = findViewById(R.id.photoCountText);
        updatePhotoCount();
    }

    private void setupClickListeners() {
        findViewById(R.id.backButton).setOnClickListener(v -> onBackPressed());

        findViewById(R.id.reshootButton).setOnClickListener(v -> {
            if (!isProcessing) {
                photoItems.clear();
                photoAdapter.notifyDataSetChanged();
                updatePhotoCount();
                startCamera();
            }
        });

        findViewById(R.id.correctAllButton).setOnClickListener(v -> {
            if (photoItems.isEmpty()) {
                Toast.makeText(this, R.string.no_photos, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isProcessing) {
                processAllDocuments();
            }
        });

        FloatingActionButton addButton = findViewById(R.id.addPhotoButton);
        addButton.setOnClickListener(v -> {
            if (!isProcessing) {
                startCamera();
            }
        });

        MaterialButton exportPdfButton = findViewById(R.id.exportPdfButton);
        exportPdfButton.setOnClickListener(v -> {
            if (photoItems.isEmpty()) {
                Toast.makeText(this, R.string.no_photos, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isProcessing) {
                exportToPdf();
            }
        });

        MaterialButton exportPngButton = findViewById(R.id.exportPngButton);
        exportPngButton.setOnClickListener(v -> {
            if (photoItems.isEmpty()) {
                Toast.makeText(this, R.string.no_photos, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isProcessing) {
                exportToPng();
            }
        });
    }

    private void initProgressDialogs() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(R.layout.dialog_processing)
                .setCancelable(false);

        progressDialog = builder.create();
        processingDialog = builder.create();
    }

    private void handleNewPhoto(Intent intent) {
        String imagePath = intent.getStringExtra("imagePath");
        if (imagePath == null) {
            Log.e(TAG, "No image path provided");
            return;
        }

        try {
            File sourceFile = new File(imagePath);
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                throw new IOException("Source file not accessible: " + imagePath);
            }

            File outputDir = new File(getFilesDir(), "documents");
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IOException("Failed to create output directory");
            }

            // 生成唯一文件名
            String timestamp = String.valueOf(System.currentTimeMillis());
            File destFile = new File(outputDir, "IMG_" + timestamp + ".jpg");
            copyFile(sourceFile, destFile);

            showProcessing(true);
            new Thread(() -> {
                try {
                    String thumbnailPath = createThumbnail(destFile.getAbsolutePath());
                    DocumentPhotoManager.PhotoItem newItem = new DocumentPhotoManager.PhotoItem(
                            destFile.getAbsolutePath(),
                            thumbnailPath,
                            System.currentTimeMillis()
                    );

                    runOnUiThread(() -> {
                        try {
                            photoItems.add(0, newItem);
                            photoAdapter.notifyItemInserted(0);
                            photoGrid.scrollToPosition(0);
                            updatePhotoCount();
                        } finally {
                            showProcessing(false);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to process image", e);
                    runOnUiThread(() -> {
                        showProcessing(false);
                        Toast.makeText(this, getString(R.string.process_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
                }
            }).start();

        } catch (IOException e) {
            Log.e(TAG, "Error handling new photo", e);
            Toast.makeText(this, getString(R.string.process_failed) + ": " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showProcessing(boolean show) {
        isProcessing = show;
        runOnUiThread(() -> {
            processingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        });
    }

    private void copyFile(File source, File dest) throws IOException {
        try (FileChannel sourceChannel = new FileInputStream(source).getChannel();
             FileChannel destChannel = new FileOutputStream(dest).getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }

    private String createThumbnail(String originalPath) throws Exception {
        File outputDir = new File(getFilesDir(), "thumbnails");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String thumbnailPath = new File(outputDir,
                "thumb_" + System.currentTimeMillis() + ".jpg").getAbsolutePath();

        return DocumentProcessor.createThumbnail(this, originalPath, thumbnailPath);
    }

    private void startCamera() {
        Intent intent = new Intent(this, CameraActivity.class);
        intent.putExtra("mode", "document");
        startActivity(intent);
    }

    private void showPhotoPreview(int position) {
        if (isProcessing) return;

        DocumentPhotoManager.PhotoItem item = photoItems.get(position);
        Intent previewIntent = new Intent(this, PhotoPreviewActivity.class);
        previewIntent.putExtra("photo_path", item.getOriginalPath());  // 使用原图路径
        previewIntent.putExtra("position", photoItems.size() - position);
        previewIntent.putExtra("total", photoItems.size());

        // 开启预览活动
        startActivity(previewIntent);
        // 添加过渡动画
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void processAllDocuments() {
        showProcessing(true);
        new Thread(() -> {
            try {
                ArrayList<DocumentPhotoManager.PhotoItem> processedItems = new ArrayList<>();
                for (DocumentPhotoManager.PhotoItem item : photoItems) {
                    DocumentProcessor.DocumentResult result =
                            DocumentProcessor.processDocument(this, item.getOriginalPath());
                    processedItems.add(new DocumentPhotoManager.PhotoItem(
                            result.processedPath,
                            result.thumbnailPath,
                            System.currentTimeMillis()
                    ));
                }

                runOnUiThread(() -> {
                    showProcessing(false);
                    photoItems.clear();
                    photoItems.addAll(processedItems);
                    photoAdapter.notifyDataSetChanged();
                    Toast.makeText(this, R.string.process_all_success, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error processing documents", e);
                runOnUiThread(() -> {
                    showProcessing(false);
                    Toast.makeText(this,
                            getString(R.string.process_failed) + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void confirmDeletePhoto(int position) {
        if (isProcessing) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    DocumentPhotoManager.PhotoItem item = photoItems.remove(position);
                    photoAdapter.notifyItemRemoved(position);
                    photoAdapter.notifyItemRangeChanged(position, photoItems.size());
                    updatePhotoCount();
                    new File(item.getOriginalPath()).delete();
                    new File(item.getThumbnailPath()).delete();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updatePhotoCount() {
        photoCountText.setText(String.format(getString(R.string.photo_count_format),
                photoItems.size()));
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
                    Toast.makeText(this,
                            getString(R.string.export_failed) + ": " + e.getMessage(),
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
                    Toast.makeText(this,
                            getString(R.string.export_failed) + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showExportProgress(boolean show) {
        if (show && !isFinishing()) {
            progressDialog.show();
        } else if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void showExportSuccess(String type, String path) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.export_success)
                .setMessage(getString(R.string.export_success_message, type))
                .setPositiveButton(R.string.share, (dialog, which) -> shareFile(path, type))
                .setNegativeButton(R.string.confirm, null)
                .show();
    }

    private void shareFile(String path, String type) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(type.equals("PDF") ? "application/pdf" : "image/*");
        Uri contentUri = DocumentExporter.getContentUri(this, new File(path));
        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
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
        setIntent(intent); // 确保更新当前intent
        if (intent.hasExtra("imagePath")) {
            handleNewPhoto(intent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        if (processingDialog != null && processingDialog.isShowing()) {
            processingDialog.dismiss();
        }
    }

    @Override
    public void onBackPressed() {
        if (!photoItems.isEmpty() && !isProcessing) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.cancel)
                    .setMessage("是否确定退出？当前拍摄的照片将会保存。")
                    .setPositiveButton(R.string.confirm, (dialog, which) -> finish())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }
}