package com.example.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.opencv.core.Core;

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

    public static String createThumbnail(Context context, String originalPath, String outputPath)
            throws IOException {
        Log.d(TAG, "Creating thumbnail for: " + originalPath);

        // 验证输入文件
        File inputFile = new File(originalPath);
        if (!inputFile.exists() || !inputFile.canRead()) {
            throw new IOException("Cannot access source file: " + originalPath);
        }

        try {
            // 加载原始图片
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(originalPath, options);

            // 计算采样率
            int sampleSize = 1;
            int width = options.outWidth;
            int height = options.outHeight;
            while (width > THUMBNAIL_SIZE * 2 || height > THUMBNAIL_SIZE * 2) {
                width /= 2;
                height /= 2;
                sampleSize *= 2;
            }

            // 加载缩小的图片
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;
            Bitmap originalBitmap = BitmapFactory.decodeFile(originalPath, options);
            if (originalBitmap == null) {
                throw new IOException("Failed to decode image");
            }

            // 创建缩略图
            try {
                Bitmap thumbnailBitmap = createThumbnail(originalBitmap);
                saveImage(thumbnailBitmap, new File(outputPath), 80);
                thumbnailBitmap.recycle();
                Log.d(TAG, "Thumbnail created successfully at: " + outputPath);
                return outputPath;
            } finally {
                originalBitmap.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating thumbnail", e);
            throw new IOException("Failed to create thumbnail: " + e.getMessage());
        }
    }
    public static DocumentResult processDocument(Context context, String imagePath) throws IOException {
        Log.d(TAG, "Starting document processing: " + imagePath);

        File outputDir = new File(context.getFilesDir(), "processed_documents");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Cannot create output directory");
        }

        Mat originalMat = null;
        Mat grayMat = null;
        Mat processedMat = null;
        Bitmap resultBitmap = null;

        try {
            // 加载图片
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;

            // 检查尺寸
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                throw new IOException("Invalid image dimensions");
            }

            // 计算采样率
            int sampleSize = calculateInSampleSize(options, 2048, 2048);
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;

            Bitmap originalBitmap = BitmapFactory.decodeFile(imagePath, options);
            if (originalBitmap == null) {
                throw new IOException("Failed to decode image");
            }

            Log.d(TAG, "Original bitmap size: " + originalBitmap.getWidth() + "x" + originalBitmap.getHeight());

            // 转换为 OpenCV Mat
            originalMat = new Mat();
            Utils.bitmapToMat(originalBitmap, originalMat);
            originalBitmap.recycle();

            // 转换为灰度
            grayMat = new Mat();
            Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

            // 增强对比度
            processedMat = new Mat();
            Core.normalize(grayMat, processedMat, 0, 255, Core.NORM_MINMAX);

            // 自适应阈值处理
            Mat binaryMat = new Mat();
            Imgproc.adaptiveThreshold(
                    processedMat,
                    binaryMat,
                    255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    11,
                    2
            );

            // 转回 RGBA
            processedMat = new Mat();
            Imgproc.cvtColor(binaryMat, processedMat, Imgproc.COLOR_GRAY2RGBA);
            binaryMat.release();

            // 创建结果图片
            resultBitmap = Bitmap.createBitmap(
                    processedMat.cols(),
                    processedMat.rows(),
                    Bitmap.Config.ARGB_8888
            );
            Utils.matToBitmap(processedMat, resultBitmap);

            // 保存处理后的图片
            String timestamp = String.valueOf(System.currentTimeMillis());
            File processedFile = new File(outputDir, "PROC" + timestamp + ".jpg");
            File thumbnailFile = new File(outputDir, "THUMB" + timestamp + ".jpg");

            Log.d(TAG, "Saving processed image");
            saveImage(resultBitmap, processedFile, 95);

            Log.d(TAG, "Creating and saving thumbnail");
            Bitmap thumbnailBitmap = createThumbnail(resultBitmap);
            saveImage(thumbnailBitmap, thumbnailFile, 80);
            thumbnailBitmap.recycle();

            return new DocumentResult(processedFile.getAbsolutePath(), thumbnailFile.getAbsolutePath());

        } finally {
            if (originalMat != null) originalMat.release();
            if (grayMat != null) grayMat.release();
            if (processedMat != null) processedMat.release();
            if (resultBitmap != null) resultBitmap.recycle();
        }
    }

    private static Bitmap createThumbnail(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();

        float ratio = Math.min(
                (float) THUMBNAIL_SIZE / width,
                (float) THUMBNAIL_SIZE / height
        );

        int thumbnailWidth = Math.round(width * ratio);
        int thumbnailHeight = Math.round(height * ratio);

        return Bitmap.createScaledBitmap(
                original, thumbnailWidth, thumbnailHeight, true);
    }

    private static void saveImage(Bitmap bitmap, File file, int quality)
            throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            out.flush();
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

}