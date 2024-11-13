package com.example.vision;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.text.SimpleDateFormat;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<HistoryItem> historyItems = new ArrayList<>();

    public static class HistoryItem {
        private final String imagePath;      // 原图路径
        private final String thumbnailPath;  // 缩略图路径
        private final String type;
        private final long timestamp;
        private final String result;

        public HistoryItem(String imagePath, String thumbnailPath, String type, long timestamp, String result) {
            this.imagePath = imagePath;
            this.thumbnailPath = thumbnailPath;
            this.type = type;
            this.timestamp = timestamp;
            this.result = result;
        }

        public String getImagePath() { return imagePath; }
        public String getThumbnailPath() { return thumbnailPath; }
        public String getType() { return type; }
        public long getTimestamp() { return timestamp; }
        public String getResult() { return result; }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnailImage;
        final TextView typeText;
        final TextView timeText;
        final TextView resultText;
        final View itemContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnailImage = itemView.findViewById(R.id.thumbnailImage);
            typeText = itemView.findViewById(R.id.typeText);
            timeText = itemView.findViewById(R.id.timeText);
            resultText = itemView.findViewById(R.id.resultText);
            itemContainer = itemView;  // 用于设置点击事件
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    public List<HistoryItem> getItems() {
        return new ArrayList<>(historyItems);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryItem item = historyItems.get(position);

        // 加载缩略图
        File thumbnailFile = new File(item.getThumbnailPath());
        Glide.with(holder.thumbnailImage)
                .load(thumbnailFile)
                .centerCrop()
                .error(android.R.drawable.ic_dialog_alert)
                .into(holder.thumbnailImage);

        // 设置类型标签
        holder.typeText.setText(item.getType());

        // 格式化并设置时间
        holder.timeText.setText(formatTime(item.getTimestamp()));

        // 设置结果文本
        holder.resultText.setText(item.getResult());

        // 设置点击事件，点击后根据类型跳转到对应的处理活动
        holder.itemContainer.setOnClickListener(v -> {
            Intent intent;
            if ("LATEX".equals(item.getType())) {
                intent = new Intent(v.getContext(), LatexActivity.class);
            } else {
                intent = new Intent(v.getContext(), DocumentActivity.class);
            }

            // 传递原图路径和其他必要信息
            intent.putExtra("imagePath", item.getImagePath());
            intent.putExtra("mode", item.getType());
            intent.putExtra("result", item.getResult());
            // 可以传递其他需要的信息

            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return historyItems.size();
    }

    public void updateItems(List<HistoryItem> newItems) {
        historyItems.clear();
        historyItems.addAll(newItems);
        notifyDataSetChanged();
    }

    private String formatTime(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return format.format(date);
    }
}