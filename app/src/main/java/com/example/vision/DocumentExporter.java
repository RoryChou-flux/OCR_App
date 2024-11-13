package com.example.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DocumentExporter {
    private static final String TAG = "DocumentExporter";

    public static String exportToPdf(Context context, ArrayList<DocumentPhotoManager.PhotoItem> items) throws IOException {
        File outputDir = getOutputDirectory(context, "PDF");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File outputFile = new File(outputDir, "DOC_" + timestamp + ".pdf");

        PdfDocument document = new PdfDocument();
        try {
            // 创建PDF页面
            for (int i = 0; i < items.size(); i++) {
                Bitmap bitmap = BitmapFactory.decodeFile(items.get(i).getOriginalPath());
                if (bitmap == null) continue;

                // 创建PDF页面
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                        bitmap.getWidth(), bitmap.getHeight(), i + 1).create();
                PdfDocument.Page page = document.startPage(pageInfo);

                // 将图片绘制到页面上
                page.getCanvas().drawBitmap(bitmap, 0, 0, null);
                document.finishPage(page);

                // 释放bitmap
                bitmap.recycle();
            }

            // 写入文件
            document.writeTo(new FileOutputStream(outputFile));
            return outputFile.getAbsolutePath();

        } finally {
            document.close();
        }
    }

    public static String exportToPng(Context context, ArrayList<DocumentPhotoManager.PhotoItem> items) throws IOException {
        File outputDir = getOutputDirectory(context, "PNG");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

        if (items.size() == 1) {
            // 单张照片直接复制
            File outputFile = new File(outputDir, "DOC_" + timestamp + ".png");
            copyFile(new File(items.get(0).getOriginalPath()), outputFile);
            return outputFile.getAbsolutePath();
        } else {
            // 多张照片创建子文件夹
            File subDir = new File(outputDir, "DOC_" + timestamp);
            if (!subDir.exists() && !subDir.mkdirs()) {
                throw new IOException("无法创建目录");
            }

            // 复制所有照片
            for (int i = 0; i < items.size(); i++) {
                File outputFile = new File(subDir, String.format(Locale.getDefault(),
                        "page_%03d.png", i + 1));
                copyFile(new File(items.get(0).getOriginalPath()), outputFile);
            }

            return subDir.getAbsolutePath();
        }
    }

    private static File getOutputDirectory(Context context, String type) throws IOException {
        File baseDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "Vision");
        File outputDir = new File(baseDir, type);

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("无法创建输出目录");
        }

        return outputDir;
    }

    private static void copyFile(File source, File dest) throws IOException {
        java.nio.channels.FileChannel sourceChannel = null;
        java.nio.channels.FileChannel destChannel = null;
        try {
            sourceChannel = new java.io.FileInputStream(source).getChannel();
            destChannel = new java.io.FileOutputStream(dest).getChannel();
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        } finally {
            if (sourceChannel != null) sourceChannel.close();
            if (destChannel != null) destChannel.close();
        }
    }
}