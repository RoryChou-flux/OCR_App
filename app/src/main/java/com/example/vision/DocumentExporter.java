package com.example.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

            // 确保输出目录存在
            outputDir.mkdirs();

            // 写入PDF文件
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

        if (items.size() == 1) {
            File outputFile = new File(outputDir, "DOC_" + timestamp + ".png");
            copyFile(new File(items.get(0).getOriginalPath()), outputFile);
            return outputFile.getAbsolutePath();
        } else {
            File subDir = new File(outputDir, "DOC_" + timestamp);
            if (!subDir.exists() && !subDir.mkdirs()) {
                throw new IOException("Cannot create output directory");
            }

            for (int i = 0; i < items.size(); i++) {
                File outputFile = new File(subDir,
                        String.format(Locale.getDefault(), "page_%03d.png", i + 1));
                copyFile(new File(items.get(i).getOriginalPath()), outputFile);
            }

            return subDir.getAbsolutePath();
        }
    }

    public static Uri getContentUri(Context context, File file) {
        try {
            return FileProvider.getUriForFile(context,
                    context.getPackageName() + ".provider",
                    file);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to get content URI", e);
            return null;
        }
    }

    private static File getOutputDirectory(Context context, String type) throws IOException {
        File baseDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), APP_FOLDER_NAME);
        File outputDir = new File(baseDir, type);

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Cannot create output directory: " + outputDir.getPath());
        }

        return outputDir;
    }

    private static void copyFile(File source, File dest) throws IOException {
        if (!source.exists()) {
            throw new IOException("Source file does not exist: " + source.getPath());
        }

        java.nio.channels.FileChannel sourceChannel = null;
        java.nio.channels.FileChannel destChannel = null;

        try {
            sourceChannel = new java.io.FileInputStream(source).getChannel();
            destChannel = new java.io.FileOutputStream(dest).getChannel();
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        } finally {
            if (sourceChannel != null) {
                sourceChannel.close();
            }
            if (destChannel != null) {
                destChannel.close();
            }
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