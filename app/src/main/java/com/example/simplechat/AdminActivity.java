package com.example.simplechat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.simplechat.data.AppDatabase;
import com.example.simplechat.data.MessageEntity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Админ-панель со статистикой приложения
 * Вход: 5 кликов по версии приложения в настройках
 */
public class AdminActivity extends AppCompatActivity {

    private TextView usersCountText;
    private TextView groupsCountText;
    private TextView chatsCountText;
    private TextView messagesCountText;
    private TextView supportMessagesCountText;
    private TextView versionText;
    private Button logoutButton;
    private ImageButton backButton;

    private AppDatabase database;
    private ExecutorService executorService;
    private SharedPreferences adminPrefs;

    private static final String ADMIN_PREFS = "admin_prefs";
    public static final String ADMIN_PIN = "admin123"; // Пароль админа

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        database = AppDatabase.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();
        adminPrefs = getSharedPreferences(ADMIN_PREFS, MODE_PRIVATE);

        // Инициализация view
        usersCountText = findViewById(R.id.usersCountText);
        groupsCountText = findViewById(R.id.groupsCountText);
        chatsCountText = findViewById(R.id.chatsCountText);
        messagesCountText = findViewById(R.id.messagesCountText);
        supportMessagesCountText = findViewById(R.id.supportMessagesCountText);
        versionText = findViewById(R.id.versionText);
        logoutButton = findViewById(R.id.logoutButton);
        backButton = findViewById(R.id.backButton);

        // Установка версии приложения
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            versionText.setText("Версия: " + versionName);
        } catch (Exception e) {
            versionText.setText("Версия: 1.0");
        }

        // Загрузка статистики
        loadStatistics();

        // Кнопка назад
        backButton.setOnClickListener(v -> finish());

        // Кнопка выхода
        logoutButton.setOnClickListener(v -> {
            adminPrefs.edit().putBoolean("is_admin", false).apply();
            Toast.makeText(this, "Вы вышли из админ-панели", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void loadStatistics() {
        executorService.execute(() -> {
            // Считаем пользователей (профили)
            int usersCount = 1; // Всегда 1 пользователь на устройстве

            // Считаем группы
            List groups = database.groupDao().getAllGroupsSync();
            int groupsCount = groups.size();

            // Считаем чаты
            List chats = database.chatDao().getAllChatsSync();
            int chatsCount = chats.size();

            // Считаем все сообщения
            List messages = database.messageDao().getAllMessagesSync();
            int messagesCount = messages.size();

            // Считаем сообщения поддержки (чат с id=9999)
            List supportMessages = database.messageDao().getMessagesForChatSync(9999L);
            int supportMessagesCount = supportMessages.size();

            runOnUiThread(() -> {
                usersCountText.setText(String.valueOf(usersCount));
                groupsCountText.setText(String.valueOf(groupsCount));
                chatsCountText.setText(String.valueOf(chatsCount));
                messagesCountText.setText(String.valueOf(messagesCount));
                supportMessagesCountText.setText(String.valueOf(supportMessagesCount));
            });
        });
    }

    /**
     * Проверка является ли текущий пользователь админом
     */
    public static boolean isAdmin(SharedPreferences prefs) {
        return prefs.getBoolean("is_admin", false);
    }

    /**
     * Показать диалог входа для админа
     */
    public static void showAdminLoginDialog(AppCompatActivity activity, SharedPreferences prefs) {
        TextInputLayout inputLayout = new TextInputLayout(activity);
        TextInputEditText input = new TextInputEditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Введите пароль");
        inputLayout.addView(input);
        inputLayout.setPadding(48, 48, 48, 48);

        new AlertDialog.Builder(activity)
            .setTitle("Вход для админа")
            .setMessage("Введите пароль администратора")
            .setView(inputLayout)
            .setPositiveButton("Войти", (dialog, which) -> {
                String password = input.getText().toString().trim();
                if (password.equals(ADMIN_PIN)) {
                    prefs.edit().putBoolean("is_admin", true).apply();
                    Toast.makeText(activity, "Добро пожаловать, Админ!", Toast.LENGTH_SHORT).show();
                    activity.startActivity(new android.content.Intent(activity, AdminActivity.class));
                    activity.finish();
                } else {
                    Toast.makeText(activity, "Неверный пароль", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }
}
