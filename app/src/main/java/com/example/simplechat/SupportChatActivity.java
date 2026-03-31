package com.example.simplechat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.simplechat.data.AppDatabase;
import com.example.simplechat.data.MessageEntity;
import com.example.simplechat.data.UserProfileEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Чат с поддержкой для пользователей
 */
public class SupportChatActivity extends AppCompatActivity implements MessageAdapter.OnMessageClickListener {

    private RecyclerView messagesRecyclerView;
    private MessageAdapter messageAdapter;
    private List<Message> messageList;
    private EditText messageInput;
    private ImageButton sendButton;
    private TextView emptyText;
    
    private AppDatabase database;
    private ExecutorService executorService;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    private static final long SUPPORT_CHAT_ID = 9999;
    private String userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support_chat);

        database = AppDatabase.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();

        // Инициализация view
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        emptyText = findViewById(R.id.emptyText);
        TextView chatTitle = findViewById(R.id.chatTitle);
        TextView chatAvatar = findViewById(R.id.chatAvatar);
        View backButton = findViewById(R.id.backButton);

        // Загружаем имя пользователя
        executorService.execute(() -> {
            UserProfileEntity profile = database.userProfileDao().getProfile();
            if (profile != null) {
                userName = profile.getName();
            } else {
                userName = "Пользователь";
            }
        });

        // Настраиваем заголовок
        chatTitle.setText("Поддержка");
        chatAvatar.setText("🎧");

        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList, this);

        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(messageAdapter);

        // Загрузка сообщений
        loadMessages();

        // Отправка сообщения
        sendButton.setOnClickListener(v -> sendMessage());

        // Назад
        backButton.setOnClickListener(v -> finish());
    }

    private void loadMessages() {
        executorService.execute(() -> {
            List<MessageEntity> entities = database.messageDao()
                    .getMessagesForChatSync(SUPPORT_CHAT_ID);

            List<Message> messages = new ArrayList<>();
            for (MessageEntity entity : entities) {
                messages.add(new Message(entity));
            }

            handler.post(() -> {
                messageList.clear();
                messageList.addAll(messages);
                messageAdapter.notifyDataSetChanged();

                if (messageList.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                    messagesRecyclerView.setVisibility(View.GONE);
                } else {
                    emptyText.setVisibility(View.GONE);
                    messagesRecyclerView.setVisibility(View.VISIBLE);
                    messagesRecyclerView.scrollToPosition(messageList.size() - 1);
                }
            });
        });
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();

        if (text.isEmpty()) {
            Toast.makeText(this, "Введите сообщение", Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            // Сохраняем сообщение в БД
            MessageEntity message = new MessageEntity(
                SUPPORT_CHAT_ID,
                text,
                true, // Исходящее
                System.currentTimeMillis(),
                MessageEntity.MessageStatus.SENT,
                "", // Путь к файлу (пусто для текста)
                MessageEntity.MediaType.NONE
            );
            database.messageDao().insert(message);

            handler.post(() -> {
                messageInput.setText("");
                loadMessages();
                
                // Имитация ответа поддержки
                simulateSupportResponse();
            });
        });
    }

    private void simulateSupportResponse() {
        // Имитация ответа через 2 секунды
        handler.postDelayed(() -> {
            executorService.execute(() -> {
                String[] responses = {
                    "Здравствуйте! Спасибо за обращение в поддержку.",
                    "Понял вашу проблему. Сейчас разберёмся.",
                    "Спасибо за подробное описание. Изучаю вопрос.",
                    "Хороший вопрос! Дайте мне немного времени на изучение.",
                    "Благодарю за обращение! Мы работаем над улучшением приложения."
                };
                
                String responseText = responses[(int)(Math.random() * responses.length)];
                
                MessageEntity responseMessage = new MessageEntity(
                    SUPPORT_CHAT_ID,
                    responseText,
                    false, // Входящее
                    System.currentTimeMillis(),
                    MessageEntity.MessageStatus.SENT,
                    "", // Путь к файлу
                    MessageEntity.MediaType.NONE
                );
                database.messageDao().insert(responseMessage);

                handler.post(this::loadMessages);
            });
        }, 2000);
    }

    @Override
    public void onMessageClick(Message message) {
        // Пока ничего не делаем при клике на сообщение
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMessages();
    }
}
