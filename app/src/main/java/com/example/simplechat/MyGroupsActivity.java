package com.example.simplechat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.simplechat.data.AppDatabase;
import com.example.simplechat.data.GroupEntity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Экран списка групп пользователя
 */
public class MyGroupsActivity extends AppCompatActivity implements GroupAdapter.OnGroupClickListener {

    private RecyclerView groupsRecyclerView;
    private GroupAdapter groupAdapter;
    private List<Group> groupList;
    private AppDatabase database;
    private ExecutorService executorService;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_groups);

        database = AppDatabase.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();

        groupsRecyclerView = findViewById(R.id.groupsRecyclerView);
        emptyText = findViewById(R.id.emptyText);
        FloatingActionButton createGroupButton = findViewById(R.id.createGroupButton);
        View backButton = findViewById(R.id.backButton);

        groupList = new ArrayList<>();
        groupAdapter = new GroupAdapter(groupList, this);

        groupsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        groupsRecyclerView.setAdapter(groupAdapter);

        // Загрузка групп
        loadGroups();

        // Кнопка создания группы
        createGroupButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, GroupActivity.class);
            startActivity(intent);
        });

        // Назад
        backButton.setOnClickListener(v -> finish());
    }

    private void loadGroups() {
        executorService.execute(() -> {
            List<GroupEntity> entities = database.groupDao().getAllGroupsSync();
            
            // Фильтруем только группы, созданные текущим пользователем (id=1)
            List<Group> groups = new ArrayList<>();
            for (GroupEntity entity : entities) {
                if (entity.getCreatedBy() == 1) {
                    groups.add(new Group(entity));
                }
            }

            handler.post(() -> {
                groupList.clear();
                groupList.addAll(groups);
                groupAdapter.notifyDataSetChanged();

                // Показываем пустой экран если нет групп
                if (groupList.isEmpty()) {
                    groupsRecyclerView.setVisibility(View.GONE);
                    emptyText.setVisibility(View.VISIBLE);
                } else {
                    groupsRecyclerView.setVisibility(View.VISIBLE);
                    emptyText.setVisibility(View.GONE);
                }
            });
        });
    }

    @Override
    public void onGroupClick(Group group) {
        // Пока просто показываем информацию о группе
        // В будущем здесь будет переход в чат группы
        new AlertDialog.Builder(this)
            .setTitle(group.getName())
            .setMessage("Группа: " + group.getName() + 
                       "\n\nУчастников: " + group.getMemberCount() +
                       "\n\nФункция группового чата в разработке")
            .setPositiveButton("OK", null)
            .show();
    }

    @Override
    public void onGroupDelete(Group group) {
        new AlertDialog.Builder(this)
            .setTitle("Удалить группу")
            .setMessage("Вы уверены, что хотите удалить группу \"" + group.getName() + "\"?\n\nЭто действие нельзя отменить.")
            .setPositiveButton("Удалить", (dialog, which) -> deleteGroup(group))
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void deleteGroup(Group group) {
        executorService.execute(() -> {
            GroupEntity entity = database.groupDao().getGroupById(group.getId());
            if (entity != null) {
                database.groupDao().delete(entity);
                
                handler.post(() -> {
                    Toast.makeText(this, "Группа удалена", Toast.LENGTH_SHORT).show();
                    loadGroups();
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGroups();
    }
}
