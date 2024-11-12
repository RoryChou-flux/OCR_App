package com.example.vision.base;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseActivity extends AppCompatActivity {
    private ProgressDialog progressDialog;
    private static final int PERMISSION_REQUEST_CODE = 1000;
    private PermissionCallback permissionCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initProgressDialog();
    }

    private void initProgressDialog() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setMessage("处理中...");
    }

    // 加载对话框相关方法
    protected void showLoading() {
        showLoading("处理中...");
    }

    protected void showLoading(String message) {
        if (progressDialog != null) {
            progressDialog.setMessage(message);
            if (!progressDialog.isShowing()) {
                progressDialog.show();
            }
        }
    }

    protected void hideLoading() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    // Toast提示相关方法
    protected void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected void showLongToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // 对话框相关方法
    protected void showDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    protected void showConfirmDialog(String title, String message,
                                     DialogCallback callback) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", (dialog, which) -> {
                    if (callback != null) {
                        callback.onConfirm();
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    if (callback != null) {
                        callback.onCancel();
                    }
                })
                .show();
    }

    // 权限相关方法
    protected void requestPermissions(String[] permissions, PermissionCallback callback) {
        this.permissionCallback = callback;
        List<String> needRequestPermissions = new ArrayList<>();
        List<String> deniedPermissions = new ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    deniedPermissions.add(permission);
                } else {
                    needRequestPermissions.add(permission);
                }
            }
        }

        if (!deniedPermissions.isEmpty()) {
            showPermissionExplanationDialog(deniedPermissions.toArray(new String[0]));
        } else if (!needRequestPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    needRequestPermissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        } else {
            if (callback != null) {
                callback.onGranted();
            }
        }
    }

    private void showPermissionExplanationDialog(String[] permissions) {
        new AlertDialog.Builder(this)
                .setTitle("权限申请")
                .setMessage("此功能需要必要的权限才能正常使用，是否前往设置？")
                .setPositiveButton("设置", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    if (permissionCallback != null) {
                        permissionCallback.onDenied();
                    }
                })
                .show();
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
            if (allGranted && permissionCallback != null) {
                permissionCallback.onGranted();
            } else if (permissionCallback != null) {
                permissionCallback.onDenied();
            }
        }
    }

    // 界面跳转相关方法
    protected void startActivity(Class<?> cls) {
        startActivity(new Intent(this, cls));
    }

    protected void startActivityForResult(Class<?> cls, int requestCode) {
        startActivityForResult(new Intent(this, cls), requestCode);
    }

    protected void startActivityWithData(Class<?> cls, Bundle data) {
        Intent intent = new Intent(this, cls);
        if (data != null) {
            intent.putExtras(data);
        }
        startActivity(intent);
    }

    // 回调接口
    public interface PermissionCallback {
        void onGranted();
        void onDenied();
    }

    public interface DialogCallback {
        void onConfirm();
        void onCancel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
        permissionCallback = null;
    }
}