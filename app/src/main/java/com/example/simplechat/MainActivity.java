package com.example.simplechat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.simplechat.data.AppDatabase;
import com.example.simplechat.data.MessageEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.net.Uri;
import android.provider.MediaStore;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.Activity;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import android.widget.Toast;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import android.content.ContentValues;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int REQUEST_IMAGE_PICK = 101;
    private static final int REQUEST_IMAGE_CAPTURE = 102;
    private static final int REQUEST_VIDEO_PICK = 103;
    
    private Uri imageUri;

    // Launcher для выбора изображения
    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.GetContent(),
        uri -> {
            if (uri != null) {
                sendMediaMessage(uri, MessageEntity.MediaType.IMAGE);
            }
        }
    );
    
    // Launcher для съёмки фото
    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
        new ActivityResultContracts.TakePicture(),
        success -> {
            if (success && imageUri != null) {
                sendMediaMessage(imageUri, MessageEntity.MediaType.IMAGE);
            }
        }
    );
    
    // Launcher для выбора видео
    private final ActivityResultLauncher<String> videoPickerLauncher = registerForActivityResult(
        new ActivityResultContracts.GetContent(),
        uri -> {
            if (uri != null) {
                sendMediaMessage(uri, MessageEntity.MediaType.VIDEO);
            }
        }
    );

    private RecyclerView messagesRecyclerView;
    private EditText messageEditText;
    private ImageButton sendButton;
    private ImageButton backButton;
    private ImageButton chatMenuButton;
    private TextView chatNameText;
    private TextView chatStatusText;
    private MessageAdapter messageAdapter;
    private List<Message> messageList;

    private AppDatabase database;
    private ExecutorService executorService;
    private Handler handler = new Handler(Looper.getMainLooper());

    private long chatId;
    private String chatName;
    private String chatAvatar;
    private boolean isOnline;

    // Имитация собеседника для демонстрации
    private final List<String> botResponses = Arrays.asList(
            "Привет! Как дела?",
            "Отличное сообщение!",
            "Интересно, расскажи ещё!",
            "Я всего лишь демо-бот 😊",
            "Понял, спасибо!",
            "Круто!",
            "Да, согласен!",
            "Что ещё расскажешь?"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализация базы данных
        database = AppDatabase.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();

        // Получаем данные о чате
        if (getIntent() != null) {
            chatId = getIntent().getLongExtra("chat_id", -1);
            chatName = getIntent().getStringExtra("chat_name");
            chatAvatar = getIntent().getStringExtra("chat_avatar");
            isOnline = getIntent().getBooleanExtra("chat_online", false);
        }

        // Инициализация view
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        backButton = findViewById(R.id.backButton);
        chatNameText = findViewById(R.id.chatNameText);
        chatStatusText = findViewById(R.id.chatStatusText);

        // Устанавливаем имя и статус
        if (chatName != null) {
            chatNameText.setText(chatName);
        } else {
            chatNameText.setText("Чат");
        }
        chatStatusText.setText(isOnline ? "онлайн" : "был(а) недавно");

        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList);

        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(messageAdapter);

        // Загружаем сообщения из БД
        loadMessagesFromDatabase();

        // Кнопка назад
        backButton.setOnClickListener(v -> finish());

        // Кнопка меню чата (3 точки)
        chatMenuButton = findViewById(R.id.chatMenuButton);
        chatMenuButton.setOnClickListener(v -> showChatMenu());

        // Кнопка прикрепления файлов
        ImageButton attachButton = findViewById(R.id.attachButton);
        attachButton.setOnClickListener(v -> showAttachDialog());

        // Отправка сообщения
        sendButton.setOnClickListener(v -> sendMessage());

        messageEditText.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
    }

    // Показ меню выбора файла
    private void showAttachDialog() {
        String[] options = {"📷 Камера", "🖼️ Галерея", "🎥 Видео"};
        new AlertDialog.Builder(this)
            .setTitle("Прикрепить файл")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // Камера
                    openCamera();
                } else if (which == 1) {
                    // Галерея
                    openGallery();
                } else if (which == 2) {
                    // Видео
                    openVideoPicker();
                }
            })
            .show();
    }
    
    // Открытие камеры
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                PERMISSION_REQUEST_CODE);
        } else {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, "photo_" + System.currentTimeMillis());
            imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            cameraLauncher.launch(imageUri);
        }
    }
    
    // Открытие галереи
    private void openGallery() {
        imagePickerLauncher.launch("image/*");
    }
    
    // Выбор видео
    private void openVideoPicker() {
        videoPickerLauncher.launch("video/*");
    }

    private void loadMessagesFromDatabase() {
        if (chatId <= 0) {
            // Если chatId не передан, показываем приветственное сообщение
            messageAdapter.addMessage(new Message("Добро пожаловать в чат! 👋", false));
            return;
        }
        
        executorService.execute(() -> {
            List<MessageEntity> entities = database.messageDao().getMessagesForChatSync(chatId);
            
            handler.post(() -> {
                if (entities.isEmpty()) {
                    messageAdapter.addMessage(new Message("Добро пожаловать в чат! 👋", false));
                } else {
                    messageList.clear();
                    for (MessageEntity entity : entities) {
                        messageList.add(new Message(entity));
                    }
                    messageAdapter.notifyDataSetChanged();
                    messagesRecyclerView.scrollToPosition(messageList.size() - 1);
                }
            });
        });
    }

    private void sendMessage() {
        String text = messageEditText.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Введите сообщение", Toast.LENGTH_SHORT).show();
            return;
        }

        // Создаём сообщение
        Message message = new Message(text, true);
        
        // Сохраняем в БД
        if (chatId > 0) {
            executorService.execute(() -> {
                MessageEntity entity = message.toEntity(chatId);
                database.messageDao().insert(entity);
                
                // Обновляем последнее сообщение в чате
                database.chatDao().updateLastMessage(chatId, text, System.currentTimeMillis());
            });
        }

        // Добавляем в UI
        messageAdapter.addMessage(message);
        messageEditText.setText("");
        messagesRecyclerView.scrollToPosition(messageList.size() - 1);

        // Имитируем ответ собеседника через 1-2 секунды
        handler.postDelayed(() -> {
            String response = botResponses.get((int) (Math.random() * botResponses.size()));
            Message botMessage = new Message(response, false);
            
            // Сохраняем ответ в БД
            if (chatId > 0) {
                executorService.execute(() -> {
                    MessageEntity entity = botMessage.toEntity(chatId);
                    database.messageDao().insert(entity);
                    
                    // Обновляем последнее сообщение в чате
                    database.chatDao().updateLastMessage(chatId, response, System.currentTimeMillis());
                });
            }
            
            // Добавляем в UI
            handler.post(() -> {
                messageAdapter.addMessage(botMessage);
                messagesRecyclerView.scrollToPosition(messageList.size() - 1);
            });
        }, 1000 + (int) (Math.random() * 1000));
    }

    // Отправка медиа-сообщения (фото/видео)
    private void sendMediaMessage(Uri uri, MessageEntity.MediaType mediaType) {
        // Копируем файл в внутреннее хранилище
        executorService.execute(() -> {
            try {
                String fileName = "media_" + System.currentTimeMillis() + "." + getExtension(uri);
                File destFile = new File(getFilesDir(), "media/" + fileName);
                destFile.getParentFile().mkdirs();
                
                // Копирование файла
                InputStream inputStream = getContentResolver().openInputStream(uri);
                FileOutputStream outputStream = new FileOutputStream(destFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                outputStream.close();
                
                // Создаём сообщение
                Message message = new Message(mediaType == MessageEntity.MediaType.VIDEO ? "🎥 Видео" : "📷 Фото", true);
                MessageEntity entity = message.toEntity(chatId, destFile.getAbsolutePath(), mediaType);
                
                database.messageDao().insert(entity);
                
                // Обновляем последнее сообщение в чате
                String lastMessageText = mediaType == MessageEntity.MediaType.VIDEO ? "🎥 Видео" : "📷 Фото";
                database.chatDao().updateLastMessage(chatId, lastMessageText, System.currentTimeMillis());
                
                // Добавляем в UI
                handler.post(() -> {
                    messageList.add(message);
                    messageAdapter.notifyDataSetChanged();
                    messagesRecyclerView.scrollToPosition(messageList.size() - 1);
                });
            } catch (IOException e) {
                e.printStackTrace();
                handler.post(() -> Toast.makeText(this, "Ошибка отправки файла", Toast.LENGTH_SHORT).show());
            }
        });
    }
    
    // Получение расширения файла
    private String getExtension(Uri uri) {
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(getContentResolver().getType(uri));
        return extension != null ? extension : "jpg";
    }

    // Обработка запроса разрешений
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Разрешение на использование камеры не предоставлено", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Меню чата (очистка переписки, удаление чата)
     */
    private void showChatMenu() {
        String[] options = {"🗑️ Очистить переписку", "🗑️ Удалить чат", "ℹ️ Информация"};
        new AlertDialog.Builder(this)
            .setTitle("Чат с " + chatName)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    showClearChatDialog();
                } else if (which == 1) {
                    showDeleteChatDialog();
                } else if (which == 2) {
                    Toast.makeText(this, "Информация о контакте", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    /**
     * Очистка переписки (удаление сообщений, но не чата)
     */
    private void showClearChatDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Очистить переписку")
            .setMessage("Вы уверены, что хотите удалить все сообщения в этом чате? Чат останется в списке.")
            .setPositiveButton("Очистить", (dialog, which) -> {
                executorService.execute(() -> {
                    database.messageDao().deleteMessagesForChatSync(chatId);
                    database.chatDao().updateLastMessage(chatId, "Переписка очищена", System.currentTimeMillis());
                    
                    handler.post(() -> {
                        messageList.clear();
                        messageAdapter.notifyDataSetChanged();
                        Toast.makeText(this, "Переписка очищена", Toast.LENGTH_SHORT).show();
                    });
                });
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    /**
     * Удаление чата (чат и все сообщения)
     */
    private void showDeleteChatDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Удалить чат")
            .setMessage("Вы уверены, что хотите удалить этот чат и все сообщения? Это действие нельзя отменить.")
            .setPositiveButton("Удалить", (dialog, which) -> {
                executorService.execute(() -> {
                    database.messageDao().deleteMessagesForChatSync(chatId);
                    database.chatDao().deleteChatByIdSync(chatId);
                    
                    handler.post(() -> {
                        Toast.makeText(this, "Чат удалён", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                });
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
