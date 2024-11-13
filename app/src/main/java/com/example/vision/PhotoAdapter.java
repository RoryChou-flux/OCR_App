package com.example.vision;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import java.io.File;
import java.util.ArrayList;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
    private final ArrayList<DocumentPhotoManager.PhotoItem> photoItems;
    private final PhotoActionListener listener;

    public PhotoAdapter(ArrayList<DocumentPhotoManager.PhotoItem> photoItems, PhotoActionListener listener) {
        this.photoItems = photoItems;
        this.listener = listener;
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

        // 检查文件是否存在
        File imageFile = new File(item.getThumbnailPath());
        if (!imageFile.exists()) {
            Toast.makeText(holder.itemView.getContext(),
                    "图片加载失败：文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        // 使用Glide加载图片
        Glide.with(holder.itemView.getContext())
                .load(item.getThumbnailPath())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(false)
                .centerCrop()
                .error(R.drawable.ic_error_image)
                .into(holder.photoImage);

        // 设置页码
        holder.pageNumber.setText(String.valueOf(position + 1));

        // 设置点击事件
        final int pos = position; // 创建final变量用于点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPhotoClick(pos);
        });

        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(pos);
        });
    }

    @Override
    public int getItemCount() {
        return photoItems.size();
    }

    public interface PhotoActionListener {
        void onPhotoClick(int position);
        void onDeleteClick(int position);
    }

    protected static class PhotoViewHolder extends RecyclerView.ViewHolder {
        protected final ImageView photoImage;
        protected final TextView pageNumber;
        protected final ImageView deleteButton;

        protected PhotoViewHolder(View itemView) {
            super(itemView);
            photoImage = itemView.findViewById(R.id.photoImage);
            pageNumber = itemView.findViewById(R.id.pageNumber);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}