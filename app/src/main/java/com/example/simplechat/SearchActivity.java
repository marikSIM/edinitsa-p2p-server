package com.example.simplechat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.simplechat.p2p.P2PClient;
import com.example.simplechat.utils.ContactHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Универсальный поиск (как в Telegram)
 * Поиск по: контактам, чатам, сообщениям, userId
 */
public class SearchActivity extends AppCompatActivity implements ContactAdapter.OnContactClickListener {

    private EditText searchEditText;
    private ImageButton closeSearchButton;
    private RecyclerView searchRecyclerView;
    private ProgressBar progressBar;
    private TextView emptyStateText;
    private LinearLayout recentSearchesLayout;

    private ContactAdapter searchAdapter;
    private List<ContactAdapter.ContactItem> searchResults = new ArrayList<>();
    private List<ContactAdapter.ContactItem> allContacts = new ArrayList<>();

    private P2PClient p2pClient;
    private ExecutorService executorService;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    private List<ContactAdapter.ContactItem> inAppContacts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        executorService = Executors.newSingleThreadExecutor();
        p2pClient = ChatListActivityInstance.getInstance();

        initViews();
        setupAdapter();
        setupListeners();
        setupP2PListener(); // Добавляем слушателя P2P
        loadContacts();
    }

    private void initViews() {
        searchEditText = findViewById(R.id.searchEditText);
        closeSearchButton = findViewById(R.id.closeSearchButton);
        searchRecyclerView = findViewById(R.id.searchRecyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyStateText = findViewById(R.id.emptyStateText);
        recentSearchesLayout = findViewById(R.id.recentSearchesLayout);
    }

    private void setupAdapter() {
        searchAdapter = new ContactAdapter(this);
        searchRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        searchRecyclerView.setAdapter(searchAdapter);
    }

    private void setupListeners() {
        closeSearchButton.setOnClickListener(v -> finish());

        searchEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterResults(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }
    
    private void setupP2PListener() {
        p2pClient.addEventListener(new P2PClient.P2PEventListener() {
            @Override
            public void onContactsSynced(List<JSONObject> matches) {
                handler.post(() -> {
                    try {
                        inAppContacts.clear();
                        for (JSONObject match : matches) {
                            ContactAdapter.ContactItem item = ContactAdapter.ContactItem.fromJSON(match);
                            if (item != null) {
                                inAppContacts.add(item);
                            }
                        }
                        
                        // Обновляем все контакты, добавляя userId найденным
                        updateContactsMatches();
                        
                        progressBar.setVisibility(View.GONE);
                        filterResults("");
                        
                        if (inAppContacts.isEmpty()) {
                            emptyStateText.setText("Контакты синхронизированы, но никого нет в приложении");
                        } else {
                            emptyStateText.setText("Найдено " + inAppContacts.size() + " контактов в приложении!");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            
            @Override public void onConnected(String userId) {}
            @Override public void onDisconnected() {}
            @Override public void onMessageReceived(String from, JSONObject payload) {}
            @Override public void onUserFound(String userId, boolean online) {}
            @Override public void onTyping(String from) {}
            @Override public void onMessageDelivered(String to, String messageId) {}
            @Override public void onWebRTCOffer(String from, JSONObject payload) {}
            @Override public void onWebRTCAnswer(String from, JSONObject payload) {}
            @Override public void onWebRTCIceCandidate(String from, JSONObject payload) {}
            @Override public void onError(String error) {}
            @Override public void onPhoneRegistered(String userId, boolean alreadyRegistered) {}
        });
    }
    
    /**
     * Обновляет allContacts, добавляя userId из inAppContacts
     */
    private void updateContactsMatches() {
        // Создаём карту найденных контактов по нормализованному номеру
        java.util.Map<String, ContactAdapter.ContactItem> inAppMap = new java.util.HashMap<>();
        for (ContactAdapter.ContactItem item : inAppContacts) {
            String normalizedPhone = normalizePhone(item.phone);
            inAppMap.put(normalizedPhone, item);
        }
        
        // Обновляем все контакты
        for (int i = 0; i < allContacts.size(); i++) {
            ContactAdapter.ContactItem contact = allContacts.get(i);
            String normalizedPhone = normalizePhone(contact.phone);
            ContactAdapter.ContactItem inAppContact = inAppMap.get(normalizedPhone);
            
            if (inAppContact != null) {
                // Заменяем на контакт с userId
                allContacts.set(i, inAppContact);
            }
        }
    }
    
    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("8") && digits.length() == 11) {
            return "7" + digits.substring(1);
        }
        return digits;
    }

    private void loadContacts() {
        progressBar.setVisibility(View.VISIBLE);
        emptyStateText.setText("Чтение контактов...");

        executorService.execute(() -> {
            try {
                List<JSONObject> contacts = ContactHelper.getContactsAsJSON(this);

                handler.post(() -> {
                    allContacts.clear();
                    for (JSONObject contact : contacts) {
                        try {
                            String name = contact.optString("name", "Контакт");
                            String phone = contact.optString("phone", "");
                            allContacts.add(new ContactAdapter.ContactItem(name, phone, null, false, false));
                        } catch (Exception e) {
                            // Пропускаем
                        }
                    }

                    emptyStateText.setText("Отправка на сервер...");
                    
                    // Проверяем кто в приложении
                    if (p2pClient != null && p2pClient.isConnected()) {
                        p2pClient.syncContacts(contacts);
                        // filterResults вызовется после получения matches в setupP2PListener
                    } else {
                        progressBar.setVisibility(View.GONE);
                        emptyStateText.setText("Нет подключения к серверу");
                        filterResults("");
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    emptyStateText.setText("Ошибка загрузки контактов");
                });
            }
        });
    }

    private void filterResults(String query) {
        searchResults.clear();
        
        if (query.isEmpty()) {
            // Показываем все контакты
            searchResults.addAll(allContacts);
        } else {
            // Фильтруем по имени и номеру
            String lowerQuery = query.toLowerCase();
            for (ContactAdapter.ContactItem contact : allContacts) {
                if (contact.name.toLowerCase().contains(lowerQuery) ||
                    contact.phone.contains(lowerQuery)) {
                    searchResults.add(contact);
                }
            }
        }

        searchAdapter.setContacts(searchResults);
        
        if (searchResults.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            emptyStateText.setText("Ничего не найдено");
        } else {
            emptyStateText.setVisibility(View.GONE);
        }
    }

    @Override
    public void onContactClick(ContactAdapter.ContactItem contact) {
        if (contact.isInApp) {
            // Открываем чат
            openChat(contact);
        } else {
            // Предлагаем пригласить
            showInviteDialog(contact);
        }
    }

    @Override
    public void onMessageClick(ContactAdapter.ContactItem contact) {
        if (contact.isInApp) {
            openChat(contact);
        } else {
            showInviteDialog(contact);
        }
    }

    private void openChat(ContactAdapter.ContactItem contact) {
        if (contact.userId == null || contact.userId.isEmpty()) {
            Toast.makeText(this, "Пользователь не найден в приложении", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Создаём или находим чат в базе данных
        executorService.execute(() -> {
            try {
                com.example.simplechat.data.AppDatabase database = 
                    com.example.simplechat.data.AppDatabase.getInstance(this);
                
                // Проверяем, есть ли уже чат с этим userId
                com.example.simplechat.data.ChatEntity existingChat = 
                    database.chatDao().getChatByFriendUserIdSync(contact.userId);
                
                long chatId;
                if (existingChat != null) {
                    chatId = existingChat.getId();
                } else {
                    // Создаём новый чат
                    com.example.simplechat.data.ChatEntity newChat = 
                        new com.example.simplechat.data.ChatEntity(
                            System.currentTimeMillis(),
                            contact.name,
                            "👤",
                            "Напишите сообщение...",
                            System.currentTimeMillis(),
                            contact.isOnline,
                            0,
                            contact.userId
                        );
                    database.chatDao().insert(newChat);
                    chatId = newChat.getId();
                }
                
                // Открываем чат
                handler.post(() -> {
                    Intent intent = new Intent(this, P2PChatActivity.class);
                    intent.putExtra("chat_id", chatId);
                    intent.putExtra("friend_user_id", contact.userId);
                    intent.putExtra("chat_name", contact.name);
                    intent.putExtra("is_online", contact.isOnline);
                    startActivity(intent);
                    finish();
                });
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> 
                    Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void showInviteDialog(ContactAdapter.ContactItem contact) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Пригласить в ЕДИНИЦУ")
            .setMessage(contact.name + " ещё не в приложении. Пригласить?")
            .setPositiveButton("SMS", (dialog, which) -> sendInviteSMS(contact.phone))
            .setNegativeButton("Поделиться", (dialog, which) -> shareInviteLink())
            .setNeutralButton("Отмена", null)
            .show();
    }

    private void sendInviteSMS(String phone) {
        String message = "Привет! Скачай мессенджер ЕДИНИЦА - приватное P2P общение: https://disk.yandex.ru/d/nSHjVI06gi_xzg";
        
        Intent smsIntent = new Intent(android.content.Intent.ACTION_SENDTO);
        smsIntent.setData(android.net.Uri.parse("smsto:" + phone));
        smsIntent.putExtra("sms_body", message);
        
        if (smsIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(smsIntent);
        }
    }

    private void shareInviteLink() {
        String shareText = "Привет! Скачай мессенджер ЕДИНИЦА - приватное P2P общение: https://disk.yandex.ru/d/nSHjVI06gi_xzg";
        
        Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareText);
        
        startActivity(Intent.createChooser(shareIntent, "Поделиться через"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        executorService.shutdown();
    }
}
