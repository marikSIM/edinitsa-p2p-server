package com.example.simplechat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.simplechat.data.AppDatabase;
import com.example.simplechat.data.ChatEntity;
import com.example.simplechat.data.UserProfileEntity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.simplechat.p2p.P2PClient;
import org.json.JSONObject;

public class ChatListActivity extends AppCompatActivity implements ChatAdapter.OnChatClickListener, P2PClient.P2PEventListener {

    private RecyclerView chatsRecyclerView;
    private ChatAdapter chatAdapter;
    private List<Chat> chatList;
    private AppDatabase database;
    private ExecutorService executorService;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // P2P Клиент
    private P2PClient p2pClient;
    private TextView connectionStatusText;

    // Поиск
    private ImageButton searchButton;
    private ImageButton closeSearchButton;
    private LinearLayout searchBar;
    private EditText searchEditText;
    private List<Chat> fullChatList; // Полный список для поиска
    
    // Navigation Drawer
    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private TextView navUserName;
    private TextView navUserStatus;
    private TextView navUserAvatar;

    // Тестовые данные
    private final List<String> botNames = Arrays.asList(
            "Алексей", "Мария", "Дмитрий", "Елена", "Андрей",
            "Ольга", "Сергей", "Наталья", "Михаил", "Екатерина"
    );
    private final List<String> botAvatars = Arrays.asList(
            "👨", "👩", "🧑", "👨‍🦱", "👩‍🦱",
            "👨‍🦰", "👩‍🦰", "🧔", "👨‍🦳", "👩‍🦳"
    );
    private final List<String> initialMessages = Arrays.asList(
            "Привет! Как дела?",
            "Добрый день!",
            "Как твои дела?",
            "Давно не виделись!",
            "Есть минутка?",
            "Привет! 👋",
            "Как настроение?",
            "Что нового?"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_chat_list);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка layout: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            finish();
            return;
        }

        // Инициализация базы данных
        try {
            database = AppDatabase.getInstance(this);
            executorService = Executors.newSingleThreadExecutor();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка БД: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            finish();
            return;
        }

        // Инициализация Navigation Drawer
        try {
            initNavigationDrawer();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка меню: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            finish();
            return;
        }

        chatsRecyclerView = findViewById(R.id.chatsRecyclerView);
        FloatingActionButton newChatButton = findViewById(R.id.newChatButton);
        FloatingActionButton searchFab = findViewById(R.id.searchFab);
        View menuButton = findViewById(R.id.menuButton);
        FloatingActionButton p2pChatButton = findViewById(R.id.p2pChatButton);

        // Статус подключения P2P
        connectionStatusText = findViewById(R.id.connectionStatusText);

        // Поиск
        initSearch();

        chatList = new ArrayList<>();
        fullChatList = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatList, this);

        chatsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatsRecyclerView.setAdapter(chatAdapter);

        // Инициализация и подключение P2P клиента
        try {
            initP2PClient();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка P2P: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        // Кнопка поиска
        searchFab.setOnClickListener(v -> {
            Intent intent = new Intent(ChatListActivity.this, SearchActivity.class);
            startActivity(intent);
        });

        // Кнопка нового чата - меню как в Telegram
        newChatButton.setOnClickListener(v -> showNewChatMenu());

        // Кнопка P2P чата
        p2pChatButton.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(ChatListActivity.this, NewP2PChatActivity.class);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Кнопка меню
        menuButton.setOnClickListener(v -> drawerLayout.open());

        // Загружаем чаты из БД или создаём тестовые
        loadChatsFromDatabase();

        // Автоматическая проверка обновлений (через 3 секунды после запуска)
        handler.postDelayed(this::checkForUpdates, 3000);
    }

    /**
     * Проверка обновлений (автоматически при запуске)
     */
    private void checkForUpdates() {
        UpdateManager updateManager = new UpdateManager(this);
        updateManager.checkForUpdates();
    }

    private void initP2PClient() {
        try {
            p2pClient = new P2PClient();
            p2pClient.addEventListener(this);
            p2pClient.connect();
            updateConnectionStatus("Подключение...");

            // Сохраняем экземпляр в синглтон для доступа из других Activity
            ChatListActivityInstance.setInstance(p2pClient);
        } catch (Exception e) {
            // Игнорируем ошибку P2P для тестирования
            updateConnectionStatus("⚠️ P2P недоступен");
            Toast.makeText(this, "P2P ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateConnectionStatus(String status) {
        handler.post(() -> {
            if (connectionStatusText != null) {
                connectionStatusText.setText(status);
            }
        });
    }

    // ============================================
    // Поиск по чатам
    // ============================================

    private void initSearch() {
        searchButton = findViewById(R.id.searchButton);
        closeSearchButton = findViewById(R.id.closeSearchButton);
        searchBar = findViewById(R.id.searchBar);
        searchEditText = findViewById(R.id.searchEditText);

        // Открытие поиска
        searchButton.setOnClickListener(v -> {
            searchBar.setVisibility(View.VISIBLE);
            searchEditText.requestFocus();
            searchEditText.setText("");
            filterChats("");
        });

        // Закрытие поиска
        closeSearchButton.setOnClickListener(v -> {
            searchBar.setVisibility(View.GONE);
            searchEditText.setText("");
            searchEditText.clearFocus();
            // Показываем все чаты
            chatList.clear();
            chatList.addAll(fullChatList);
            chatAdapter.notifyDataSetChanged();
        });

        // Поиск при вводе текста
        searchEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterChats(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void filterChats(String query) {
        chatList.clear();
        if (query.isEmpty()) {
            chatList.addAll(fullChatList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (Chat chat : fullChatList) {
                if (chat.getName().toLowerCase().contains(lowerQuery)) {
                    chatList.add(chat);
                }
            }
        }
        chatAdapter.notifyDataSetChanged();
    }

    private void updateFullChatList() {
        fullChatList.clear();
        fullChatList.addAll(chatList);
    }

    /**
     * Меню "Новый чат" как в Telegram
     */
    private void showNewChatMenu() {
        String[] options = {"💬 Новый чат", "👥 Создать группу", "📞 Контакт", "☁️ P2P чат"};
        new AlertDialog.Builder(this)
            .setTitle("Новый чат")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // Новый чат - открываем поиск контактов
                    Intent intent = new Intent(ChatListActivity.this, SearchActivity.class);
                    startActivity(intent);
                } else if (which == 1) {
                    // Группа
                    createGroup();
                } else if (which == 2) {
                    // Контакт - синхронизация
                    openContacts();
                } else if (which == 3) {
                    // P2P чат
                    Intent intent = new Intent(ChatListActivity.this, NewP2PChatActivity.class);
                    startActivity(intent);
                }
            })
            .show();
    }

    private void initNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.nav_view);
        
        // Получаем ссылки на элементы хедера
        View headerView = navView.getHeaderView(0);
        navUserName = headerView.findViewById(R.id.navUserName);
        navUserStatus = headerView.findViewById(R.id.navUserStatus);
        navUserAvatar = headerView.findViewById(R.id.navUserAvatar);
        
        // Загружаем профиль пользователя
        loadUserProfile();
        
        // Обработка кликов по меню
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_profile) {
                openProfile();
            } else if (id == R.id.nav_groups) {
                createGroup();
            } else if (id == R.id.nav_contacts) {
                openContacts();
            } else if (id == R.id.nav_favorites) {
                Toast.makeText(this, "Избранное (в разработке)", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_settings) {
                openSettings();
            } else if (id == R.id.nav_support) {
                openSupport();
            } else if (id == R.id.nav_clear_history) {
                showClearHistoryDialog();
            }

            drawerLayout.close();
            return true;
        });
    }

    private void openContacts() {
        Intent intent = new Intent(this, ContactsActivity.class);
        startActivity(intent);
    }

    private void openSupport() {
        Intent intent = new Intent(this, SupportChatActivity.class);
        startActivity(intent);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void createGroup() {
        Intent intent = new Intent(this, MyGroupsActivity.class);
        startActivity(intent);
    }

    private void loadUserProfile() {
        executorService.execute(() -> {
            UserProfileEntity profile = database.userProfileDao().getProfile();
            
            if (profile == null) {
                // Создаём профиль по умолчанию
                profile = new UserProfileEntity(
                    "Пользователь",
                    "👤",
                    "онлайн",
                    "",
                    "",
                    null
                );
                database.userProfileDao().insert(profile);
            }
            
            UserProfileEntity finalProfile = profile;
            handler.post(() -> {
                navUserName.setText(finalProfile.getName());
                navUserStatus.setText(finalProfile.getStatus());
                navUserAvatar.setText(finalProfile.getAvatar());
            });
        });
    }

    private void openProfile() {
        Intent intent = new Intent(this, ProfileActivity.class);
        startActivity(intent);
    }

    private void loadChatsFromDatabase() {
        executorService.execute(() -> {
            List<ChatEntity> entities = database.chatDao().getAllChatsSync();

            if (entities.isEmpty()) {
                // Если БД пуста, создаём тестовые чаты
                createMockChats();
            } else {
                // Загружаем из БД
                List<Chat> chats = new ArrayList<>();
                for (ChatEntity entity : entities) {
                    chats.add(new Chat(entity));
                }

                handler.post(() -> {
                    chatList.clear();
                    chatList.addAll(chats);
                    updateFullChatList(); // Сохраняем полную копию
                    chatAdapter.notifyDataSetChanged();
                });
            }
        });
    }

    private void createMockChats() {
        long now = System.currentTimeMillis();
        List<ChatEntity> entities = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            ChatEntity entity = new ChatEntity(
                    i + 1,
                    botNames.get(i),
                    botAvatars.get(i),
                    initialMessages.get(i % initialMessages.size()),
                    now - (long) (Math.random() * 3600000),
                    Math.random() > 0.5,
                    new Random().nextInt(3)
            );
            entities.add(entity);
        }
        
        database.chatDao().insertAll(entities);
        
        // Загружаем только что созданные чаты
        loadChatsFromDatabase();
    }

    private void createNewChat() {
        executorService.execute(() -> {
            List<ChatEntity> existing = database.chatDao().getAllChatsSync();
            long newId = existing.size() + 1;
            
            if (newId <= botNames.size()) {
                ChatEntity entity = new ChatEntity(
                        newId,
                        botNames.get((int)newId - 1),
                        botAvatars.get((int)newId - 1),
                        "Новый чат",
                        System.currentTimeMillis(),
                        true,
                        0
                );
                database.chatDao().insert(entity);
                
                handler.post(() -> {
                    Chat chat = new Chat(entity);
                    chatList.add(0, chat);
                    chatAdapter.notifyItemInserted(0);
                    chatsRecyclerView.scrollToPosition(0);
                });
            }
        });
    }

    @Override
    public void onChatClick(Chat chat) {
        // Сбрасываем счётчик непрочитанных
        executorService.execute(() -> {
            database.chatDao().resetUnreadCount(chat.getId());
        });
        
        // Переход к экрану чата
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("chat_id", chat.getId());
        intent.putExtra("chat_name", chat.getName());
        intent.putExtra("chat_avatar", chat.getAvatar());
        intent.putExtra("chat_online", chat.isOnline());
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Обновляем список чатов при возврате из чата
        loadChatsFromDatabase();
        // Обновляем профиль
        loadUserProfile();
    }

    private void showClearHistoryDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Очистить историю")
                .setMessage("Вы уверены, что хотите удалить все чаты и сообщения? Это действие нельзя отменить.")
                .setPositiveButton("Удалить", (dialog, which) -> clearAllHistory())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void clearAllHistory() {
        executorService.execute(() -> {
            database.clearAllData();
            handler.post(() -> {
                chatList.clear();
                chatAdapter.notifyDataSetChanged();
                Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show();
            });
        });
    }

    // ============================================
    // P2PEventListener методы
    // ============================================

    @Override
    public void onConnected(String userId) {
        updateConnectionStatus("✅ Подключено: " + userId.substring(0, 8));
        Toast.makeText(this, "P2P подключено!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisconnected() {
        updateConnectionStatus("❌ Отключено");
    }

    @Override
    public void onMessageReceived(String from, JSONObject payload) {
        // Ищем чат с этим userId
        executorService.execute(() -> {
            try {
                ChatEntity chat = database.chatDao().getChatByFriendUserIdSync(from);
                if (chat != null) {
                    // Обновляем последнее сообщение
                    String text = payload.optString("text", "Новое сообщение");
                    database.chatDao().updateLastMessage(chat.getId(), text, System.currentTimeMillis());
                    
                    // Увеличиваем счётчик непрочитанных
                    database.chatDao().incrementUnreadCount(chat.getId());
                    
                    handler.post(() -> {
                        // Обновляем UI
                        loadChatsFromDatabase();
                        Toast.makeText(this, "📨 Сообщение от " + from.substring(0, 8), Toast.LENGTH_SHORT).show();
                    });
                } else {
                    // Чат не найден - создаём новый
                    ChatEntity newChat = new ChatEntity(
                        System.currentTimeMillis(),
                        "P2P: " + from.substring(0, 8),
                        "👤",
                        payload.optString("text", "Новое сообщение"),
                        System.currentTimeMillis(),
                        true,
                        1,
                        from
                    );
                    database.chatDao().insert(newChat);
                    
                    handler.post(() -> {
                        loadChatsFromDatabase();
                        Toast.makeText(this, "📨 Новое сообщение!", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onUserFound(String userId, boolean online) {
        // Пользователь найден
    }

    @Override
    public void onTyping(String from) {
        // Собеседник печатает
    }

    @Override
    public void onMessageDelivered(String to, String messageId) {
        // Сообщение доставлено
    }

    @Override
    public void onWebRTCOffer(String from, JSONObject payload) {
        // WebRTC предложение
    }

    @Override
    public void onWebRTCAnswer(String from, JSONObject payload) {
        // WebRTC ответ
    }

    @Override
    public void onWebRTCIceCandidate(String from, JSONObject payload) {
        // WebRTC кандидат
    }

    @Override
    public void onError(String error) {
        updateConnectionStatus("⚠️ Ошибка: " + error);
    }

    @Override
    public void onContactsSynced(List<JSONObject> matches) {
        // Синхронизация контактов (обрабатывается в ContactsActivity)
    }

    @Override
    public void onPhoneRegistered(String userId, boolean alreadyRegistered) {
        // Регистрация номера (обрабатывается в ContactsActivity)
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (p2pClient != null) {
            p2pClient.disconnect();
        }
    }

    /**
     * Получить P2PClient для доступа из других Activity
     */
    public P2PClient getP2PClient() {
        return p2pClient;
    }
}
