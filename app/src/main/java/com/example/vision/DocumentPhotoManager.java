package com.example.vision;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;

public class DocumentPhotoManager {
    public static class PhotoItem implements Parcelable {
        private String originalPath;
        private String thumbnailPath;
        private long timestamp;

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
            dest.writeString(originalPath);
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

        // Getters
        public String getOriginalPath() { return originalPath; }
        public String getThumbnailPath() { return thumbnailPath; }
        public long getTimestamp() { return timestamp; }
    }

    public static final String EXTRA_PHOTO_ITEMS = "photo_items";
    public static final String EXTRA_IS_CONTINUE = "is_continue";
    public static final int REQUEST_CODE_CROP = 1001;
    public static final int REQUEST_CODE_DOCUMENT = 1002;
}