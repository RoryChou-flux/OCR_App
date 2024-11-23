package com.example.vision;
import androidx.activity.OnBackPressedCallback;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class HistoryActivity extends AppCompatActivity implements HistoryAdapter.HistoryActionListener {
    private static final String TAG = "HistoryActivity";

    // UI组件
    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private MaterialButton selectButton;
    private MaterialButton selectAllButton;
    private MaterialButton deleteSelectedButton;
    private MaterialButton cancelSelectionButton;
    private View selectionControls;
    private HistoryAdapter adapter;

    private boolean isSelectionMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // 使用新的 back handler
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode();
                } else {
                    finish();
                }
            }
        });

        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        loadHistoryData();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);
        selectButton = findViewById(R.id.selectButton);
        selectAllButton = findViewById(R.id.selectAllButton);
        deleteSelectedButton = findViewById(R.id.deleteSelectedButton);
        cancelSelectionButton = findViewById(R.id.cancelSelectionButton);
        selectionControls = findViewById(R.id.selectionControls);

        // 初始状态下隐藏选择控制栏
        selectionControls.setVisibility(View.GONE);
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new HistoryAdapter(this);
        recyclerView.setAdapter(adapter);


    }

    private void setupClickListeners() {
        // 返回按钮
        findViewById(R.id.backButton).setOnClickListener(v -> {
            if (isSelectionMode) {
                exitSelectionMode();
            } else {
                finish();
            }
        });

        // 选择按钮
        selectButton.setOnClickListener(v -> enterSelectionMode());

        // 全选按钮
        selectAllButton.setOnClickListener(v -> adapter.selectAll());

        // 删除选中项按钮
        deleteSelectedButton.setOnClickListener(v -> {
            List<HistoryAdapter.HistoryItem> selectedItems = adapter.getSelectedItems();
            if (!selectedItems.isEmpty()) {
                showDeleteConfirmDialog(selectedItems);
            }
        });

        // 取消选择按钮
        cancelSelectionButton.setOnClickListener(v -> exitSelectionMode());
    }

    private void loadHistoryData() {
        try {
            List<DocumentHistoryManager.HistoryItem> historyItems =
                    DocumentHistoryManager.getAllHistory(this);

            List<HistoryAdapter.HistoryItem> adapterItems = new ArrayList<>();
            for (DocumentHistoryManager.HistoryItem item : historyItems) {
                adapterItems.add(new HistoryAdapter.HistoryItem(
                        item.getOriginalPath(),
                        item.getProcessedPath(),
                        item.getThumbnailPath(),
                        item.getTimestamp()
                ));
            }

            adapter.updateItems(adapterItems);
            updateEmptyView(!adapterItems.isEmpty());

        } catch (Exception e) {
            Log.e(TAG, "加载历史记录失败", e);
            Toast.makeText(this, getString(R.string.load_history_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void enterSelectionMode() {
        isSelectionMode = true;
        adapter.setSelectionMode(true);
        selectButton.setVisibility(View.GONE);
        selectionControls.setVisibility(View.VISIBLE);
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        adapter.setSelectionMode(false);
        selectButton.setVisibility(View.VISIBLE);
        selectionControls.setVisibility(View.GONE);
    }

    @Override
    public void onItemClick(HistoryAdapter.HistoryItem item) {
        Intent intent = new Intent(this, CompareActivity.class);
        intent.putExtra("originalPath", item.getOriginalPath());
        intent.putExtra("processedPath", item.getProcessedPath());
        startActivity(intent);
    }

    @Override
    public void onDeleteClick(HistoryAdapter.HistoryItem item) {
        // 直接删除，不再弹窗确认
        deleteHistoryItems(Collections.singletonList(item));
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        deleteSelectedButton.setText(getString(R.string.delete_selected, selectedCount));
        deleteSelectedButton.setEnabled(selectedCount > 0);
    }

    private void showDeleteConfirmDialog(List<HistoryAdapter.HistoryItem> items) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(getString(R.string.delete_multiple_confirm_message, items.size()))
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteHistoryItems(items))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }


    private void deleteHistoryItems(List<HistoryAdapter.HistoryItem> items) {
        boolean allSuccess = true;
        int deletedCount = 0;

        for (HistoryAdapter.HistoryItem item : items) {
            try {
                // 删除文件
                boolean originalDeleted = new File(item.getOriginalPath()).delete();
                boolean processedDeleted = new File(item.getProcessedPath()).delete();
                boolean thumbnailDeleted = new File(item.getThumbnailPath()).delete();

                if (originalDeleted && processedDeleted && thumbnailDeleted) {
                    // 从历史记录管理器中删除
                    DocumentHistoryManager.removeHistory(this,
                            new DocumentHistoryManager.HistoryItem(
                                    item.getOriginalPath(),
                                    item.getProcessedPath(),
                                    item.getThumbnailPath(),
                                    item.getTimestamp()
                            )
                    );
                    deletedCount++;
                } else {
                    allSuccess = false;
                }
            } catch (Exception e) {
                Log.e(TAG, "删除文件失败", e);
                allSuccess = false;
            }
        }

        // 从适配器中移除
        adapter.removeItems(items);

        // 检查是否需要显示空视图
        updateEmptyView(adapter.getItemCount() > 0);

        // 如果在选择模式下，并且没有剩余项目，退出选择模式
        if (isSelectionMode && adapter.getItemCount() == 0) {
            exitSelectionMode();
        }

        // 显示删除结果
        if (allSuccess) {
            Toast.makeText(this, getString(R.string.delete_success, deletedCount),
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.delete_partial_success, deletedCount),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void updateEmptyView(boolean hasData) {
        emptyView.setVisibility(hasData ? View.GONE : View.VISIBLE);
        recyclerView.setVisibility(hasData ? View.VISIBLE : View.GONE);
        selectButton.setVisibility(hasData ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recyclerView.setAdapter(null);
        adapter = null;
    }
}