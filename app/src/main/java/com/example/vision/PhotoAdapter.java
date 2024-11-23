package com.example.vision;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
    private static final String TAG = "PhotoAdapter";
    private final ArrayList<DocumentPhotoManager.PhotoItem> photoItems;
    private final PhotoActionListener listener;
    private final RequestOptions glideOptions;
    private boolean isSelectionMode = false;
    private final HashSet<Integer> selectedItems = new HashSet<>();

    public interface PhotoActionListener {
        void onPhotoClick(int position);
        void onDeleteClick(int position);
        void onSelectionChanged(int selectedCount);
    }

    @Override
    public int getItemCount() {
        return photoItems.size();
    }

    @Override
    public long getItemId(int position) {
        return photoItems.get(position).getTimestamp();
    }

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
        final int itemPosition = position;
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
                    position + 1));


            holder.itemView.setOnClickListener(v -> {
                if (!isSelectionMode && listener != null) {
                    listener.onPhotoClick(itemPosition);
                }
            });
            // 设置选择框状态
            holder.selectionCheckBox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            holder.selectionCheckBox.setChecked(selectedItems.contains(position));
            holder.deleteButton.setVisibility(isSelectionMode ? View.GONE : View.VISIBLE);

            // 设置点击事件
            final int pos = position;
            holder.itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(pos);
                } else if (listener != null) {
                    listener.onPhotoClick(pos);
                }
            });

            holder.deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(pos);
                }
            });

            holder.selectionCheckBox.setOnClickListener(v -> toggleSelection(pos));

        } catch (Exception e) {
            Log.e(TAG, "Error binding view holder", e);
            Toast.makeText(holder.itemView.getContext(),
                    "加载图片失败: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void setSelectionMode(boolean selectionMode) {
        if (this.isSelectionMode != selectionMode) {
            this.isSelectionMode = selectionMode;
            if (!selectionMode) {
                selectedItems.clear();
            }
            notifyDataSetChanged();
        }
    }

    public void selectAll() {
        selectedItems.clear();
        for (int i = 0; i < photoItems.size(); i++) {
            selectedItems.add(i);
        }
        if (listener != null) {
            listener.onSelectionChanged(selectedItems.size());
        }
        notifyDataSetChanged();
    }


    private void toggleSelection(int position) {
        if (selectedItems.contains(position)) {
            selectedItems.remove(position);
        } else {
            selectedItems.add(position);
        }
        if (listener != null) {
            listener.onSelectionChanged(selectedItems.size());
        }
        notifyItemChanged(position);
    }

    public HashSet<Integer> getSelectedItems() {
        return new HashSet<>(selectedItems);
    }

    public static class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ImageView photoImage;
        final TextView pageNumber;
        final ImageView deleteButton;
        final CheckBox selectionCheckBox;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            photoImage = itemView.findViewById(R.id.photoImage);
            pageNumber = itemView.findViewById(R.id.pageNumber);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            selectionCheckBox = itemView.findViewById(R.id.selectionCheckBox);
        }
    }
}