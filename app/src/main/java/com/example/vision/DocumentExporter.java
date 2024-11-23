package com.example.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.util.Log;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class DocumentExporter {
    private static final String TAG = "DocumentExporter";
    private static final int MAX_BITMAP_SIZE = 2048;
    private static final String APP_FOLDER_NAME = "Vision";

    public static String exportToPdf(Context context,
                                     ArrayList<DocumentPhotoManager.PhotoItem> items) throws IOException {
        if (items.isEmpty()) {
            throw new IOException("No items to export");
        }

        File outputDir = getOutputDirectory(context, "PDF");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        File outputFile = new File(outputDir, "DOC_" + timestamp + ".pdf");

        PdfDocument document = new PdfDocument();
        try {
            for (int i = 0; i < items.size(); i++) {
                Bitmap bitmap = loadAndResizeBitmap(items.get(i).getOriginalPath());
                if (bitmap == null) continue;

                try {
                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                            bitmap.getWidth(), bitmap.getHeight(), i + 1).create();
                    PdfDocument.Page page = document.startPage(pageInfo);
                    page.getCanvas().drawBitmap(bitmap, 0, 0, null);
                    document.finishPage(page);
                } finally {
                    bitmap.recycle();
                }
            }

            outputDir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                document.writeTo(out);
            }

            return outputFile.getAbsolutePath();
        } finally {
            document.close();
        }
    }

    public static String exportToPng(Context context,
                                     ArrayList<DocumentPhotoManager.PhotoItem> items) throws IOException {
        if (items.isEmpty()) {
            throw new IOException("No items to export");
        }

        File outputDir = getOutputDirectory(context, "PNG");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        File exportDir = new File(outputDir, "DOC_" + timestamp);

        // 确保导出目录存在并可写
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw new IOException("Cannot create output directory: " + exportDir.getPath());
        }

        int successCount = 0;
        ArrayList<File> exportedFiles = new ArrayList<>();

        // 导出所有选中的图片
        for (int i = 0; i < items.size(); i++) {
            try {
                DocumentPhotoManager.PhotoItem item = items.get(i);
                File sourceFile = new File(item.getOriginalPath());
                String fileName = String.format(Locale.getDefault(), "page_%03d.png", i + 1);
                File outputFile = new File(exportDir, fileName);

                // 验证源文件
                if (!sourceFile.exists() || !sourceFile.canRead()) {
                    Log.e(TAG, "Source file not accessible: " + sourceFile.getPath());
                    continue;
                }

                // 复制文件
                copyFile(sourceFile, outputFile);
                exportedFiles.add(outputFile);
                successCount++;

                // 确保文件被正确写入并可读
                if (!outputFile.exists() || !outputFile.canRead()) {
                    Log.e(TAG, "Failed to verify exported file: " + outputFile.getPath());
                    continue;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to export file at index " + i, e);
            }
        }

        if (successCount == 0) {
            throw new IOException("No files were successfully exported");
        }

        // 返回的结果包含目录路径和成功导出的文件数
        return String.format("%s:%d", exportDir.getAbsolutePath(), successCount);
    }


    private static File getOutputDirectory(Context context, String type) throws IOException {
        // 使用应用私有目录而不是公共目录
        File baseDir = new File(context.getExternalFilesDir(null), APP_FOLDER_NAME);
        File outputDir = new File(baseDir, type);

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Cannot create output directory: " + outputDir.getPath());
        }

        return outputDir;
    }

    private static void copyFile(File source, File dest) throws IOException {
        try (FileInputStream inStream = new FileInputStream(source);
             FileOutputStream outStream = new FileOutputStream(dest)) {

            byte[] buffer = new byte[8192];
            int length;
            while ((length = inStream.read(buffer)) > 0) {
                outStream.write(buffer, 0, length);
            }
            outStream.flush();
        }
    }

    private static Bitmap loadAndResizeBitmap(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        int scale = 1;
        while (options.outWidth / scale > MAX_BITMAP_SIZE ||
                options.outHeight / scale > MAX_BITMAP_SIZE) {
            scale *= 2;
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = scale;

        return BitmapFactory.decodeFile(path, options);
    }
}
