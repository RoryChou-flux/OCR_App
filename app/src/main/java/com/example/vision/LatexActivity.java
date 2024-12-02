package com.example.vision;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import id.zelory.compressor.Compressor;

public class LatexActivity extends BaseActivity {
    private static final String TAG = "LatexActivity";

    // UI组件
    private WebView latexWebView;
    private TextInputEditText latexInput;
    private FrameLayout uploadArea;
    private ImageView originalImage;

    // Activity启动器
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private Uri currentPhotoUri;
    private File currentPhotoFile;

    // ClipboardManager
    private ClipboardManager clipboardManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_latex);

        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        initViews();
        initActivityLaunchers();
        setupClickListeners();
        handleIncomingImage();
    }

    private void initViews() {
        // 初始化工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("LaTeX识别");
        }

        // 初始化UI组件
        latexInput = findViewById(R.id.latexInput);
        uploadArea = findViewById(R.id.uploadArea);
        latexWebView = findViewById(R.id.latexWebView);
        originalImage = findViewById(R.id.originalImage);

        // 配置 WebView
        WebSettings webSettings = latexWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        latexWebView.setWebViewClient(new WebViewClient());

        // 添加 JavaScript 接口以获取 MathML
        latexWebView.addJavascriptInterface(new MathMLInterface(), "MathMLHandler");
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
        findViewById(R.id.copyLatexButton).setOnClickListener(v -> copyLatex());
        findViewById(R.id.copyMathMLButton).setOnClickListener(v -> copyMathML());
        findViewById(R.id.copyPNGButton).setOnClickListener(v -> savePNGToGallery());

        // 模式切换按钮点击事件
        findViewById(R.id.toggleModeButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, PdfActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            finish();
        });

        // LaTeX输入框变化监听
        latexInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                generateLatexPreview(s.toString());
            }
        });
    }

    private void handleIncomingImage() {
        String imagePath = getIntent().getStringExtra("imagePath");
        if (imagePath != null) {
            File imageFile = new File(imagePath);
            if (imageFile.exists()) {
                displayOriginalImage(imageFile);
                recognizeLatex(imageFile);
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
        String imageFileName = "LATEX_" + timeStamp + "_";
        File storageDir = new File(getExternalFilesDir(null), "Latex");
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

    private void recognizeLatex(File imageFile) {
        showLoading("正在识别公式...");

        new Thread(() -> {
            try {
                File compressedImageFile = new Compressor(this)
                        .setQuality(75)
                        .setMaxWidth(1280)
                        .setMaxHeight(720)
                        .compressToFile(imageFile);

                runOnUiThread(() -> {
                    SimpletexApiManager.getInstance().recognizeLatex(
                            compressedImageFile,
                            new SimpletexApiManager.ApiCallback() {
                                @Override
                                public void onSuccess(String latex) {
                                    hideLoading();
                                    latexInput.setText(latex);
                                    renderLatexToWebView(latex);
                                }

                                @Override
                                public void onFailure(String error) {
                                    hideLoading();
                                    showToast("识别失败: " + error);
                                    latexInput.setText("");
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
    private void renderLatexToWebView(String latex) {
        if (latex == null || latex.trim().isEmpty()) {
            latexWebView.loadData("", "text/html", "UTF-8");
            return;
        }

        String formattedLatex = "$$ " + latex + " $$";
        String html = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<script type=\"text/javascript\" async " +
                "src=\"https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js\">" +
                "</script>" +
                "<style>" +
                "html, body {" +
                "    height: 100%;" +
                "    margin: 0;" +
                "    padding: 0;" +
                "    display: flex;" +
                "    justify-content: center;" +
                "    align-items: center;" +
                "}" +
                "body {" +
                "    font-size: 16px;" +
                "    padding: 10px;" +
                "}" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div>" + formattedLatex + "</div>" +
                "</body>" +
                "</html>";

        latexWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void generateLatexPreview(String latex) {
        if (latex == null || latex.trim().isEmpty()) {
            latexWebView.loadData("", "text/html", "UTF-8");
            return;
        }

        String formattedLatex = "$$ " + latex + " $$";
        String html = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<script type=\"text/javascript\" async " +
                "src=\"https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js\">" +
                "</script>" +
                "<style>" +
                "html, body {" +
                "    height: 100%;" +
                "    margin: 0;" +
                "    padding: 0;" +
                "    display: flex;" +
                "    justify-content: center;" +
                "    align-items: center;" +
                "}" +
                "body {" +
                "    font-size: 16px;" +
                "    padding: 10px;" +
                "}" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div>" + formattedLatex + "</div>" +
                "</body>" +
                "</html>";

        latexWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void copyLatex() {
        String latex = latexInput.getText().toString();
        if (latex.trim().isEmpty()) {
            showToast("没有可复制的内容");
            return;
        }
        copyToClipboard("LaTeX", latex);
    }

    private void copyMathML() {
        String latex = latexInput.getText().toString();
        if (latex.trim().isEmpty()) {
            showToast("没有 LaTeX 内容可转换");
            return;
        }

        String jsCode = "MathJax.tex2mml('" + escapeJavaScript(latex) + "');";
        latexWebView.evaluateJavascript(jsCode, null);
        String getMathML = "javascript:window.MathMLHandler.processMathML(MathJax.tex2mml('" + escapeJavaScript(latex) + "'));";
        latexWebView.evaluateJavascript(getMathML, null);
    }

    private void savePNGToGallery() {
        latexWebView.post(() -> {
            Bitmap bitmap = Bitmap.createBitmap(latexWebView.getWidth(), latexWebView.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            latexWebView.draw(canvas);

            String savedImageURL = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, "RenderedFormula_" + System.currentTimeMillis() + ".png");
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/RenderedFormulas");

                    Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(uri);
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        if (fos != null) {
                            fos.flush();
                            fos.close();
                        }
                        savedImageURL = uri.toString();
                    }
                } else {
                    String imagesDir = MediaStore.Images.Media.insertImage(
                            getContentResolver(),
                            bitmap,
                            "RenderedFormula_" + System.currentTimeMillis(),
                            "Rendered LaTeX Formula"
                    );
                    savedImageURL = imagesDir;
                }

                if (savedImageURL != null) {
                    showToast("PNG 图片已保存到相册");
                } else {
                    showToast("保存 PNG 失败");
                }
            } catch (Exception e) {
                e.printStackTrace();
                showToast("保存 PNG 失败: " + e.getMessage());
            } finally {
                bitmap.recycle();
            }
        });
    }

    private void copyToClipboard(String label, String text) {
        ClipData clip = ClipData.newPlainText(label, text);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(clip);
            showToast("已复制到剪贴板");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK && data != null) {
            final Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                File imageFile = new File(resultUri.getPath());
                if (imageFile.exists()) {
                    displayOriginalImage(imageFile);
                    recognizeLatex(imageFile);
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

    private String escapeJavaScript(String latex) {
        return latex.replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r");
    }

    // 简单文本变化监听器
    private abstract class SimpleTextWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // 不需要实现
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
            // 不需要实现
        }
    }

    // JavaScript接口类
    private class MathMLInterface {
        @JavascriptInterface
        public void processMathML(String mathML) {
            runOnUiThread(() -> {
                if (mathML != null && !mathML.isEmpty()) {
                    copyToClipboard("MathML", mathML);
                    showToast("MathML 已复制到剪贴板");
                } else {
                    showToast("MathML 转换失败");
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            if (latexWebView != null) {
                latexWebView.destroy();
            }
            // 清理临时文件
            if (currentPhotoFile != null && currentPhotoFile.exists()) {
                currentPhotoFile.delete();
            }
        }
    }
}