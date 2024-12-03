package com.example.vision;

import android.os.Parcel;
import android.os.Parcelable;
import java.io.File;

public class DocumentPhotoManager {
    public static final String EXTRA_IS_CONTINUE = "is_continue";

    public static class PhotoItem implements Parcelable {
        private final String originalPath;     // 原始图片路径
        private final String processedPath;    // 处理后的图片路径
        private final String thumbnailPath;    // 缩略图路径
        private final long timestamp;          // 时间戳

        // 新的四参数构造函数
        public PhotoItem(String originalPath, String processedPath, String thumbnailPath, long timestamp) {
            this.originalPath = originalPath;
            this.processedPath = processedPath;
            this.thumbnailPath = thumbnailPath;
            this.timestamp = timestamp;
        }

        // 保持向后兼容的三参数构造函数
        public PhotoItem(String originalPath, String thumbnailPath, long timestamp) {
            this(originalPath, originalPath, thumbnailPath, timestamp);
        }

        // Parcelable 实现
        protected PhotoItem(Parcel in) {
            originalPath = in.readString();
            processedPath = in.readString();
            thumbnailPath = in.readString();
            timestamp = in.readLong();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(originalPath);
            dest.writeString(processedPath);
            dest.writeString(thumbnailPath);
            dest.writeLong(timestamp);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<PhotoItem> CREATOR = new Creator<PhotoItem>() {
            @Override
            public PhotoItem createFromParcel(Parcel in) {
                return new PhotoItem(in);
            }

            @Override
            public PhotoItem[] newArray(int size) {
                return new PhotoItem[size];
            }
        };

        // Getter 方法
        public String getOriginalPath() {
            return originalPath;
        }

        public String getProcessedPath() {
            return processedPath;
        }

        public String getThumbnailPath() {
            return thumbnailPath;
        }

        public long getTimestamp() {
            return timestamp;
        }

        // 获取用于显示的路径（优先使用处理后的路径）
        public String getDisplayPath() {
            return processedPath != null ? processedPath : originalPath;
        }

        // 验证文件是否存在
        public boolean exists() {
            boolean original = new File(originalPath).exists();
            boolean processed = processedPath.equals(originalPath) || new File(processedPath).exists();
            boolean thumbnail = new File(thumbnailPath).exists();

            return original && processed && thumbnail;
        }

        // 便于调试的 toString 方法
        @Override
        public String toString() {
            return "PhotoItem{" +
                    "originalPath='" + originalPath + '\'' +
                    ", processedPath='" + processedPath + '\'' +
                    ", thumbnailPath='" + thumbnailPath + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}