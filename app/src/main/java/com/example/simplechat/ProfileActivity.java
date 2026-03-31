package com.example.simplechat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.simplechat.ChatListActivityInstance;
import com.example.simplechat.data.AppDatabase;
import com.example.simplechat.data.UserProfileEntity;
import com.example.simplechat.utils.QRCodeGenerator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Экран редактирования профиля пользователя
 */
public class ProfileActivity extends AppCompatActivity {

    private TextView userAvatar;
    private ImageView profilePhoto;
    private EditText editName;
    private EditText editStatus;
    private Button saveButton;
    private Button cancelButton;
    private Button changeEmojiButton;
    private Button changePhotoButton;
    private Button showQRButton;
    private TextView avatarHintText;

    private AppDatabase database;
    private ExecutorService executorService;

    private int currentAvatarIndex = 0;
    private final String[] avatars = {"👤", "😎", "🤔", "😄", "🎯", "⚡", "🔥", "💎"};

    private String currentPhotoPath = null;
    private boolean isUsingPhoto = false;

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int PICK_IMAGE_REQUEST = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        database = AppDatabase.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();

        // Инициализация view
        userAvatar = findViewById(R.id.profileAvatar);
        profilePhoto = findViewById(R.id.profilePhoto);
        editName = findViewById(R.id.editName);
        editStatus = findViewById(R.id.editStatus);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        changeEmojiButton = findViewById(R.id.changeEmojiButton);
        changePhotoButton = findViewById(R.id.changePhotoButton);
        avatarHintText = findViewById(R.id.avatarHintText);
        View backButton = findViewById(R.id.backButton);

        // Загрузка профиля
        loadProfile();

        // Клик по аватару для смены эмодзи
        userAvatar.setOnClickListener(v -> {
            currentAvatarIndex = (currentAvatarIndex + 1) % avatars.length;
            userAvatar.setText(avatars[currentAvatarIndex]);
            isUsingPhoto = false;
            updateAvatarVisibility();
        });

        // Кнопка смены эмодзи
        changeEmojiButton.setOnClickListener(v -> {
            currentAvatarIndex = (currentAvatarIndex + 1) % avatars.length;
            userAvatar.setText(avatars[currentAvatarIndex]);
            isUsingPhoto = false;
            updateAvatarVisibility();
            avatarHintText.setText("Нажмите на аватар для смены эмодзи");
        });

        // Кнопка загрузки фото
        changePhotoButton.setOnClickListener(v -> showPhotoPickerDialog());

        // Кнопка показа QR-кода
        showQRButton = findViewById(R.id.showQRButton);
        showQRButton.setOnClickListener(v -> showQRCodeDialog());

        // Сохранение
        saveButton.setOnClickListener(v -> saveProfile());

        // Отмена
        cancelButton.setOnClickListener(v -> finish());

        // Назад
        backButton.setOnClickListener(v -> finish());
    }

    private void loadProfile() {
        executorService.execute(() -> {
            UserProfileEntity profile = database.userProfileDao().getProfile();

            if (profile != null) {
                runOnUiThread(() -> {
                    editName.setText(profile.getName());
                    editStatus.setText(profile.getStatus());
                    currentPhotoPath = profile.getPhotoPath();

                    // Находим индекс аватара
                    for (int i = 0; i < avatars.length; i++) {
                        if (avatars[i].equals(profile.getAvatar())) {
                            currentAvatarIndex = i;
                            break;
                        }
                    }
                    userAvatar.setText(profile.getAvatar());
                    
                    // Если есть фото, показываем его
                    if (currentPhotoPath != null && !currentPhotoPath.isEmpty()) {
                        File photoFile = new File(currentPhotoPath);
                        if (photoFile.exists()) {
                            isUsingPhoto = true;
                            updateAvatarVisibility();
                            try {
                                Glide.with(this)
                                    .load(photoFile)
                                    .centerCrop()
                                    .into(profilePhoto);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
        });
    }

    private void updateAvatarVisibility() {
        if (isUsingPhoto) {
            userAvatar.setVisibility(View.GONE);
            profilePhoto.setVisibility(View.VISIBLE);
        } else {
            userAvatar.setVisibility(View.VISIBLE);
            profilePhoto.setVisibility(View.GONE);
        }
    }

    private void showPhotoPickerDialog() {
        String[] options = {"Галерея", "Камера", "Удалить фото"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выберите действие")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    openGallery();
                } else if (which == 1) {
                    openCamera();
                } else if (which == 2) {
                    removePhoto();
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSION_REQUEST_CODE);
        } else {
            launchCamera();
        }
    }

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this, "Разрешение камеры не предоставлено", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_IMAGE_REQUEST) {
                if (data != null && data.getData() != null) {
                    Uri imageUri = data.getData();
                    // Просто сохраняем фото без кропа
                    savePhotoToInternalStorage(imageUri);
                } else if (data.hasExtra("data")) {
                    // Фото с камеры
                    Bitmap bitmap = (Bitmap) data.getExtras().get("data");
                    Uri tempUri = getImageUri(bitmap);
                    if (tempUri != null) {
                        savePhotoToInternalStorage(tempUri);
                    }
                }
            }
        }
    }

    private void savePhotoToInternalStorage(Uri imageUri) {
        executorService.execute(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                File photoFile = new File(getFilesDir(), "profile_photo.jpg");
                FileOutputStream outputStream = new FileOutputStream(photoFile);
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                inputStream.close();
                outputStream.close();
                
                currentPhotoPath = photoFile.getAbsolutePath();
                isUsingPhoto = true;
                
                runOnUiThread(() -> {
                    updateAvatarVisibility();
                    avatarHintText.setText("Фото профиля установлено");
                    try {
                        Glide.with(this)
                            .load(photoFile)
                            .centerCrop()
                            .into(profilePhoto);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Ошибка сохранения фото", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private Uri getImageUri(Bitmap bitmap) {
        File file = new File(getCacheDir(), "temp_photo.jpg");
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            outputStream.flush();
            outputStream.close();
            return Uri.fromFile(file);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void removePhoto() {
        currentPhotoPath = null;
        isUsingPhoto = false;
        currentAvatarIndex = 0;
        userAvatar.setText(avatars[0]);
        updateAvatarVisibility();
        avatarHintText.setText("Фото удалено. Выберите эмодзи или загрузите новое фото.");
        
        // Удаляем файл фото
        executorService.execute(() -> {
            File photoFile = new File(getFilesDir(), "profile_photo.jpg");
            if (photoFile.exists()) {
                photoFile.delete();
            }
            database.userProfileDao().updatePhoto(null);
        });
    }

    private void saveProfile() {
        String name = editName.getText().toString().trim();
        String status = editStatus.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Введите имя", Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            if (isUsingPhoto && currentPhotoPath != null) {
                database.userProfileDao().updateProfileWithPhoto(name, avatars[currentAvatarIndex], status, currentPhotoPath);
            } else {
                database.userProfileDao().updateProfile(name, avatars[currentAvatarIndex], status);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "Профиль сохранён", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    /**
     * Показать диалог с QR-кодом пользователя
     */
    private void showQRCodeDialog() {
        // Получаем userId из P2PClient
        String userId = ChatListActivityInstance.getInstance() != null 
            ? ChatListActivityInstance.getInstance().getUserId() 
            : null;

        if (userId == null) {
            Toast.makeText(this, "P2P не подключен", Toast.LENGTH_SHORT).show();
            return;
        }

        // Генерируем QR-код
        Bitmap qrBitmap = QRCodeGenerator.generateUserQRCode(userId, 512);

        if (qrBitmap == null) {
            Toast.makeText(this, "Ошибка генерации QR-кода", Toast.LENGTH_SHORT).show();
            return;
        }

        // Создаём диалог
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Мой QR-код");

        // Создаём ImageView для QR-кода
        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(qrBitmap);
        imageView.setPadding(32, 32, 32, 32);
        builder.setView(imageView);

        // Кнопка "Копировать ID"
        builder.setPositiveButton("Копировать ID", (dialog, which) -> {
            android.content.ClipboardManager clipboard = 
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("User ID", userId);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "ID скопирован", Toast.LENGTH_SHORT).show();
        });

        // Кнопка "Поделиться"
        builder.setNeutralButton("Поделиться", (dialog, which) -> {
            shareUserId(userId);
        });

        builder.setNegativeButton("Закрыть", null);
        builder.show();
    }

    /**
     * Поделиться userId
     */
    private void shareUserId(String userId) {
        String shareText = "Добавь меня в ЕДИНИЦЕ! Мой ID: " + userId + 
            "\n\nСкачай приложение: https://edinitsa.app";
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        
        startActivity(Intent.createChooser(shareIntent, "Поделиться через"));
    }
}
