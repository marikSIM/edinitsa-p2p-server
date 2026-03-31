package com.example.simplechat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.simplechat.data.AppDatabase;
import com.example.simplechat.data.UserProfileEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Экран настроек приложения
 */
public class SettingsActivity extends AppCompatActivity {

    private TextView versionText;
    private AppDatabase database;
    private ExecutorService executorService;
    private SharedPreferences adminPrefs;
    private UpdateManager updateManager;

    private int clickCount = 0;
    private static final int ADMIN_CLICK_COUNT = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        database = AppDatabase.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();
        adminPrefs = getSharedPreferences("admin_prefs", MODE_PRIVATE);
        updateManager = new UpdateManager(this);

        // Инициализация view
        versionText = findViewById(R.id.versionText);
        View backButton = findViewById(R.id.backButton);
        TextView supportLinkText = findViewById(R.id.supportLinkText);
        View checkUpdateLayout = findViewById(R.id.checkUpdateLayout);

        // Установка версии приложения
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            versionText.setText("Версия: " + versionName);
        } catch (Exception e) {
            versionText.setText("Версия: 1.0");
        }

        // Клик по версии для входа в админку или проверки обновлений
        versionText.setOnClickListener(v -> {
            clickCount++;
            if (clickCount >= ADMIN_CLICK_COUNT) {
                clickCount = 0;
                // Проверяем, не является ли уже пользователь админом
                if (AdminActivity.isAdmin(adminPrefs)) {
                    // Уже админ, просто открываем админку
                    startActivity(new android.content.Intent(this, AdminActivity.class));
                } else {
                    // Показываем диалог входа
                    AdminActivity.showAdminLoginDialog(this, adminPrefs);
                }
            } else if (clickCount == 3) {
                Toast.makeText(this, "Ещё " + (ADMIN_CLICK_COUNT - clickCount) + " нажатия", Toast.LENGTH_SHORT).show();
            } else if (clickCount == 4) {
                Toast.makeText(this, "Ещё 1 нажатие", Toast.LENGTH_SHORT).show();
            }
        });

        // Долгий клик по версии для проверки обновлений
        versionText.setOnLongClickListener(v -> {
            updateManager.checkForUpdatesForced();
            return true;
        });

        // Клик по кнопке проверки обновлений
        checkUpdateLayout.setOnClickListener(v -> {
            updateManager.checkForUpdatesForced();
        });

        // Клик по ссылке на поддержку
        supportLinkText.setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, SupportChatActivity.class));
        });

        // Назад
        backButton.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Сбрасываем счётчик кликов
        clickCount = 0;
        
        // Проверяем обновления при входе в настройки
        updateManager.checkForUpdates();
    }
}
