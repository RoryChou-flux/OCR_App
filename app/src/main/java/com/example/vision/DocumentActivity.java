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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
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
import java.util.Set;
import java.lang.ref.WeakReference;

public class DocumentActivity extends AppCompatActivity implements PhotoAdapter.PhotoActionListener {
    private static final String TAG = "DocumentActivity";
    private static WeakReference<DocumentActivity> instanceRef;

    private RecyclerView photoGrid;
    private PhotoAdapter photoAdapter;
    private TextView photoCountText;
    private final ArrayList<DocumentPhotoManager.PhotoItem> photoItems = new ArrayList<>();
    private AlertDialog progressDialog;
    private AlertDialog processingDialog;
    private View processingProgress;
    private volatile boolean isProcessing = false;

    private String currentSelectionMode = null;
    private MaterialButton selectAllButton;
    private MaterialButton cancelSelectionButton;
    private MaterialButton confirmSelectionButton;
    private boolean isSelectionMode = false;

    private ActivityResultLauncher<Intent> previewLauncher;

    public static DocumentActivity getInstance() {
        return instanceRef != null ? instanceRef.get() : null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document);
        instanceRef = new WeakReference<>(this);

        initActivityResult();

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
        initSelectionControls();

        if (getIntent().hasExtra("imagePath")) {
            handleNewPhoto(getIntent().getStringExtra("imagePath"));
        }
    }

    private void initActivityResult() {
        previewLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handlePreviewResult(result.getData());
                    }
                }
        );
    }

    private void initViews() {
        photoGrid = findViewById(R.id.photoGrid);
        processingProgress = findViewById(R.id.processingProgress);
        selectAllButton = findViewById(R.id.selectAllButton);
        cancelSelectionButton = findViewById(R.id.cancelSelectionButton);
        confirmSelectionButton = findViewById(R.id.confirmSelectionButton);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        photoGrid.setLayoutManager(layoutManager);

        photoAdapter = new PhotoAdapter(photoItems, this);
        photoGrid.setAdapter(photoAdapter);
        photoCountText = findViewById(R.id.photoCountText);
        updatePhotoCount();
    }

    private void setupClickListeners() {
        findViewById(R.id.backButton).setOnClickListener(v -> onBackPressed());

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

        FloatingActionButton addButton = findViewById(R.id.addPhotoButton);
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

        confirmSelectionButton.setOnClickListener(v -> {
            if (!isProcessing && isSelectionMode) {
                processSelectedItems();
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

    private void showProcessingDialog() {
        runOnUiThread(() -> {
            if (!isFinishing() && progressDialog != null) {
                progressDialog.show();
            }
        });
    }

    private void hideProcessingDialog() {
        runOnUiThread(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        });
    }

    private void initSelectionControls() {
        selectAllButton.setOnClickListener(v -> photoAdapter.selectAll());
        cancelSelectionButton.setOnClickListener(v -> exitSelectionMode());
        confirmSelectionButton.setOnClickListener(v -> processSelectedItems());
        updateSelectionModeViews(false);
    }

    private void enterSelectionMode(String mode) {
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

    private void updateSelectionModeViews(boolean showSelection) {
        selectAllButton.setVisibility(showSelection ? View.VISIBLE : View.GONE);
        cancelSelectionButton.setVisibility(showSelection ? View.VISIBLE : View.GONE);
    }

    private void handlePreviewResult(Intent data) {
        try {
            String processedPath = data.getStringExtra("processed_path");
            String thumbnailPath = data.getStringExtra("thumbnail_path");
            String originalPath = data.getStringExtra("original_path");

            if (originalPath != null && processedPath != null) {
                int position = findPhotoPosition(originalPath);
                if (position != -1) {
                    updatePhotoItem(position, processedPath, thumbnailPath);
                } else {
                    Log.e(TAG, "Original path not found in photoItems: " + originalPath);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating preview result", e);
            Toast.makeText(this, "更新预览结果失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private int findPhotoPosition(String originalPath) {
        for (int i = 0; i < photoItems.size(); i++) {
            if (photoItems.get(i).getOriginalPath().equals(originalPath)) {
                return i;
            }
        }
        return -1;
    }

    private void updatePhotoItem(int position, String processedPath, String thumbnailPath) {
        DocumentPhotoManager.PhotoItem oldItem = photoItems.get(position);
        DocumentPhotoManager.PhotoItem newItem = new DocumentPhotoManager.PhotoItem(
                processedPath,
                thumbnailPath != null ? thumbnailPath : oldItem.getThumbnailPath(),
                System.currentTimeMillis()
        );

        photoItems.set(position, newItem);
        photoAdapter.notifyItemChanged(position);

        DocumentHistoryManager.HistoryItem historyItem = new DocumentHistoryManager.HistoryItem(
                oldItem.getOriginalPath(),
                processedPath,
                thumbnailPath,
                System.currentTimeMillis()
        );
        DocumentHistoryManager.addHistory(this, historyItem);
    }

    private void showPhotoPreview(int position) {
        if (isProcessing) return;

        try {
            DocumentPhotoManager.PhotoItem item = photoItems.get(position);
            File photoFile = new File(item.getOriginalPath());
            if (!photoFile.exists() || !photoFile.canRead()) {
                Toast.makeText(this, "无法访问图片文件", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent previewIntent = new Intent(this, PhotoPreviewActivity.class);
            previewIntent.putExtra("photo_path", item.getOriginalPath());
            previewIntent.putExtra("original_path", item.getOriginalPath());
            previewIntent.putExtra("position", position);
            previewIntent.putExtra("total", photoItems.size());

            previewLauncher.launch(previewIntent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        } catch (Exception e) {
            Log.e(TAG, "Error showing preview", e);
            Toast.makeText(this, "无法显示预览：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processSelectedItems() {
        final Set<Integer> selectedPositions = photoAdapter.getSelectedItems();
        if (selectedPositions.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_select_photos), Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<DocumentPhotoManager.PhotoItem> selectedItems = new ArrayList<>();
        for (Integer position : selectedPositions) {
            selectedItems.add(photoItems.get(position));
        }

        if ("pdf".equals(currentSelectionMode)) {
            exportSelectedToPdf(selectedItems);
        } else if ("png".equals(currentSelectionMode)) {
            exportSelectedToPng(selectedItems);
        } else if ("correct".equals(currentSelectionMode)) {
            processSelectedDocuments(selectedItems);
        } else {
            Toast.makeText(this, "无效的选择模式", Toast.LENGTH_SHORT).show();
        }
    }

    private void processSelectedDocuments(ArrayList<DocumentPhotoManager.PhotoItem> items) {
        final Set<Integer> selectedPositions = photoAdapter.getSelectedItems();
        showProcessingDialog();

        new Thread(() -> {
            try {
                ArrayList<DocumentPhotoManager.PhotoItem> processedItems = new ArrayList<>();
                for (DocumentPhotoManager.PhotoItem item : items) {
                    File sourceFile = new File(item.getOriginalPath());
                    if (!sourceFile.exists() || !sourceFile.canRead()) {
                        throw new IOException("Source file not accessible: " + item.getOriginalPath());
                    }

                    // 创建默认的四个角点
                    Bitmap bitmap = BitmapFactory.decodeFile(item.getOriginalPath());
                    if (bitmap != null) {
                        try {
                            float width = bitmap.getWidth();
                            float height = bitmap.getHeight();
                            PointF[] corners = new PointF[] {
                                    new PointF(0, 0),
                                    new PointF(width, 0),
                                    new PointF(width, height),
                                    new PointF(0, height)
                            };

                            Uri sourceUri = Uri.fromFile(sourceFile);
                            DocumentProcessor.DocumentResult result =
                                    DocumentProcessor.processDocument(this, sourceUri, corners);

                            processedItems.add(new DocumentPhotoManager.PhotoItem(
                                    result.processedPath,
                                    result.thumbnailPath,
                                    System.currentTimeMillis()
                            ));

                            DocumentHistoryManager.addHistory(this, new DocumentHistoryManager.HistoryItem(
                                    item.getOriginalPath(),
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
                    } finally {hideProcessingDialog();
                        exitSelectionMode();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error processing documents", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.process_failed) + ": " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    hideProcessingDialog();
                    exitSelectionMode();
                });
            }
        }).start();
    }

    private void exportSelectedToPdf(ArrayList<DocumentPhotoManager.PhotoItem> items) {
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

    private void exportSelectedToPng(ArrayList<DocumentPhotoManager.PhotoItem> items) {
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

    private void handleNewPhoto(String imagePath) {
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
            new Thread(() -> {
                try {
                    String thumbnailPath = createThumbnail(destFile.getAbsolutePath());
                    runOnUiThread(() -> {
                        try {
                            DocumentPhotoManager.PhotoItem newItem = new DocumentPhotoManager.PhotoItem(
                                    destFile.getAbsolutePath(),
                                    thumbnailPath,
                                    System.currentTimeMillis()
                            );
                            photoItems.add(newItem);
                            photoAdapter.notifyItemInserted(photoItems.size() - 1);
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
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest);
             FileChannel sourceChannel = fis.getChannel();
             FileChannel destChannel = fos.getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }

    private String createThumbnail(String originalPath) throws Exception {
        File outputDir = new File(getFilesDir(), "thumbnails");
        if (!createDirectoryIfNeeded(outputDir)) {
            throw new IOException("Failed to create thumbnails directory");
        }

        String thumbnailPath = new File(outputDir,
                "thumb_" + System.currentTimeMillis() + ".jpg").getAbsolutePath();

        return DocumentProcessor.processThumbnail(this, originalPath, thumbnailPath);  // 修改这里
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

    private void showExportProgress(boolean show) {
        if (show && !isFinishing()) {
            progressDialog.show();
        } else if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void showExportSuccess(String type, String path) {
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
                                    getPackageName() + ".provider",
                                    imageFile);
                            uris.add(uri);
                        }
                    }
                }
            } else {
                Uri uri = FileProvider.getUriForFile(this,
                        getPackageName() + ".provider",
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

    private boolean createDirectoryIfNeeded(File dir) {
        return dir.exists() || dir.mkdirs();
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

            // 替换 switch 表达式
            String modeText;
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
                    default:
                        modeText = "";
                        break;
                }
                photoCountText.setText(modeText);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList("photoItems", photoItems);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasExtra("imagePath")) {
            handleNewPhoto(intent.getStringExtra("imagePath"));
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
        if (processingDialog != null && processingDialog.isShowing()) {
            processingDialog.dismiss();
        }
    }

    @Override
    public void onBackPressed() {
        if (!isSelectionMode) {
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
        } else {
            exitSelectionMode();
        }
    }

    public static void addNewPhoto(String imagePath) {
        DocumentActivity instance = getInstance();
        if (instance != null) {
            instance.handleNewPhoto(imagePath);
        }
    }
}