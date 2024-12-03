package com.example.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DocumentProcessor {
    private static final String TAG = "DocumentProcessor";
    private static final int THUMBNAIL_SIZE = 512;

    public static class DocumentResult {
        public final String processedPath;
        public final String thumbnailPath;

        public DocumentResult(String processedPath, String thumbnailPath) {
            this.processedPath = processedPath;
            this.thumbnailPath = thumbnailPath;
        }
    }

    public static DocumentResult processDocument(@NonNull Context context, @NonNull Uri sourceUri, @NonNull PointF[] corners) throws IOException {
        File processedDir = new File(context.getFilesDir(), "documents");
        File thumbnailDir = new File(context.getFilesDir(), "thumbnails");

        if (!processedDir.exists() && !processedDir.mkdirs()) {
            throw new IOException("Cannot create processed directory");
        }
        if (!thumbnailDir.exists() && !thumbnailDir.mkdirs()) {
            throw new IOException("Cannot create thumbnail directory");
        }

        Mat originalMat = null;
        Mat transformedMat = null;
        Bitmap resultBitmap = null;

        try {
            // 使用 ContentResolver 加载图片
            Bitmap originalBitmap;
            try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri)) {
                if (inputStream == null) {
                    throw new IOException("Failed to open input stream");
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                originalBitmap = BitmapFactory.decodeStream(inputStream, null, options);
            }

            if (originalBitmap == null) {
                throw new IOException("Failed to decode image");
            }

            // 转换为OpenCV Mat
            originalMat = new Mat();
            Utils.bitmapToMat(originalBitmap, originalMat);
            originalBitmap.recycle();

            // 执行透视变换
            transformedMat = performPerspectiveTransform(originalMat, corners);

            // 执行文档增强
            Mat enhancedMat = enhanceDocument(transformedMat);

            // 转换回Bitmap
            resultBitmap = Bitmap.createBitmap(
                    enhancedMat.cols(),
                    enhancedMat.rows(),
                    Bitmap.Config.ARGB_8888
            );
            Utils.matToBitmap(enhancedMat, resultBitmap);
            enhancedMat.release();

            // 保存处理后的图片
            String timestamp = String.valueOf(System.currentTimeMillis());
            File processedFile = new File(processedDir, "SCAN_" + timestamp + ".jpg");
            File thumbnailFile = new File(thumbnailDir, "THUMB_" + timestamp + ".jpg");

            // 保存处理后的图片
            saveImage(resultBitmap, processedFile, 95);

            // 创建并保存缩略图
            Bitmap thumbnailBitmap = createThumbnail(resultBitmap);
            saveImage(thumbnailBitmap, thumbnailFile, 80);
            thumbnailBitmap.recycle();

            return new DocumentResult(processedFile.getAbsolutePath(), thumbnailFile.getAbsolutePath());

        } finally {
            if (originalMat != null) originalMat.release();
            if (transformedMat != null) transformedMat.release();
            if (resultBitmap != null) resultBitmap.recycle();
        }
    }

    public static String processThumbnail(@NonNull Context context, @NonNull String originalPath, @NonNull String thumbnailPath) throws IOException {
        // 加载原始图片
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap original = BitmapFactory.decodeFile(originalPath, options);
        if (original == null) {
            throw new IOException("Failed to decode image");
        }

        try {
            // 创建缩略图
            Bitmap thumbnail = createThumbnail(original);
            // 保存缩略图
            saveImage(thumbnail, new File(thumbnailPath), 80);
            thumbnail.recycle();
            return thumbnailPath;
        } finally {
            original.recycle();
        }
    }

    private static Mat performPerspectiveTransform(@NonNull Mat source, @NonNull PointF[] corners) {
        // corners的顺序是：左上、左下、右下、右上
        Point[] sourcePoints = new Point[4];
        sourcePoints[0] = new Point(corners[0].x, corners[0].y);  // 左上
        sourcePoints[1] = new Point(corners[1].x, corners[1].y);  // 左下
        sourcePoints[2] = new Point(corners[2].x, corners[2].y);  // 右下
        sourcePoints[3] = new Point(corners[3].x, corners[3].y);  // 右上
        MatOfPoint2f sourceMat = new MatOfPoint2f(sourcePoints);

        // 计算目标矩形的尺寸
        double width = Math.max(
                calculateDistance(sourcePoints[0].x, sourcePoints[0].y,
                        sourcePoints[3].x, sourcePoints[3].y),  // 左上到右上的距离
                calculateDistance(sourcePoints[1].x, sourcePoints[1].y,
                        sourcePoints[2].x, sourcePoints[2].y)   // 左下到右下的距离
        );
        double height = Math.max(
                calculateDistance(sourcePoints[0].x, sourcePoints[0].y,
                        sourcePoints[1].x, sourcePoints[1].y),  // 左上到左下的距离
                calculateDistance(sourcePoints[3].x, sourcePoints[3].y,
                        sourcePoints[2].x, sourcePoints[2].y)   // 右上到右下的距离
        );

        Point[] destinationPoints = {
                new Point(0, 0),           // 左上
                new Point(0, height),      // 左下
                new Point(width, height),  // 右下
                new Point(width, 0)        // 右上
        };
        MatOfPoint2f destinationMat = new MatOfPoint2f(destinationPoints);

        Mat perspectiveTransform = Imgproc.getPerspectiveTransform(sourceMat, destinationMat);
        Mat result = new Mat();
        Imgproc.warpPerspective(source, result, perspectiveTransform, new Size(width, height));

        perspectiveTransform.release();
        sourceMat.release();
        destinationMat.release();

        return result;
    }


    private static Mat enhanceDocument(@NonNull Mat source) {
        Mat result = source.clone();
        Log.d(TAG, "Starting document enhancement");

        try {
            // 首先确保颜色空间正确
            Imgproc.cvtColor(result, result, Imgproc.COLOR_RGBA2RGB);

            // 1. 分析图像整体亮度分布
            Mat grayForAnalysis = new Mat();
            Imgproc.cvtColor(result, grayForAnalysis, Imgproc.COLOR_RGB2GRAY);
            MatOfDouble meanMat = new MatOfDouble();
            MatOfDouble stdMat = new MatOfDouble();
            Core.meanStdDev(grayForAnalysis, meanMat, stdMat);
            double initialMean = meanMat.get(0, 0)[0];
            double initialStd = stdMat.get(0, 0)[0];
            Log.d(TAG, "Initial image statistics - Mean: " + initialMean + ", Std: " + initialStd);

            // 2. 计算局部均值
            Mat localMean = new Mat();
            Imgproc.boxFilter(grayForAnalysis, localMean, -1, new Size(50, 50)); // 使用更大的窗口

            // 3. 创建并应用自适应增强
            Mat denoised = new Mat();
            // 调整 bilateral filter 参数基于整体亮度
            int sigmaColor = initialMean > 200 ? 40 : 60;
            Imgproc.bilateralFilter(result, denoised, 9, sigmaColor, 60);
            Log.d(TAG, "Bilateral filter applied with sigmaColor: " + sigmaColor);

            // 4. 转换到LAB色彩空间并增强
            Imgproc.cvtColor(denoised, denoised, Imgproc.COLOR_RGB2Lab);
            List<Mat> channels = new ArrayList<>();
            Core.split(denoised, channels);

            // 处理L通道
            Mat l = channels.get(0);
            Imgproc.GaussianBlur(l, l, new Size(3, 3), 0);

            // 根据局部亮度调整CLAHE参数
            double claheClipLimit = initialMean > 200 ? 2 :
                    initialMean > 150 ? 2.2 : 2.5;
            Log.d(TAG, "CLAHE clip limit: " + claheClipLimit);
            Imgproc.createCLAHE(claheClipLimit, new Size(8, 8)).apply(l, l);

            // 自适应亮度调整
            Core.meanStdDev(l, meanMat, stdMat);
            double lMean = meanMat.get(0, 0)[0];
            double brightnessFactor = initialMean > 200 ? 1.1 :
                    initialMean < 100 ? 1.4 : 1.3;
            double brightnessOffset = 128 - lMean * (initialMean > 180 ? 0.5 : 0.7);
            Log.d(TAG, "Brightness adjustment - Factor: " + brightnessFactor + ", Offset: " + brightnessOffset);
            Core.convertScaleAbs(l, l, brightnessFactor, brightnessOffset);

            // 合并通道
            Core.merge(channels, denoised);
            Imgproc.cvtColor(denoised, denoised, Imgproc.COLOR_Lab2RGB);

            // 5. 自适应锐化
            Mat blurred = new Mat();
            double sigma = initialStd < 30 ? 2.5 : 2.0; // 低对比度图像使用更大的sigma
            Imgproc.GaussianBlur(denoised, blurred, new Size(0, 0), sigma);
            double sharpAmount = initialMean > 200 ? 1.4 :
                    initialMean < 100 ? 1.9 : 1.7;
            Log.d(TAG, "Sharpening - Amount: " + sharpAmount + ", Sigma: " + sigma);
            Core.addWeighted(denoised, sharpAmount, blurred, -(sharpAmount-1), 0, result);

            // 6. 最终亮度调整
            double finalAlpha = initialMean > 180 ? 1.1 :
                    initialMean < 100 ? 1.3 : 1.2;
            double finalBeta = initialMean > 180 ? -10 :
                    initialMean < 100 ? 10 : 0;
            Log.d(TAG, "Final adjustment - Alpha: " + finalAlpha + ", Beta: " + finalBeta);
            Core.convertScaleAbs(result, result, finalAlpha, finalBeta);

            // 转换回RGBA
            Imgproc.cvtColor(result, result, Imgproc.COLOR_RGB2RGBA);
            Log.d(TAG, "Enhancement completed successfully");

            // 释放资源
            grayForAnalysis.release();
            localMean.release();
            meanMat.release();
            stdMat.release();
            blurred.release();
            denoised.release();
            for (Mat channel : channels) {
                channel.release();
            }

            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error in enhanceDocument: " + e.getMessage(), e);
            return source.clone();
        }
    }

    private static double calculateDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    private static Bitmap createThumbnail(@NonNull Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        float ratio = Math.min((float) THUMBNAIL_SIZE / width, (float) THUMBNAIL_SIZE / height);
        int thumbnailWidth = Math.round(width * ratio);
        int thumbnailHeight = Math.round(height * ratio);
        return Bitmap.createScaledBitmap(original, thumbnailWidth, thumbnailHeight, true);
    }

    private static void saveImage(@NonNull Bitmap bitmap, @NonNull File file, int quality) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            out.flush();
        }
    }
}