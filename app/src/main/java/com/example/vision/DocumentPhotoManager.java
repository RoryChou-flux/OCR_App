package com.example.vision;

import android.os.Parcel;
import android.os.Parcelable;
import java.io.File;
import java.util.ArrayList;

public class DocumentPhotoManager {
    public static final String EXTRA_PHOTO_ITEMS = "photo_items";
    public static final String EXTRA_IS_CONTINUE = "is_continue";
    public static final int REQUEST_CODE_CROP = 1001;
    public static final int REQUEST_CODE_DOCUMENT = 1002;

    public static class PhotoItem implements Parcelable {
        private final String originalPath;
        private final String thumbnailPath;
        private final long timestamp;

        public PhotoItem(String originalPath, String thumbnailPath, long timestamp) {
            this.originalPath = originalPath;
            this.thumbnailPath = thumbnailPath;
            this.timestamp = timestamp;
        }

        protected PhotoItem(Parcel in) {
            originalPath = in.readString();
            thumbnailPath = in.readString();
            timestamp = in.readLong();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(originalPath);
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

        public String getOriginalPath() { return originalPath; }
        public String getThumbnailPath() { return thumbnailPath; }
        public long getTimestamp() { return timestamp; }

        public boolean exists() {
            return new File(originalPath).exists() && new File(thumbnailPath).exists();
        }

        public void delete() {
            new File(originalPath).delete();
            new File(thumbnailPath).delete();
        }
    }
}