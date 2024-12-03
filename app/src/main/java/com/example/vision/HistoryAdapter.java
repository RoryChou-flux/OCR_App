package com.example.vision;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import android.content.Context;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
    private final List<HistoryItem> historyItems = new ArrayList<>();
    private boolean isSelectionMode = false;
    private final HashSet<Integer> selectedItems = new HashSet<>();
    private final HistoryActionListener listener;
    private SwipeLayout lastOpenedLayout;

    public interface HistoryActionListener {
        void onItemClick(HistoryItem item);
        void onDeleteClick(HistoryItem item);
        void onSelectionChanged(int selectedCount);
    }

    public static class HistoryViewHolder extends RecyclerView.ViewHolder {
        final View contentView;
        final ImageView thumbnailImage;
        final TextView timeText;
        final CheckBox selectionCheckBox;
        final ImageButton deleteButton;
        final SwipeLayout swipeLayout;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            contentView = itemView.findViewById(R.id.contentCard);
            thumbnailImage = itemView.findViewById(R.id.thumbnailImage);
            timeText = itemView.findViewById(R.id.timeText);
            selectionCheckBox = itemView.findViewById(R.id.selectionCheckBox);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            swipeLayout = (SwipeLayout) itemView;
        }
    }

    public static class HistoryItem {
        private final String originalPath;
        private final String processedPath;
        private final String thumbnailPath;
        private final long timestamp;

        public HistoryItem(String originalPath, String processedPath, String thumbnailPath, long timestamp) {
            this.originalPath = originalPath;
            this.processedPath = processedPath;
            this.thumbnailPath = thumbnailPath;
            this.timestamp = timestamp;
        }

        public String getOriginalPath() { return originalPath; }
        public String getProcessedPath() { return processedPath; }
        public String getThumbnailPath() { return thumbnailPath; }
        public long getTimestamp() { return timestamp; }
    }

    public HistoryAdapter(HistoryActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        HistoryItem item = historyItems.get(position);

        // 加载原始图片（处理前）
        Glide.with(holder.thumbnailImage)
                .load(new File(item.getOriginalPath()))
                .centerCrop()
                .error(android.R.drawable.ic_dialog_alert)
                .into(holder.thumbnailImage);

        // 当用户点击图片时，新增处理前后对比的逻辑
        holder.thumbnailImage.setOnClickListener(v -> {
            if (!isSelectionMode && listener != null && !holder.swipeLayout.isOpen()) {
                // 打开对比活动，传递两个图片的路径
                Intent intent = new Intent(v.getContext(), CompareActivity.class);
                intent.putExtra("originalPath", item.getOriginalPath());     // 原始图片路径
                intent.putExtra("processedPath", item.getProcessedPath());   // 处理后图片路径
                v.getContext().startActivity(intent);
            }
        });

        // 设置时间戳
        holder.timeText.setText(formatTime(item.getTimestamp()));

        // 设置选择框状态
        holder.selectionCheckBox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        holder.selectionCheckBox.setChecked(selectedItems.contains(position));

        // 重置swipe状态
        holder.swipeLayout.close();

        // 设置滑动状态监听
        holder.swipeLayout.setOnSwipeListener(isOpen -> {
            if (isOpen && lastOpenedLayout != null && lastOpenedLayout != holder.swipeLayout) {
                lastOpenedLayout.close();
            }
            if (isOpen) {
                lastOpenedLayout = holder.swipeLayout;
            }
        });

        // 设置删除按钮点击事件
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(item);
            }
            holder.swipeLayout.close();
        });

        // 设置内容点击事件
        holder.contentView.setOnClickListener(v -> {
            if (holder.swipeLayout.isOpen()) {
                holder.swipeLayout.close();
            } else if (isSelectionMode) {
                toggleSelection(position);
            } else if (listener != null) {
                // 修改这里，点击整个内容区域也打开对比界面
                Intent intent = new Intent(v.getContext(), CompareActivity.class);
                intent.putExtra("originalPath", item.getOriginalPath());     // 原始图片路径
                intent.putExtra("processedPath", item.getProcessedPath());   // 处理后图片路径
                v.getContext().startActivity(intent);
            }
        });
    }



    @Override
    public int getItemCount() {
        return historyItems.size();
    }

    public void setSelectionMode(boolean selectionMode) {
        if (this.isSelectionMode != selectionMode) {
            this.isSelectionMode = selectionMode;
            if (!selectionMode) {
                selectedItems.clear();
                if (listener != null) {
                    listener.onSelectionChanged(0);
                }
            }
            notifyItemRangeChanged(0, historyItems.size());
        }
    }

    public void selectAll() {
        selectedItems.clear();
        for (int i = 0; i < historyItems.size(); i++) {
            selectedItems.add(i);
        }
        if (listener != null) {
            listener.onSelectionChanged(selectedItems.size());
        }
        notifyItemRangeChanged(0, historyItems.size());
    }

    public void toggleSelection(int position) {
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

    public List<HistoryItem> getSelectedItems() {
        List<HistoryItem> items = new ArrayList<>();
        for (Integer position : selectedItems) {
            items.add(historyItems.get(position));
        }
        return items;
    }

    public void updateItems(List<HistoryItem> newItems) {
        historyItems.clear();
        historyItems.addAll(newItems);
        selectedItems.clear();
        notifyDataSetChanged();
    }

    public void removeItem(HistoryItem item) {
        if (lastOpenedLayout != null) {
            lastOpenedLayout.close();
            lastOpenedLayout = null;
        }
        int position = historyItems.indexOf(item);
        if (position != -1) {
            historyItems.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, historyItems.size() - position);
        }
    }

    public void removeItems(List<HistoryItem> items) {
        for (HistoryItem item : items) {
            removeItem(item);
        }
        selectedItems.clear();
    }


    private String formatTime(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return format.format(date);
    }

}