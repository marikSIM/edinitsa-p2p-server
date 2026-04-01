package com.example.simplechat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.simplechat.data.AppDatabase;
import com.example.simplechat.data.ChatEntity;
import com.example.simplechat.data.UserProfileEntity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Главный экран мессенджера (как в Telegram)
 *
 * Изменения:
 * ✅ Убраны боты при первом запуске
 * ✅ P2PManager вместо прямого создания P2PClient
 * ✅ BottomNavigationView вместо Drawer
 * ✅ userId сохраняется навсегда в SharedPreferences
 */
public class ChatListActivity extends AppCompatActivity
    implements ChatAdapter.OnChatClickListener {

    private RecyclerView chatsRecyclerView;
    private ChatAdapter chatAdapter;
    private List<Chat> chatList;
    private AppDatabase database;
    private ExecutorService executorService;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // P2P Manager (единый на всё приложение)
    private P2PManager p2pManager;
    private P2PManager.P2PEventListener p2pListener;
    private TextView connectionStatusText;

    // Поиск
    private ImageButton searchButton;
    private ImageButton closeSearchButton;
    private LinearLayout searchBar;
    private EditText searchEditText;
    private List<Chat> fullChatList;

    // Navigation
    private BottomNavigationView bottomNavigation;
    private Toolbar toolbar;

    // UI
    private TextView emptyStateView;
    private FloatingActionButton newChatButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        // Инициализация БД
        database = AppDatabase.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();

        // Очищаем базу от старых ботов при первом запуске новой версии
        clearOldBotChats();

        // Инициализация UI
        initViews();
        setupNavigation();
        setupRecyclerView();

        // Инициализация P2P
        initP2P();

        // Загрузка чатов (без ботов!)
        loadChatsFromDatabase();
    }

    /**
     * Очистка старых ботов из базы данных
     * Вызывается один раз при обновлении
     */
    private void clearOldBotChats() {
        executorService.execute(() -> {
            // Проверяем есть ли боты (имена из старого списка)
            List<String> botNames = java.util.Arrays.asList(
                "Алексей", "Мария", "Дмитрий", "Елена", "Андрей",
                "Ольга", "Сергей", "Наталья", "Михаил", "Екатерина"
            );
            
            List<ChatEntity> chats = database.chatDao().getAllChatsSync();
            boolean hasBots = false;
            
            for (ChatEntity chat : chats) {
                if (botNames.contains(chat.getName())) {
                    hasBots = true;
                    break;
                }
            }
            
            // Если есть боты — очищаем всю базу
            if (hasBots) {
                database.clearAllData();
                Log.d("ChatListActivity", "✅ Старые боты удалены");
            }
        });
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        chatsRecyclerView = findViewById(R.id.chatsRecyclerView);
        emptyStateView = findViewById(R.id.emptyStateView);
        newChatButton = findViewById(R.id.newChatButton);
        connectionStatusText = findViewById(R.id.connectionStatusText);
        
        // Поиск
        searchButton = findViewById(R.id.searchButton);
        closeSearchButton = findViewById(R.id.closeSearchButton);
        searchBar = findViewById(R.id.searchBar);
        searchEditText = findViewById(R.id.searchEditText);
    }

    private void setupNavigation() {
        // Toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Кнопка меню
        ImageButton menuButton = findViewById(R.id.menuButton);
        menuButton.setOnClickListener(v -> showMainMenu());

        // Нижнее меню (как в Telegram)
        bottomNavigation.setSelectedItemId(R.id.nav_chats);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            
            if (id == R.id.nav_chats) {
                // Уже на экране чатов
                return true;
            } else if (id == R.id.nav_contacts) {
                // Открываем контакты
                startActivity(new Intent(this, ContactsActivity.class));
                return true;
            } else if (id == R.id.nav_groups) {
                // Открываем группы
                startActivity(new Intent(this, MyGroupsActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                // Открываем настройки
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            
            return false;
        });
    }

    /**
     * Главное меню (как в Telegram)
     */
    private void showMainMenu() {
        String[] options = {
            "👤 Профиль",
            "📞 Контакты",
            "👥 Группы",
            "⭐ Избранное",
            "⚙️ Настройки",
            "🎧 Поддержка",
            "🗑️ Очистить историю"
        };
        
        new AlertDialog.Builder(this)
            .setTitle("Меню")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // Профиль
                    openProfile();
                } else if (which == 1) {
                    // Контакты
                    startActivity(new Intent(this, ContactsActivity.class));
                } else if (which == 2) {
                    // Группы
                    startActivity(new Intent(this, MyGroupsActivity.class));
                } else if (which == 3) {
                    // Избранное
                    Toast.makeText(this, "Избранное (в разработке)", Toast.LENGTH_SHORT).show();
                } else if (which == 4) {
                    // Настройки
                    startActivity(new Intent(this, SettingsActivity.class));
                } else if (which == 5) {
                    // Поддержка
                    startActivity(new Intent(this, SupportChatActivity.class));
                } else if (which == 6) {
                    // Очистить историю
                    showClearHistoryDialog();
                }
            })
            .show();
    }

    private void openProfile() {
        Intent intent = new Intent(this, ProfileActivity.class);
        startActivity(intent);
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

    private void setupRecyclerView() {
        chatList = new ArrayList<>();
        fullChatList = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatList, this);

        chatsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatsRecyclerView.setAdapter(chatAdapter);

        // Поиск
        setupSearch();
    }

    private void setupSearch() {
        if (searchButton == null || closeSearchButton == null) return;

        searchButton.setOnClickListener(v -> {
            searchBar.setVisibility(View.VISIBLE);
            searchEditText.requestFocus();
            searchEditText.setText("");
            filterChats("");
        });

        closeSearchButton.setOnClickListener(v -> {
            searchBar.setVisibility(View.GONE);
            searchEditText.setText("");
            searchEditText.clearFocus();
            // Показываем все чаты
            chatList.clear();
            chatList.addAll(fullChatList);
            chatAdapter.notifyDataSetChanged();
        });

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

    private void initP2P() {
        // Получаем единый экземпляр P2PManager
        p2pManager = P2PManager.getInstance(this);

        // Создаём слушателя для ЭТОЙ активности
        p2pListener = new P2PManager.P2PEventListener() {
            @Override
            public void onConnected(String userId) {
                handler.post(() -> {
                    connectionStatusText.setText("✅ Подключено: " + userId.substring(0, 8));
                    connectionStatusText.setVisibility(View.VISIBLE);
                    
                    // Скрываем статус через 3 секунды
                    handler.postDelayed(() -> {
                        connectionStatusText.setVisibility(View.GONE);
                    }, 3000);
                });
            }

            @Override
            public void onMessageReceived(String from, JSONObject payload) {
                handleIncomingMessage(from, payload);
            }

            @Override
            public void onDisconnected() {
                handler.post(() -> {
                    connectionStatusText.setText("❌ Отключено");
                    connectionStatusText.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onError(String error) {
                handler.post(() -> {
                    Toast.makeText(ChatListActivity.this, 
                        "P2P ошибка: " + error, Toast.LENGTH_SHORT).show();
                });
            }

            // Остальные методы не используем
            @Override public void onUserFound(String userId, boolean online) {}
            @Override public void onTyping(String from) {}
            @Override public void onMessageDelivered(String to, String messageId) {}
            @Override public void onWebRTCOffer(String from, JSONObject payload) {}
            @Override public void onWebRTCAnswer(String from, JSONObject payload) {}
            @Override public void onWebRTCIceCandidate(String from, JSONObject payload) {}
            @Override public void onContactsSynced(List<JSONObject> matches) {}
            @Override public void onPhoneRegistered(String userId, boolean alreadyRegistered) {}
        };

        // Подключаемся
        p2pManager.connect(p2pListener);
    }

    private void handleIncomingMessage(String from, JSONObject payload) {
        executorService.execute(() -> {
            try {
                // Ищем чат с этим userId
                ChatEntity chat = database.chatDao().getChatByFriendUserIdSync(from);
                
                if (chat != null) {
                    // Обновляем последнее сообщение
                    String text = payload.optString("text", "Новое сообщение");
                    database.chatDao().updateLastMessage(chat.getId(), text, System.currentTimeMillis());
                    database.chatDao().incrementUnreadCount(chat.getId());

                    handler.post(() -> {
                        loadChatsFromDatabase();
                        Toast.makeText(this, "📨 Сообщение от " + from.substring(0, 8), 
                            Toast.LENGTH_SHORT).show();
                    });
                } else {
                    // Создаём новый чат
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

    private void loadChatsFromDatabase() {
        executorService.execute(() -> {
            List<ChatEntity> entities = database.chatDao().getAllChatsSync();

            handler.post(() -> {
                if (entities.isEmpty()) {
                    // ✅ ПОКАЗЫВАЕМ ПУСТОЙ ЭКРАН (без ботов!)
                    emptyStateView.setVisibility(View.VISIBLE);
                    chatsRecyclerView.setVisibility(View.GONE);
                } else {
                    // Показываем чаты
                    emptyStateView.setVisibility(View.GONE);
                    chatsRecyclerView.setVisibility(View.VISIBLE);

                    chatList.clear();
                    fullChatList.clear();
                    
                    for (ChatEntity entity : entities) {
                        Chat chat = new Chat(entity);
                        chatList.add(chat);
                        fullChatList.add(chat);
                    }
                    
                    chatAdapter.notifyDataSetChanged();
                }
            });
        });
    }

    @Override
    public void onChatClick(Chat chat) {
        // Сбрасываем счётчик непрочитанных
        executorService.execute(() -> {
            database.chatDao().resetUnreadCount(chat.getId());
        });

        // Переход к экрану чата
        Intent intent = new Intent(this, P2PChatActivity.class);
        intent.putExtra("chat_id", chat.getId());
        intent.putExtra("chat_name", chat.getName());
        intent.putExtra("chat_avatar", chat.getAvatar());
        intent.putExtra("chat_online", chat.isOnline());
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Обновляем список чатов при возврате
        loadChatsFromDatabase();
        // Регистрируем слушателя
        if (p2pListener != null) {
            p2pManager.addListener(p2pListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Отписываемся когда экран не активен
        if (p2pListener != null) {
            p2pManager.removeListener(p2pListener);
        }
    }

    @Override
    protected void onDestroy() {
        // ❌ НЕ вызываем disconnect() — менеджер живёт дольше активности!
        super.onDestroy();
    }
}
