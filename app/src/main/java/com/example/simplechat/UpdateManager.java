package com.example.simplechat;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Менеджер обновлений приложения
 *
 * Автоматическая проверка и загрузка обновлений
 *
 * Принцип работы:
 * 1. Проверяем version.json на GitHub
 * 2. Если новая версия — показываем диалог
 * 3. При согласии — скачиваем APK через DownloadManager
 * 4. После загрузки — предлагаем установить
 */
public class UpdateManager {

    private Activity activity;
    private ExecutorService executorService;
    private Handler handler;

    // URL для проверки обновлений
    // Разместите version.json на GitHub Pages или вашем сервере
    private static final String VERSION_JSON_URL = 
        "https://raw.githubusercontent.com/marikSIM/edinitsa-p2p-server/main/version.json";

    // Текущая версия (увеличивайте при каждом обновлении!)
    private static final int CURRENT_VERSION_CODE = 3;
    private static final String CURRENT_VERSION_NAME = "1.2";

    private static final int PERMISSION_REQUEST_CODE = 200;

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
                String jsonContent = downloadJson(VERSION_JSON_URL);
                if (jsonContent != null) {
                    checkVersionFromJson(jsonContent);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
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

            handler.post(() -> {
                Toast.makeText(activity, "Ошибка проверки обновлений", Toast.LENGTH_SHORT).show();
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
                .setPositiveButton("Скачать и установить", (dialog, which) -> {
                    downloadAndInstallUpdate(downloadUrl);
                })
                .setNegativeButton("Позже", null)
                .setCancelable(false)
                .show();
        });
    }

    /**
     * Скачать и установить обновление
     */
    private void downloadAndInstallUpdate(String downloadUrl) {
        // Проверяем разрешение на установку
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                // Запрашиваем разрешение
                ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.REQUEST_INSTALL_PACKAGES},
                    PERMISSION_REQUEST_CODE);
                return;
            }
        }

        // Проверяем разрешение на запись
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                PERMISSION_REQUEST_CODE);
            return;
        }

        // Скачиваем через DownloadManager
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setTitle("ЕДИНИЦА Обновление");
        request.setDescription("Загрузка новой версии...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setAllowedOverMetered(true);
        request.setAllowedOverRoaming(true);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "edinitsa-update.apk");

        DownloadManager downloadManager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = downloadManager.enqueue(request);

        // Слушаем завершение загрузки
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                activity.unregisterReceiver(this);
                
                // Проверяем статус загрузки
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                android.database.Cursor cursor = downloadManager.query(query);
                
                if (cursor != null && cursor.moveToFirst()) {
                    int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(statusIndex);
                    
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        // Загрузка успешна — устанавливаем
                        installApk(context, downloadId);
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        Toast.makeText(activity, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
                    }
                    cursor.close();
                }
            }
        };

        activity.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        
        Toast.makeText(activity, "Загрузка началась...", Toast.LENGTH_SHORT).show();
    }

    /**
     * Установить APK
     */
    private void installApk(Context context, long downloadId) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri apkUri = downloadManager.getUriForDownloadedFile(downloadId);

        if (apkUri == null) {
            // Фолбэк — используем прямой путь
            File apkFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), 
                "edinitsa-update.apk");
            apkUri = FileProvider.getUriForFile(context, 
                context.getPackageName() + ".provider", apkFile);
        }

        Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        installIntent.setData(apkUri);
        installIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        context.startActivity(installIntent);
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
