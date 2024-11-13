package com.example.vision.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageProcessorUtils {

    public static class ProcessedImage {
        public final String originalPath;
        public final String thumbnailPath;

        public ProcessedImage(String originalPath, String thumbnailPath) {
            this.originalPath = originalPath;
            this.thumbnailPath = thumbnailPath;
        }
    }

    // 生成缩略图并保存
    public static ProcessedImage processAndSaveThumbnail(Context context, Uri sourceUri) throws IOException {
        // 创建缩略图存储目录
        File thumbnailDir = new File(context.getFilesDir(), "thumbnails");
        if (!thumbnailDir.exists()) {
            thumbnailDir.mkdirs();
        }

        // 生成唯一文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String originalFileName = "IMG_" + timeStamp + ".jpg";
        String thumbnailFileName = "THUMB_" + timeStamp + ".jpg";

        // 保存原图
        File originalFile = new File(context.getFilesDir(), originalFileName);
        FileOutputStream originalFos = new FileOutputStream(originalFile);

        // 保存缩略图
        File thumbnailFile = new File(thumbnailDir, thumbnailFileName);
        FileOutputStream thumbnailFos = new FileOutputStream(thumbnailFile);

        try {
            // 从Uri读取图片
            Bitmap originalBitmap = BitmapFactory.decodeStream(
                    context.getContentResolver().openInputStream(sourceUri));

            // 保存原图
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, originalFos);

            // 创建缩略图
            int thumbnailSize = 200; // 设置缩略图大小
            float ratio = Math.min(
                    (float) thumbnailSize / originalBitmap.getWidth(),
                    (float) thumbnailSize / originalBitmap.getHeight());
            int dstWidth = Math.round(originalBitmap.getWidth() * ratio);
            int dstHeight = Math.round(originalBitmap.getHeight() * ratio);

            Bitmap thumbnailBitmap = Bitmap.createScaledBitmap(
                    originalBitmap, dstWidth, dstHeight, true);

            // 保存缩略图
            thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 80, thumbnailFos);

            // 清理
            if (!originalBitmap.isRecycled()) {
                originalBitmap.recycle();
            }
            if (!thumbnailBitmap.isRecycled()) {
                thumbnailBitmap.recycle();
            }

            return new ProcessedImage(
                    originalFile.getAbsolutePath(),
                    thumbnailFile.getAbsolutePath()
            );

        } finally {
            thumbnailFos.close();
            originalFos.close();
        }
    }

    // 删除图片及其缩略图
    public static void deleteImages(String originalPath, String thumbnailPath) {
        if (originalPath != null) {
            new File(originalPath).delete();
        }
        if (thumbnailPath != null) {
            new File(thumbnailPath).delete();
        }
    }
}