package com.example.vision;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.InputStream;

public class DocumentCropActivity extends AppCompatActivity {
    private static final String TAG = "CropActivity";
    private static final String CROP_OUTPUT_DIR = "crop_output";
    private static final int REQUEST_PERMISSION_CODE = 1001;
    private Bitmap currentBitmap;

    private Uri sourceUri;
    private PolygonCropView cropView;
    private AlertDialog progressDialog;
    private MaterialButton doneButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        checkPermissions();
        initViews();
        handleIntent();
        setupButtons();
        initProgressDialog();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION_CODE);
        }
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        cropView = findViewById(R.id.crop_view);
        doneButton = findViewById(R.id.done_button);

        doneButton.setEnabled(false);

        cropView.setOnPointsChangeListener(pointCount -> {
            Log.d(TAG, "Points changed: " + pointCount);
            doneButton.setEnabled(pointCount == 4);
        });
    }

    private void initProgressDialog() {
        progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(R.layout.dialog_processing)
                .setCancelable(false)
                .create();
    }

    private void handleIntent() {
        sourceUri = getIntent().getParcelableExtra("sourceUri");
        if (sourceUri == null) {
            Log.e(TAG, "No source URI provided");
            Toast.makeText(this, R.string.crop_error_no_image, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            loadImage();
        } catch (Exception e) {
            Log.e(TAG, "Error loading image", e);
            Toast.makeText(this, R.string.crop_error_loading, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadImage() throws Exception {
        try (InputStream is = getContentResolver().openInputStream(sourceUri)) {
            if (is == null) throw new Exception("Cannot open input stream");
            Log.d(TAG, "Loading image from URI: " + sourceUri.toString());

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            currentBitmap = BitmapFactory.decodeStream(is, null, options);

            if (currentBitmap == null) throw new Exception("Failed to decode image");
            cropView.setBitmap(currentBitmap);
        }
    }

    private void setupButtons() {
        MaterialButton resetButton = findViewById(R.id.reset_button);

        doneButton.setOnClickListener(view -> {
            Log.d(TAG, "Done button clicked");
            if (!cropView.isValidShape()) {
                Log.e(TAG, "Invalid shape");
                Toast.makeText(this, R.string.crop_error_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            PointF[] corners = cropView.getScaledPoints();
            if (corners == null || corners.length != 4) {
                Log.e(TAG, "Invalid corners");
                Toast.makeText(this, R.string.crop_error_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            showProcessingDialog();

            new Thread(() -> {
                try {
                    Log.d(TAG, "Processing document...");
                    // 使用 URI 而不是路径
                    DocumentProcessor.DocumentResult result =
                            DocumentProcessor.processDocument(this, sourceUri, corners);

                    if (result == null || result.processedPath == null) {
                        throw new Exception("Processing failed: null result");
                    }

                    runOnUiThread(() -> {
                        Log.d(TAG, "Processing complete");
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("imagePath", result.processedPath);
                        resultIntent.putExtra("thumbnailPath", result.thumbnailPath);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error processing document", e);
                    runOnUiThread(() -> {
                        Toast.makeText(DocumentCropActivity.this, R.string.crop_error_failed, Toast.LENGTH_SHORT).show();
                    });
                } finally {
                    runOnUiThread(() -> hideProcessingDialog());
                }
            }).start();
        });

        resetButton.setOnClickListener(view -> {
            Log.d(TAG, "Reset button clicked");
            cropView.reset();
            doneButton.setEnabled(false);
        });
    }

    private void showProcessingDialog() {
        if (!isFinishing() && progressDialog != null) {
            progressDialog.show();
        }
    }

    private void hideProcessingDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handleIntent();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        if (cropView != null) {
            cropView.setBitmap(null);
        }
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }
    }
}