package com.example.vision;

import android.app.Application;
import android.util.Log;
import org.opencv.android.OpenCVLoader;

public class VisionApplication extends Application {
    private static final String TAG = "VisionApplication";
    private static boolean openCVInitialized = false;  // 添加静态标志

    @Override
    public void onCreate() {
        super.onCreate();
        initOpenCV();
    }

    private void initOpenCV() {
        try {
            if (!OpenCVLoader.initDebug()) {
                Log.e(TAG, "Unable to load OpenCV");
                openCVInitialized = false;
            } else {
                Log.d(TAG, "OpenCV loaded successfully");
                openCVInitialized = true;
            }
        } catch (Error | Exception e) {
            Log.e(TAG, "OpenCV initialization failed: " + e.getMessage(), e);
            openCVInitialized = false;
        }
    }

    // 添加静态方法检查初始化状态
}