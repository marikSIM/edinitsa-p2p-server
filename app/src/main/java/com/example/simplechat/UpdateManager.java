package com.example.simplechat;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Менеджер обновлений приложения
 * 
 * Работает БЕЗ Firebase и Google Play
 * 
 * Варианты использования:
 * 
 * 1. Простая ссылка на Telegram-канал:
 *    - В настройках укажите ссылку на ваш канал
 *    - Пользователь переходит и скачивает APK
 * 
 * 2. JSON файл на GitHub/GitLab:
 *    - Создайте файл version.json с версией и ссылкой
 *    - Приложение проверяет версию автоматически
 * 
 * Пример JSON (разместить на GitHub Pages или любом хостинге):
 * {
 *   "version_code": 2,
 *   "version_name": "1.1",
 *   "message": "Доступна новая версия!",
 *   "download_url": "https://t.me/your_channel/123"
 * }
 */
public class UpdateManager {

    private Activity activity;
    private ExecutorService executorService;
    private Handler handler;
    
    // URL для проверки обновлений
    // Разместите version.json на GitHub Pages или вашем сервере
    private static final String VERSION_JSON_URL = "https://raw.githubusercontent.com/marikSIM/edinitsa-android/main/version.json";

    // Telegram канал для обновлений
    private static final String TELEGRAM_CHANNEL_URL = "https://t.me/edinitsa_messenger";

    // Текущая версия (увеличивайте при каждом обновлении!)
    private static final int CURRENT_VERSION_CODE = 2;
    private static final String CURRENT_VERSION_NAME = "1.1";

    public UpdateManager(Activity activity) {
        this.activity = activity;
        this.executorService = Executors.newSingleThreadExecutor();
        this.handler = new Handler(Looper.getMainLooper());
    }

    /**
     * Проверить наличие обновлений (автоматически)
     * Вызывать при входе в настройки
     */
    public void checkForUpdates() {
        executorService.execute(() -> {
            try {
                // Пытаемся получить JSON с версией
                String jsonContent = downloadJson(VERSION_JSON_URL);
                if (jsonContent != null) {
                    checkVersionFromJson(jsonContent);
                    return;
                }
            } catch (Exception e) {
                // JSON не доступен — не показываем ошибку
            }
            
            // Если JSON не доступен — просто ничего не делаем
            // (можно показать ссылку на Telegram при ручном запросе)
        });
    }

    /**
     * Проверить наличие обновлений (вручную, по запросу пользователя)
     */
    public void checkForUpdatesForced() {
        Toast.makeText(activity, "Проверка обновлений...", Toast.LENGTH_SHORT).show();
        
        executorService.execute(() -> {
            try {
                String jsonContent = downloadJson(VERSION_JSON_URL);
                if (jsonContent != null) {
                    checkVersionFromJson(jsonContent);
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // Если JSON не доступен — предлагаем перейти в Telegram
            handler.post(() -> {
                new AlertDialog.Builder(activity)
                    .setTitle("Обновление")
                    .setMessage("Автоматическая проверка недоступна.\n\n" +
                               "Перейти в Telegram-канал для проверки обновлений?")
                    .setPositiveButton("Перейти", (dialog, which) -> {
                        openUrl(TELEGRAM_CHANNEL_URL);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            });
        });
    }

    private void checkVersionFromJson(String jsonContent) {
        try {
            JSONObject json = new JSONObject(jsonContent);
            int latestVersionCode = json.getInt("version_code");
            String versionName = json.getString("version_name");
            String message = json.getString("message");
            String downloadUrl = json.getString("download_url");

            if (latestVersionCode > CURRENT_VERSION_CODE) {
                showUpdateDialog(message, versionName, downloadUrl);
            } else {
                handler.post(() -> {
                    Toast.makeText(activity, "Установлена последняя версия", Toast.LENGTH_SHORT).show();
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            handler.post(() -> {
                Toast.makeText(activity, "Ошибка проверки обновлений", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void showUpdateDialog(String message, String versionName, String downloadUrl) {
        handler.post(() -> {
            new AlertDialog.Builder(activity)
                .setTitle("Доступно обновление")
                .setMessage(message + "\n\nВерсия: " + versionName)
                .setPositiveButton("Скачать", (dialog, which) -> {
                    openUrl(downloadUrl);
                })
                .setNegativeButton("Позже", null)
                .setCancelable(false)
                .show();
        });
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Ошибка открытия ссылки", Toast.LENGTH_SHORT).show();
        }
    }

    private String downloadJson(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream())
                );
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                reader.close();
                return result.toString();
            }
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
