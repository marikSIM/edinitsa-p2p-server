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
import com.example.simplechat.p2p.P2PClient;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class P2PChatActivity extends AppCompatActivity implements P2PClient.P2PEventListener {

    private RecyclerView messagesRecyclerView;
    private EditText messageEditText;
    private ImageButton sendButton;
    private ImageButton backButton;
    private TextView chatNameText;
    private TextView connectionStatusText;
    private MessageAdapter messageAdapter;
    private List<Message> messageList;

    private AppDatabase database;
    private ExecutorService executorService;
    private Handler handler = new Handler(Looper.getMainLooper());

    private P2PClient p2pClient;
    private String friendUserId;
    private long chatId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_p2p_chat);

        database = AppDatabase.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();

        friendUserId = getIntent().getStringExtra("friend_user_id");
        chatId = getIntent().getLongExtra("chat_id", -1);

        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        backButton = findViewById(R.id.backButton);
        chatNameText = findViewById(R.id.chatNameText);
        connectionStatusText = findViewById(R.id.connectionStatusText);

        chatNameText.setText("Чат: " + friendUserId.substring(0, 8));

        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList);

        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(messageAdapter);

        loadMessages();

        backButton.setOnClickListener(v -> finish());

        sendButton.setOnClickListener(v -> sendMessage());

        // Инициализация P2P клиента
        initP2PClient();
    }

    private void initP2PClient() {
        p2pClient = new P2PClient();
        p2pClient.addEventListener(this);
        p2pClient.connect();
        updateStatus("Подключение...");
    }

    private void updateStatus(String status) {
        handler.post(() -> connectionStatusText.setText(status));
    }

    private void loadMessages() {
        if (chatId <= 0) {
            messageAdapter.addMessage(new Message("Начните чат! Сообщения будут доставлены другу 👋", false));
            return;
        }

        executorService.execute(() -> {
            List<MessageEntity> entities = database.messageDao().getMessagesForChatSync(chatId);
            handler.post(() -> {
                if (!entities.isEmpty()) {
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
        if (text.isEmpty() || friendUserId == null || p2pClient == null || !p2pClient.isConnected()) {
            Toast.makeText(this, "Нет соединения с сервером", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("text", text);
            payload.put("timestamp", System.currentTimeMillis());

            // Отправляем через P2P сервер
            p2pClient.sendMessage(friendUserId, payload);

            // Сохраняем локально
            Message message = new Message(text, true);
            if (chatId > 0) {
                executorService.execute(() -> {
                    MessageEntity entity = message.toEntity(chatId);
                    database.messageDao().insert(entity);
                    database.chatDao().updateLastMessage(chatId, text, System.currentTimeMillis());
                });
            }

            messageAdapter.addMessage(message);
            messageEditText.setText("");
            messagesRecyclerView.scrollToPosition(messageList.size() - 1);

        } catch (Exception e) {
            Toast.makeText(this, "Ошибка отправки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConnected(String userId) {
        updateStatus("✅ Подключено");
        Toast.makeText(this, "P2P подключено!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisconnected() {
        updateStatus("❌ Отключено");
    }

    @Override
    public void onMessageReceived(String from, JSONObject payload) {
        if (from.equals(friendUserId)) {
            try {
                String text = payload.getString("text");
                Message message = new Message(text, false);

                if (chatId > 0) {
                    executorService.execute(() -> {
                        MessageEntity entity = message.toEntity(chatId);
                        database.messageDao().insert(entity);
                        database.chatDao().updateLastMessage(chatId, text, System.currentTimeMillis());
                    });
                }

                handler.post(() -> {
                    messageAdapter.addMessage(message);
                    messagesRecyclerView.scrollToPosition(messageList.size() - 1);
                    Toast.makeText(this, "Сообщение от " + from.substring(0, 8), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onUserFound(String userId, boolean online) {
        updateStatus(online ? "✅ Онлайн" : "⚠️ Офлайн");
    }

    @Override
    public void onTyping(String from) {}

    @Override
    public void onMessageDelivered(String to, String messageId) {
        handler.post(() -> Toast.makeText(this, "Доставлено!", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onWebRTCOffer(String from, JSONObject payload) {}

    @Override
    public void onWebRTCAnswer(String from, JSONObject payload) {}

    @Override
    public void onWebRTCIceCandidate(String from, JSONObject payload) {}

    @Override
    public void onError(String error) {
        updateStatus("⚠️ Ошибка");
    }

    @Override
    public void onContactsSynced(List<JSONObject> matches) {
        // Не используется в этой Activity
    }

    @Override
    public void onPhoneRegistered(String userId, boolean alreadyRegistered) {
        // Не используется в этой Activity
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (p2pClient != null) {
            p2pClient.disconnect();
        }
    }
}
