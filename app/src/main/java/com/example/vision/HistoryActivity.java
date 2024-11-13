package com.example.vision;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import com.example.vision.utils.ImageProcessorUtils;

public class HistoryActivity extends AppCompatActivity {

    private static final String TAG = "HistoryActivity";
    private HistoryAdapter adapter;
    private RecyclerView recyclerView;
    private LinearLayout emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        initializeViews();
        loadHistoryData();
    }

    private void initializeViews() {
        // 初始化 RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        if (recyclerView != null) {
            LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            recyclerView.setLayoutManager(layoutManager);

            // 设置分割线 - layoutManager 在这里一定不为空，不需要检查
            DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(
                    this,
                    layoutManager.getOrientation()
            );
            recyclerView.addItemDecoration(dividerItemDecoration);
        }

        // 初始化适配器
        adapter = new HistoryAdapter();
        if (recyclerView != null) {
            recyclerView.setAdapter(adapter);
        }

        // 初始化空视图
        emptyView = findViewById(R.id.emptyView);

        // 处理返回按钮点击
        MaterialButton backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // 处理清空按钮点击
        MaterialButton clearButton = findViewById(R.id.clearButton);
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> showClearConfirmDialog());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistoryData(); // 每次返回界面时重新加载数据
    }

    private void loadHistoryData() {
        try {
            // TODO: 从本地数据库加载数据
            List<HistoryAdapter.HistoryItem> historyItems = new ArrayList<>();

            // 示例数据，使用正确的文件路径
            File imagesDir = new File(getFilesDir(), "images");
            File thumbnailsDir = new File(getFilesDir(), "thumbnails");

            historyItems.add(new HistoryAdapter.HistoryItem(
                    new File(imagesDir, "original_1.jpg").getAbsolutePath(),
                    new File(thumbnailsDir, "thumb_1.jpg").getAbsolutePath(),
                    "LATEX",
                    System.currentTimeMillis(),
                    "LaTeX识别结果示例"
            ));

            if (adapter != null) {
                adapter.updateItems(historyItems);
                updateEmptyView(!historyItems.isEmpty());
            }

        } catch (Exception e) {
            Log.e(TAG, "加载历史记录失败", e);
        }
    }

    private void updateEmptyView(boolean hasData) {
        if (emptyView != null && recyclerView != null) {
            emptyView.setVisibility(hasData ? View.GONE : View.VISIBLE);
            recyclerView.setVisibility(hasData ? View.VISIBLE : View.GONE);
        }
    }

    private void showClearConfirmDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_clear_history)           // 更新为正确的资源ID
                .setMessage(R.string.message_clear_history)       // 更新为正确的资源ID
                .setNegativeButton(R.string.button_cancel, null)  // 更新为正确的资源ID
                .setPositiveButton(R.string.button_confirm,       // 更新为正确的资源ID
                        (dialog, which) -> clearHistory())
                .show();
    }

    private void clearHistory() {
        try {
            if (adapter != null) {
                // 使用局部变量存储当前项目的副本
                @NonNull List<HistoryAdapter.HistoryItem> itemsToDelete = new ArrayList<>(adapter.getItems());

                // 清除所有相关文件
                for (HistoryAdapter.HistoryItem item : itemsToDelete) {
                    ImageProcessorUtils.deleteImages(item.getImagePath(), item.getThumbnailPath());
                }

                // 清空适配器数据
                adapter.updateItems(new ArrayList<>());
                updateEmptyView(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "清空历史记录失败", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recyclerView = null;
        adapter = null;
        emptyView = null;
    }
}