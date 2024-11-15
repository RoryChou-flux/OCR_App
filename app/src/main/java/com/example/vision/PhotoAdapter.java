package com.example.vision;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import java.io.File;
import java.util.ArrayList;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
    private static final String TAG = "PhotoAdapter";
    private final ArrayList<DocumentPhotoManager.PhotoItem> photoItems;
    private final PhotoActionListener listener;
    private final RequestOptions glideOptions;

    public PhotoAdapter(ArrayList<DocumentPhotoManager.PhotoItem> photoItems, PhotoActionListener listener) {
        this.photoItems = photoItems;
        this.listener = listener;

        this.glideOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .centerCrop()
                .error(R.drawable.ic_error_image);
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        DocumentPhotoManager.PhotoItem item = photoItems.get(position);

        try {
            File imageFile = new File(item.getOriginalPath());
            if (!imageFile.exists() || !imageFile.canRead()) {
                Toast.makeText(holder.itemView.getContext(),
                        R.string.image_load_failed_file_not_exists,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // 加载缩略图
            Glide.with(holder.itemView.getContext())
                    .load(new File(item.getThumbnailPath()))
                    .apply(glideOptions)
                    .into(holder.photoImage);

            // 设置页码
            holder.pageNumber.setText(String.format(
                    holder.itemView.getContext().getString(R.string.page_number_format),
                    photoItems.size() - position));

            // 整个项的点击事件
            final int pos = position;  // 捕获位置
            holder.photoImage.setOnClickListener(v -> {
                if (listener != null) {
                    try {
                        listener.onPhotoClick(pos);
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling photo click", e);
                    }
                }
            });

            // 删除按钮点击事件
            holder.deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(pos);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error binding view holder for position " + position, e);
            Toast.makeText(holder.itemView.getContext(),
                    "加载图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return photoItems.size();
    }

    @Override
    public long getItemId(int position) {
        return photoItems.get(position).getTimestamp();
    }

    public interface PhotoActionListener {
        void onPhotoClick(int position);
        void onDeleteClick(int position);
    }

    public static class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ImageView photoImage;
        final TextView pageNumber;
        final ImageView deleteButton;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            photoImage = itemView.findViewById(R.id.photoImage);
            pageNumber = itemView.findViewById(R.id.pageNumber);
            deleteButton = itemView.findViewById(R.id.deleteButton);

            photoImage.setClickable(true);
            photoImage.setFocusable(true);

            deleteButton.setClickable(true);
            deleteButton.setFocusable(true);
        }
    }
}