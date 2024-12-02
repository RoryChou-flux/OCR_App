package com.example.vision;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import com.bumptech.glide.Glide;
import com.example.vision.base.BaseActivity;
import com.example.vision.network.SimpletexApiManager;
import com.google.android.material.textfield.TextInputEditText;
import com.yalantis.ucrop.UCrop;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;
import id.zelory.compressor.Compressor;

public class PdfActivity extends BaseActivity {
    private static final String TAG = "PdfActivity";

    // UI组件
    private TextInputEditText pdfInput;
    private FrameLayout uploadArea;
    private ImageView originalImage;

    // Activity启动器
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private Uri currentPhotoUri;
    private File currentPhotoFile;

    // 剪贴板管理器
    private ClipboardManager clipboardManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_pdf);

            clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

            initViews();
            initActivityLaunchers();
            setupClickListeners();
            handleIncomingImage();
        } catch (Exception e) {
            Log.e(TAG, "onCreate error: ", e);
            showToast("初始化失败: " + e.getMessage());
            finish();
        }
    }

    private void initViews() {
        // 初始化工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("文档识别");
        }

        // 初始化UI组件
        pdfInput = findViewById(R.id.pdfInput);
        uploadArea = findViewById(R.id.uploadArea);
        originalImage = findViewById(R.id.originalImage);
    }

    private void initActivityLaunchers() {
        // 相机启动器
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (currentPhotoUri != null) {
                            startCrop(currentPhotoUri);
                        }
                    }
                }
        );

        // 图库启动器
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            startCrop(selectedImageUri);
                        }
                    }
                }
        );
    }

    private void setupClickListeners() {
        // 上传区域点击
        uploadArea.setOnClickListener(v -> showImageSourceDialog());

        // 复制按钮点击事件
        findViewById(R.id.copyTextButton).setOnClickListener(v -> copyText(false));
        findViewById(R.id.copyMarkdownButton).setOnClickListener(v -> copyText(true));

        // 模式切换按钮点击事件
        findViewById(R.id.toggleModeButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, LatexActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            finish();
        });
    }

    private void handleIncomingImage() {
        String imagePath = getIntent().getStringExtra("imagePath");
        if (imagePath != null) {
            File imageFile = new File(imagePath);
            if (imageFile.exists()) {
                displayOriginalImage(imageFile);
                recognizePdf(imageFile);
            }
        }
    }

    private void showImageSourceDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("选择图片来源")
                .setItems(new String[]{"拍照", "从相册选择"}, (dialog, which) -> {
                    if (which == 0) {
                        startCamera();
                    } else {
                        openGallery();
                    }
                })
                .show();
    }

    private void startCamera() {
        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File photoFile = createImageFile();
            if (photoFile != null) {
                currentPhotoFile = photoFile;
                currentPhotoUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".provider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                cameraLauncher.launch(takePictureIntent);
            }
        } catch (IOException ex) {
            showToast("创建图片文件失败");
            Log.e(TAG, "Error creating image file", ex);
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "PDF_" + timeStamp + "_";
        File storageDir = new File(getExternalFilesDir(null), "PDF");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void startCrop(Uri sourceUri) {
        String destinationFileName = "CROPPED_" + System.currentTimeMillis() + ".jpg";
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), destinationFileName));

        UCrop.Options options = new UCrop.Options();
        options.setCompressionQuality(90);
        options.setToolbarColor(getColor(R.color.colorPrimary));
        options.setStatusBarColor(getColor(R.color.colorPrimaryDark));
        options.setToolbarTitle("裁剪图片");

        UCrop.of(sourceUri, destinationUri)
                .withOptions(options)
                .start(this);
    }

    private void displayOriginalImage(File imageFile) {
        Glide.with(this)
                .load(imageFile)
                .into(originalImage);
    }

    private void recognizePdf(File imageFile) {
        showLoading("正在识别文档...");

        new Thread(() -> {
            try {
                File compressedImageFile = new Compressor(this)
                        .setQuality(75)
                        .setMaxWidth(1280)
                        .setMaxHeight(720)
                        .compressToFile(imageFile);

                runOnUiThread(() -> {
                    SimpletexApiManager.getInstance().recognizePdf(
                            compressedImageFile,
                            new SimpletexApiManager.ApiCallback() {
                                @Override
                                public void onSuccess(String text) {
                                    hideLoading();
                                    pdfInput.setText(text);
                                }

                                @Override
                                public void onFailure(String error) {
                                    hideLoading();
                                    showToast("识别失败: " + error);
                                    pdfInput.setText("");
                                }
                            }
                    );
                });
            } catch (IOException e) {
                hideLoading();
                e.printStackTrace();
                runOnUiThread(() -> showToast("图片压缩失败: " + e.getMessage()));
            }
        }).start();
    }

    private void copyText(boolean asMarkdown) {
        String text = pdfInput.getText().toString();
        if (text.trim().isEmpty()) {
            showToast("没有可复制的内容");
            return;
        }

        // 如果需要转换为Markdown格式
        if (asMarkdown) {
            text = convertToMarkdown(text);
        }

        ClipData clip = ClipData.newPlainText(asMarkdown ? "Markdown Text" : "Plain Text", text);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(clip);
            showToast("已复制到剪贴板");
        }
    }

    private String convertToMarkdown(String text) {
        // 检查文本中是否已经包含Markdown语法
        if (text.contains("```") || text.contains("$$")) {
            return text;
        }

        StringBuilder markdown = new StringBuilder();
        String[] lines = text.split("\n");
        boolean inMathBlock = false;

        for (String line : lines) {
            String trimmedLine = line.trim();

            // 忽略空行
            if (trimmedLine.isEmpty()) {
                markdown.append("\n");
                continue;
            }

            // 检查是否是数学公式
            if (containsMathSymbols(trimmedLine)) {
                if (!inMathBlock) {
                    markdown.append("$$\n");
                    inMathBlock = true;
                }
                markdown.append(trimmedLine).append("\n");
            } else {
                if (inMathBlock) {
                    markdown.append("$$\n\n");
                    inMathBlock = false;
                }
                markdown.append(trimmedLine).append("\n");
            }
        }

        // 确保最后的数学块被关闭
        if (inMathBlock) {
            markdown.append("$$\n");
        }

        return markdown.toString().trim();
    }

    private boolean containsMathSymbols(String text) {
        // 定义包含常见数学符号的正则表达式
        String mathSymbolPattern = "[=+\\-×÷∫∑∏√±∞≠≈<>≤≥∈∉∪∩∆∇∂∫∮∝∞∟∠∡∢∴∵∶∷∼∽≂≃≄≅≆≇≈≉≊≋≌≍≎≏]";
        return Pattern.compile(mathSymbolPattern).matcher(text).find() ||
                text.contains("\\") || // LaTeX命令
                text.matches(".*[a-zA-Z][²³].*") || // 上标
                text.matches(".*\\d+/\\d+.*"); // 分数
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK && data != null) {
            final Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                File imageFile = new File(resultUri.getPath());
                if (imageFile.exists()) {
                    displayOriginalImage(imageFile);
                    recognizePdf(imageFile);
                }
            }
        } else if (resultCode == UCrop.RESULT_ERROR && data != null) {
            final Throwable cropError = UCrop.getError(data);
            showToast("裁剪失败: " + (cropError != null ? cropError.getMessage() : "未知错误"));
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            // 清理临时文件
            if (currentPhotoFile != null && currentPhotoFile.exists()) {
                currentPhotoFile.delete();
            }
        }
    }
}