package com.example.simplechat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.simplechat.data.AppDatabase;
import com.example.simplechat.data.ChatDao;
import com.example.simplechat.data.ChatEntity;
import com.example.simplechat.p2p.P2PClient;

import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NewP2PChatActivity extends AppCompatActivity implements P2PClient.P2PEventListener {

    private EditText userIdEditText;
    private Button createChatButton;
    private ImageButton backButton;
    private TextView connectionStatusText;

    private P2PClient p2pClient;
    private AppDatabase database;
    private ExecutorService executorService;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_p2p_chat);

        database = AppDatabase.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();

        userIdEditText = findViewById(R.id.userIdEditText);
        createChatButton = findViewById(R.id.createChatButton);
        backButton = findViewById(R.id.backButton);
        connectionStatusText = findViewById(R.id.connectionStatusText);

        backButton.setOnClickListener(v -> finish());

        createChatButton.setOnClickListener(v -> createP2PChat());

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

    private void createP2PChat() {
        String friendUserId = userIdEditText.getText().toString().trim();

        if (friendUserId.isEmpty()) {
            Toast.makeText(this, "Введите userId друга", Toast.LENGTH_SHORT).show();
            return;
        }

        if (friendUserId.length() < 8) {
            Toast.makeText(this, "Неверный формат userId", Toast.LENGTH_SHORT).show();
            return;
        }

        if (p2pClient == null || !p2pClient.isConnected()) {
            Toast.makeText(this, "Нет соединения с сервером", Toast.LENGTH_SHORT).show();
            return;
        }

        // Проверяем, есть ли уже чат
        executorService.execute(() -> {
            ChatDao chatDao = database.chatDao();
            ChatEntity existingChat = chatDao.getChatByFriendUserIdSync(friendUserId);

            if (existingChat != null) {
                // Чат уже есть - открываем его
                openChat(existingChat.getId(), friendUserId);
            } else {
                // Создаём новый чат
                ChatEntity newChat = new ChatEntity(
                    System.currentTimeMillis(),
                    "P2P: " + friendUserId.substring(0, 8),
                    "👤",
                    "Новый P2P чат",
                    System.currentTimeMillis(),
                    true,
                    1,
                    friendUserId
                );
                chatDao.insert(newChat);
                openChat(newChat.getId(), friendUserId);
            }
        });
    }

    private void openChat(long chatId, String friendUserId) {
        Intent intent = new Intent(this, P2PChatActivity.class);
        intent.putExtra("chat_id", chatId);
        intent.putExtra("friend_user_id", friendUserId);
        startActivity(intent);
        finish();
    }

    @Override
    public void onConnected(String userId) {
        updateStatus("✅ Подключено: " + userId.substring(0, 8));
    }

    @Override
    public void onDisconnected() {
        updateStatus("❌ Отключено");
    }

    @Override
    public void onMessageReceived(String from, org.json.JSONObject payload) {}

    @Override
    public void onUserFound(String userId, boolean online) {}

    @Override
    public void onTyping(String from) {}

    @Override
    public void onMessageDelivered(String to, String messageId) {}

    @Override
    public void onWebRTCOffer(String from, org.json.JSONObject payload) {}

    @Override
    public void onWebRTCAnswer(String from, org.json.JSONObject payload) {}

    @Override
    public void onWebRTCIceCandidate(String from, org.json.JSONObject payload) {}

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
