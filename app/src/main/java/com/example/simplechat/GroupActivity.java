package com.example.simplechat;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.simplechat.data.AppDatabase;
import com.example.simplechat.data.GroupEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Экран создания группы
 */
public class GroupActivity extends AppCompatActivity {

    private TextView groupAvatar;
    private EditText editGroupName;
    private Button createButton;
    private Button cancelButton;
    
    private AppDatabase database;
    private ExecutorService executorService;
    
    private int currentAvatarIndex = 0;
    private final String[] avatars = {"👥", "🎉", "💼", "🎮", "🎵", "🍕", "✈️", "🎬"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        database = AppDatabase.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();

        // Инициализация view
        groupAvatar = findViewById(R.id.groupAvatar);
        editGroupName = findViewById(R.id.editGroupName);
        createButton = findViewById(R.id.createButton);
        cancelButton = findViewById(R.id.cancelButton);

        // Клик по аватару для смены
        groupAvatar.setOnClickListener(v -> {
            currentAvatarIndex = (currentAvatarIndex + 1) % avatars.length;
            groupAvatar.setText(avatars[currentAvatarIndex]);
        });

        // Создание группы
        createButton.setOnClickListener(v -> createGroup());

        // Отмена
        cancelButton.setOnClickListener(v -> finish());
    }

    private void createGroup() {
        String name = editGroupName.getText().toString().trim();
        
        if (name.isEmpty()) {
            Toast.makeText(this, "Введите название группы", Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            GroupEntity group = new GroupEntity(
                name,
                avatars[currentAvatarIndex],
                1, // ID текущего пользователя
                System.currentTimeMillis(),
                1 // Пока только создатель
            );
            
            database.groupDao().insert(group);
            
            runOnUiThread(() -> {
                Toast.makeText(this, "Группа создана", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
