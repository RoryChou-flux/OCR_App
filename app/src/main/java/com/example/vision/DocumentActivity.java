package com.example.vision;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.FileProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Set;
import java.lang.ref.WeakReference;

@SuppressWarnings({"deprecation", "ResultOfMethodCallIgnored"})
public class DocumentActivity extends AppCompatActivity implements PhotoAdapter.PhotoActionListener {
    private static final String TAG = "DocumentActivity";
    private static WeakReference<DocumentActivity> instanceRef;

    private RecyclerView photoGrid;
    private PhotoAdapter photoAdapter;
    private TextView photoCountText;
    private final ArrayList<DocumentPhotoManager.PhotoItem> photoItems = new ArrayList<>();
    private AlertDialog progressDialog;
    private View processingProgress;
    private volatile boolean isProcessing = false;

    private String currentSelectionMode = null;
    private MaterialButton selectAllButton;
    private MaterialButton cancelSelectionButton;
    private MaterialButton confirmSelectionButton;
    private boolean isSelectionMode = false;

    private final ActivityResultLauncher<Intent> previewLauncher;

    public static DocumentActivity getInstance() {
        return instanceRef != null ? instanceRef.get() : null;
    }

    public DocumentActivity() {
        previewLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handlePreviewResult(result.getData());
                    }
                }
        );
    }

    @Override
    protected void onCreate(@NonNull Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document);
        instanceRef = new WeakReference<>(this);

        if (savedInstanceState != null) {
            ArrayList<DocumentPhotoManager.PhotoItem> savedItems =
                    savedInstanceState.getParcelableArrayList("photoItems");
            if (savedItems != null) {
                photoItems.addAll(savedItems);
            }
        }

        initViews();
        setupClickListeners();
        initProgressDialog();
        initSelectionControls();
        setupBackPressedCallback();

        String imagePath = getIntent().getStringExtra("imagePath");
        if (imagePath != null) {
            handleNewPhoto(imagePath);
        }
    }

    private void setupBackPressedCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode();
                } else if (!photoItems.isEmpty() && !isProcessing) {
                    new MaterialAlertDialogBuilder(DocumentActivity.this)
                            .setTitle(R.string.cancel)
                            .setMessage("是否确定退出？当前拍摄的照片将会保存。")
                            .setPositiveButton(R.string.confirm, (dialog, which) -> finish())
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                } else {
                    finish();
                }
            }
        });
    }

    private void initViews() {
        photoGrid = findViewById(R.id.photoGrid);
        processingProgress = findViewById(R.id.processingProgress);
        selectAllButton = findViewById(R.id.selectAllButton);
        cancelSelectionButton = findViewById(R.id.cancelSelectionButton);
        confirmSelectionButton = findViewById(R.id.confirmSelectionButton);
        photoCountText = findViewById(R.id.photoCountText);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        photoGrid.setLayoutManager(layoutManager);

        photoAdapter = new PhotoAdapter(photoItems, this);
        photoGrid.setAdapter(photoAdapter);
        updatePhotoCount();
    }

    private void setupClickListeners() {
        FloatingActionButton addButton = findViewById(R.id.addPhotoButton);

        findViewById(R.id.reshootButton).setOnClickListener(v -> {
            if (!isProcessing && !isSelectionMode) {
                photoItems.clear();
                photoAdapter.notifyDataSetChanged();
                updatePhotoCount();
                finish();
            }
        });

        findViewById(R.id.correctAllButton).setOnClickListener(v -> {
            if (photoItems.isEmpty()) {
                Toast.makeText(this, R.string.no_photos, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isProcessing && !isSelectionMode) {
                enterSelectionMode("correct");
            }
        });

        addButton.setOnClickListener(v -> {
            if (!isProcessing && !isSelectionMode) {
                startCamera();
            }
        });

        findViewById(R.id.exportPdfButton).setOnClickListener(v -> {
            if (photoItems.isEmpty()) {
                Toast.makeText(this, R.string.no_photos, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isProcessing && !isSelectionMode) {
                enterSelectionMode("pdf");
            }
        });

        findViewById(R.id.exportPngButton).setOnClickListener(v -> {
            if (photoItems.isEmpty()) {
                Toast.makeText(this, R.string.no_photos, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isProcessing && !isSelectionMode) {
                enterSelectionMode("png");
            }
        });
    }
    private void initProgressDialog() {
        progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(R.layout.dialog_processing)
                .setCancelable(false)
                .create();
    }

    private boolean showPhotoPreview(int position) {
        if (isProcessing) return false;

        try {
            DocumentPhotoManager.PhotoItem item = photoItems.get(position);
            Log.d(TAG, "准备预览图片: " + item.getOriginalPath());

            File photoFile = new File(item.getOriginalPath());
            if (!photoFile.exists() || !photoFile.canRead()) {
                Toast.makeText(this, "无法访问图片文件", Toast.LENGTH_SHORT).show();
                return false;
            }

            Intent previewIntent = new Intent(this, PhotoPreviewActivity.class);
            // 确保传递所有必要的参数
            previewIntent.putExtra("photo_path", item.getOriginalPath());    // 当前显示的图片路径
            previewIntent.putExtra("original_path", item.getOriginalPath()); // 原始图片路径
            previewIntent.putExtra("processed_path", item.getProcessedPath()); // 处理后的路径
            previewIntent.putExtra("thumbnail_path", item.getThumbnailPath()); // 缩略图路径
            previewIntent.putExtra("position", position);
            previewIntent.putExtra("total", photoItems.size());

            Log.d(TAG, String.format("启动预览，参数:\n" +
                            "photo_path: %s\n" +
                            "original_path: %s\n" +
                            "processed_path: %s\n" +
                            "thumbnail_path: %s",
                    item.getOriginalPath(),
                    item.getOriginalPath(),
                    item.getProcessedPath(),
                    item.getThumbnailPath()));

            previewLauncher.launch(previewIntent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "预览图片失败", e);
            Toast.makeText(this, "无法显示预览：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void handleNewPhoto(@NonNull String imagePath) {
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
            if (!createDirectoryIfNeeded(outputDir)) {
                throw new IOException("Failed to create output directory");
            }

            String timestamp = String.valueOf(System.currentTimeMillis());
            File destFile = new File(outputDir, "IMG_" + timestamp + ".jpg");
            copyFile(sourceFile, destFile);

            showProcessing(true);
            processNewPhoto(destFile);

        } catch (IOException e) {
            Log.e(TAG, "Error handling new photo", e);
            Toast.makeText(this, getString(R.string.process_failed) + ": " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void processNewPhoto(@NonNull File photoFile) {
        new Thread(() -> {
            try {
                // 创建裁剪图片的副本
                File croppedDir = new File(getFilesDir(), "cropped");  // 改为 cropped 目录
                if (!croppedDir.exists()) {
                    croppedDir.mkdirs();
                }

                String timestamp = String.valueOf(System.currentTimeMillis());
                File croppedFile = new File(croppedDir, "CROP_" + timestamp + ".jpg");  // 使用 CROP_ 前缀
                copyFile(photoFile, croppedFile);

                // 创建缩略图
                String thumbnailPath = createThumbnail(croppedFile.getAbsolutePath());

                // 创建 PhotoItem，使用裁剪后文件
                DocumentPhotoManager.PhotoItem newItem = new DocumentPhotoManager.PhotoItem(
                        croppedFile.getAbsolutePath(),   // 裁剪后的文件路径
                        photoFile.getAbsolutePath(),     // 处理后的文件路径作为显示路径
                        thumbnailPath,                   // 缩略图路径
                        System.currentTimeMillis()
                );

                // 创建历史记录，保存裁剪和增强后的路径
                DocumentHistoryManager.HistoryItem historyItem = new DocumentHistoryManager.HistoryItem(
                        croppedFile.getAbsolutePath(),  // 裁剪后的文件路径
                        photoFile.getAbsolutePath(),    // 增强后的文件路径
                        thumbnailPath,                  // 缩略图路径
                        System.currentTimeMillis()
                );

                boolean success = DocumentHistoryManager.addHistory(this, historyItem);
                Log.d(TAG, String.format("历史记录添加 %s:\n裁剪后文件: %s\n增强后文件: %s",
                        success ? "成功" : "失败",
                        croppedFile.getAbsolutePath(),
                        photoFile.getAbsolutePath()));

                runOnUiThread(() -> {
                    try {
                        photoItems.add(newItem);
                        photoAdapter.notifyItemInserted(photoItems.size() - 1);
                        updatePhotoCount();
                    } finally {
                        showProcessing(false);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "处理新照片失败", e);
                runOnUiThread(() -> {
                    showProcessing(false);
                    Toast.makeText(this, getString(R.string.process_failed) + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void handlePreviewResult(Intent data) {
        try {
            // 获取传递过来的所有路径
            String originalFile = data.getStringExtra("original_file");
            String processedPath = data.getStringExtra("processed_file");
            String thumbnailPath = data.getStringExtra("thumbnail_path");
            String legacyPath = data.getStringExtra("imagePath");  // 兼容旧版本

            if (originalFile == null || processedPath == null) {
                throw new IllegalArgumentException("缺少必要的路径信息");
            }

            // 查找匹配的条目
            int position = findPhotoPosition(originalFile);

            if (position != -1) {
                Log.d(TAG, "找到匹配的图片项，位置: " + position);
                DocumentPhotoManager.PhotoItem oldItem = photoItems.get(position);

                // 创建新的项目，使用原始路径和处理后的路径
                DocumentPhotoManager.PhotoItem newItem = new DocumentPhotoManager.PhotoItem(
                        originalFile,                               // 原始图片路径
                        processedPath,                             // 处理后图片路径
                        thumbnailPath,                             // 缩略图路径
                        System.currentTimeMillis()
                );

                // 更新列表
                photoItems.set(position, newItem);
                photoAdapter.notifyItemChanged(position);

                // 添加历史记录
                DocumentHistoryManager.HistoryItem historyItem = new DocumentHistoryManager.HistoryItem(
                        originalFile,      // 原始图片路径
                        processedPath,     // 处理后图片路径
                        thumbnailPath,     // 缩略图路径
                        System.currentTimeMillis()
                );

                boolean success = DocumentHistoryManager.addHistory(this, historyItem);
                Log.d(TAG, "历史记录添加: " + (success ? "成功" : "失败"));
            } else {
                // 如果找不到现有项，添加为新项
                DocumentPhotoManager.PhotoItem newItem = new DocumentPhotoManager.PhotoItem(
                        originalFile,      // 原始图片路径
                        processedPath,     // 处理后图片路径
                        thumbnailPath,     // 缩略图路径
                        System.currentTimeMillis()
                );

                photoItems.add(newItem);
                photoAdapter.notifyItemInserted(photoItems.size() - 1);
                updatePhotoCount();
            }

        } catch (Exception e) {
            Log.e(TAG, "更新预览结果出错", e);
            Toast.makeText(this, "更新预览结果失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private void validateFile(String label, String path) throws IllegalStateException {
        if (path == null) {
            throw new IllegalStateException(label + "路径为空");
        }
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalStateException(label + "不存在: " + path);
        }
        if (!file.canRead()) {
            throw new IllegalStateException(label + "无法读取: " + path);
        }
    }

    private void enterSelectionMode(@NonNull String mode) {
        isSelectionMode = true;
        currentSelectionMode = mode;
        photoAdapter.setSelectionMode(true);
        updateSelectionModeViews(true);
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        currentSelectionMode = null;
        photoAdapter.setSelectionMode(false);

        View selectionControlsBar = findViewById(R.id.selectionControlsBar);
        View actionButtons = findViewById(R.id.actionButtons);
        selectionControlsBar.setVisibility(View.GONE);
        actionButtons.setVisibility(View.VISIBLE);
        confirmSelectionButton.setVisibility(View.GONE);

        updatePhotoCount();
    }

    private void initSelectionControls() {
        selectAllButton.setOnClickListener(v -> photoAdapter.selectAll());
        cancelSelectionButton.setOnClickListener(v -> exitSelectionMode());
        confirmSelectionButton.setOnClickListener(v -> {
            if (!isProcessing && isSelectionMode) {
                processSelectedItems();
            }
        });
        updateSelectionModeViews(false);
    }

    private void updateSelectionModeViews(boolean showSelection) {
        selectAllButton.setVisibility(showSelection ? View.VISIBLE : View.GONE);
        cancelSelectionButton.setVisibility(showSelection ? View.VISIBLE : View.GONE);
    }

    private void processSelectedItems() {
        Set<Integer> selectedPositions = photoAdapter.getSelectedItems();
        if (selectedPositions.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_select_photos), Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<DocumentPhotoManager.PhotoItem> selectedItems = new ArrayList<>();
        for (Integer position : selectedPositions) {
            selectedItems.add(photoItems.get(position));
        }

        switch (currentSelectionMode) {
            case "pdf":
                exportSelectedToPdf(selectedItems);
                break;
            case "png":
                exportSelectedToPng(selectedItems);
                break;
            case "correct":
                processSelectedDocuments(selectedItems);
                break;
            default:
                Toast.makeText(this, "无效的选择模式", Toast.LENGTH_SHORT).show();
        }
    }

    private void processSelectedDocuments(@NonNull ArrayList<DocumentPhotoManager.PhotoItem> items) {
        final Set<Integer> selectedPositions = photoAdapter.getSelectedItems();
        showProcessingDialog();
        Log.d(TAG, "开始处理文档...");

        new Thread(() -> {
            try {
                ArrayList<DocumentPhotoManager.PhotoItem> processedItems = new ArrayList<>();
                for (DocumentPhotoManager.PhotoItem item : items) {
                    File sourceFile = new File(item.getOriginalPath());
                    if (!sourceFile.exists()) {
                        throw new IOException("源文件不可访问: " + item.getOriginalPath());
                    }

                    Bitmap bitmap = BitmapFactory.decodeFile(item.getOriginalPath());
                    if (bitmap != null) {
                        try {
                            float width = bitmap.getWidth();
                            float height = bitmap.getHeight();

                            Uri sourceUri = Uri.fromFile(sourceFile);
                            DocumentProcessor.DocumentResult result =
                                    DocumentProcessor.processDocument(this, sourceUri,
                                            new PointF[] {
                                                    new PointF(0, 0),
                                                    new PointF(0, height),
                                                    new PointF(width, height),
                                                    new PointF(width, 0)
                                            }
                                    );

                            // 添加历史记录
                            DocumentHistoryManager.HistoryItem historyItem = new DocumentHistoryManager.HistoryItem(
                                    item.getOriginalPath(),
                                    result.processedPath,
                                    result.thumbnailPath,
                                    System.currentTimeMillis()
                            );

                            boolean success = DocumentHistoryManager.addHistory(this, historyItem);
                            Log.d(TAG, "History record added: " + success);

                            processedItems.add(new DocumentPhotoManager.PhotoItem(
                                    result.processedPath,
                                    result.thumbnailPath,
                                    System.currentTimeMillis()
                            ));

                        } finally {
                            bitmap.recycle();
                        }
                    }
                }

                runOnUiThread(() -> {
                    try {
                        int processedIndex = 0;
                        for (Integer position : selectedPositions) {
                            if (processedIndex < processedItems.size()) {
                                photoItems.set(position, processedItems.get(processedIndex++));
                            }
                        }
                        photoAdapter.notifyDataSetChanged();
                        Toast.makeText(this, R.string.process_all_success, Toast.LENGTH_SHORT).show();
                    } finally {
                        hideProcessingDialog();
                        exitSelectionMode();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "处理文档出错", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.process_failed) + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    hideProcessingDialog();
                    exitSelectionMode();
                });
            }
        }).start();
    }

    private void exportSelectedToPdf(@NonNull ArrayList<DocumentPhotoManager.PhotoItem> items) {
        showExportProgress(true);
        new Thread(() -> {
            try {
                for (DocumentPhotoManager.PhotoItem item : items) {
                    File sourceFile = new File(item.getOriginalPath());
                    if (!sourceFile.exists() || !sourceFile.canRead()) {
                        throw new IOException("Source file not accessible: " + item.getOriginalPath());
                    }
                }

                String outputPath = DocumentExporter.exportToPdf(this, items);
                File outputFile = new File(outputPath);
                if (!outputFile.exists() || !outputFile.canRead()) {
                    throw new IOException("Output file not accessible: " + outputPath);
                }

                runOnUiThread(() -> {
                    showExportProgress(false);
                    showExportSuccess("PDF", outputPath);
                    exitSelectionMode();
                });
            } catch (Exception e) {
                Log.e(TAG, "PDF导出失败", e);
                runOnUiThread(() -> {
                    showExportProgress(false);
                    Toast.makeText(this,
                            getString(R.string.export_failed) + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    exitSelectionMode();
                });
            }
        }).start();
    }

    private void exportSelectedToPng(@NonNull ArrayList<DocumentPhotoManager.PhotoItem> items) {
        showExportProgress(true);
        new Thread(() -> {
            try {
                for (DocumentPhotoManager.PhotoItem item : items) {
                    File sourceFile = new File(item.getOriginalPath());
                    if (!sourceFile.exists() || !sourceFile.canRead()) {
                        throw new IOException("Source file not accessible: " + item.getOriginalPath());
                    }
                }

                String outputPathWithCount = DocumentExporter.exportToPng(this, items);
                String[] parts = outputPathWithCount.split(":");
                String outputPath = parts[0];
                int successCount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

                File outputDir = new File(outputPath);
                if (!outputDir.exists()) {
                    throw new IOException("Output directory not accessible: " + outputPath);
                }

                runOnUiThread(() -> {
                    showExportProgress(false);
                    String message = getString(R.string.export_multiple_success, successCount);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.export_success)
                            .setMessage(message)
                            .setPositiveButton(R.string.share, (dialog, which) ->
                                    shareFile(outputPath, "PNG"))
                            .setNegativeButton(R.string.confirm, null)
                            .show();
                    exitSelectionMode();
                });
            } catch (Exception e) {
                Log.e(TAG, "PNG导出失败", e);
                runOnUiThread(() -> {
                    showExportProgress(false);
                    Toast.makeText(this,
                            getString(R.string.export_failed) + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    exitSelectionMode();
                });
            }
        }).start();
    }

    private int findPhotoPosition(@NonNull String originalPath) {
        for (int i = 0; i < photoItems.size(); i++) {
            if (photoItems.get(i).getOriginalPath().equals(originalPath)) {
                return i;
            }
        }
        return -1;
    }

    private void startCamera() {
        Intent intent = new Intent(this, CameraActivity.class);
        intent.putExtra("mode", "document");
        startActivity(intent);
    }

    private void confirmDeletePhoto(int position) {
        if (isProcessing) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    try {
                        DocumentPhotoManager.PhotoItem item = photoItems.remove(position);
                        photoAdapter.notifyItemRemoved(position);
                        if (position < photoItems.size()) {
                            photoAdapter.notifyItemRangeChanged(position, photoItems.size() - position);
                        }
                        updatePhotoCount();

                        File originalFile = new File(item.getOriginalPath());
                        File thumbnailFile = new File(item.getThumbnailPath());
                        if (originalFile.exists()) {
                            originalFile.delete();
                        }
                        if (thumbnailFile.exists()) {
                            thumbnailFile.delete();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "删除文件失败", e);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void copyFile(@NonNull File source, @NonNull File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest);
             FileChannel sourceChannel = fis.getChannel();
             FileChannel destChannel = fos.getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }

    private String createThumbnail(@NonNull String originalPath) throws Exception {
        File outputDir = new File(getFilesDir(), "thumbnails");
        if (!createDirectoryIfNeeded(outputDir)) {
            throw new IOException("Failed to create thumbnails directory");
        }

        String thumbnailPath = new File(outputDir,
                "thumb_" + System.currentTimeMillis() + ".jpg").getAbsolutePath();

        return DocumentProcessor.processThumbnail(this, originalPath, thumbnailPath);
    }

    private boolean showProcessing(boolean show) {
        isProcessing = show;
        if (!isFinishing()) {
            runOnUiThread(() -> processingProgress.setVisibility(show ? View.VISIBLE : View.GONE));
            return true;
        }
        return false;
    }

    private void showProcessingDialog() {
        if (!isFinishing()) {
            runOnUiThread(() -> {
                if (progressDialog != null) {
                    progressDialog.show();
                }
            });
        }
    }

    private void hideProcessingDialog() {
        runOnUiThread(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        });
    }

    private void showExportProgress(boolean show) {
        if (show && !isFinishing()) {
            progressDialog.show();
            return;
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void showExportSuccess(@NonNull String type, @NonNull String path) {
        File file = new File(path);
        String message = file.isDirectory() ?
                getString(R.string.export_multiple_success, file.listFiles() != null ? file.listFiles().length : 0) :
                getString(R.string.export_success_message, type);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.export_success)
                .setMessage(message)
                .setPositiveButton(R.string.share, (dialog, which) -> shareFile(path, type))
                .setNegativeButton(R.string.confirm, null)
                .show();
    }

    private void shareFile(@NonNull String path, @NonNull String type) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
                return;
            }

            ArrayList<Uri> uris = new ArrayList<>();
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File imageFile : files) {
                        String name = imageFile.getName().toLowerCase();
                        if (name.endsWith(".png") || name.endsWith(".jpg")) {
                            Uri uri = FileProvider.getUriForFile(this,
                                    BuildConfig.APPLICATION_ID + ".provider",
                                    imageFile);
                            uris.add(uri);
                        }
                    }
                }
            } else {
                Uri uri = FileProvider.getUriForFile(this,
                        BuildConfig.APPLICATION_ID + ".provider",
                        file);
                uris.add(uri);
            }

            if (uris.isEmpty()) {
                Toast.makeText(this, "没有可分享的文件", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent shareIntent;
            if (uris.size() > 1) {
                shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            } else {
                shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            }

            shareIntent.setType("image/*");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_file)));
        } catch (Exception e) {
            Log.e(TAG, "分享文件失败", e);
            Toast.makeText(this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePhotoCount() {
        photoCountText.setText(String.format(getString(R.string.photo_count_format),
                photoItems.size()));
    }

    private void updateSelectionUI(int selectedCount) {
        if (isSelectionMode) {
            View selectionControlsBar = findViewById(R.id.selectionControlsBar);
            View actionButtons = findViewById(R.id.actionButtons);

            selectionControlsBar.setVisibility(View.VISIBLE);
            actionButtons.setVisibility(View.GONE);

            confirmSelectionButton.setVisibility(View.VISIBLE);
            confirmSelectionButton.setText(getString(R.string.selected_count, selectedCount));
            confirmSelectionButton.setEnabled(selectedCount > 0);

            String modeText = "";
            if (currentSelectionMode != null) {
                switch (currentSelectionMode) {
                    case "pdf":
                        modeText = getString(R.string.select_mode_pdf);
                        break;
                    case "png":
                        modeText = getString(R.string.select_mode_png);
                        break;
                    case "correct":
                        modeText = getString(R.string.select_mode_correct);
                        break;
                }
                photoCountText.setText(modeText);
            }
        }
    }

    private boolean createDirectoryIfNeeded(@NonNull File dir) {
        if (dir.exists()) return true;
        return dir.mkdirs();
    }

    @Override
    public void onPhotoClick(int position) {
        showPhotoPreview(position);
    }

    @Override
    public void onDeleteClick(int position) {
        if (!isSelectionMode) {
            confirmDeletePhoto(position);
        }
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        updateSelectionUI(selectedCount);
        int itemCount = photoAdapter.getItemCount();
        if (itemCount > 0) {
            photoAdapter.notifyItemRangeChanged(0, itemCount);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList("photoItems", photoItems);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String imagePath = intent.getStringExtra("imagePath");
        if (imagePath != null) {
            handleNewPhoto(imagePath);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instanceRef != null && instanceRef.get() == this) {
            instanceRef.clear();
            instanceRef = null;
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    public static void addNewPhoto(@NonNull String imagePath) {
        DocumentActivity instance = getInstance();
        if (instance != null) {
            instance.handleNewPhoto(imagePath);
        }
    }
}
