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

import com.example.simplechat.P2PManager;
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

    private P2PManager p2pManager;
    private ExecutorService executorService;
    private Handler handler = new Handler(Looper.getMainLooper());

    private List<ContactAdapter.ContactItem> inAppContacts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        executorService = Executors.newSingleThreadExecutor();
        p2pManager = P2PManager.getInstance(this);

        initViews();
        setupAdapter();
        setupListeners();
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
                    if (p2pManager != null && p2pManager.isConnected()) {
                        p2pManager.syncContacts(contacts);
                        // filterResults вызовется после получения matches
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
                    // Получаем аватар пользователя из БД
                    String userAvatar = "👤"; // По умолчанию
                    com.example.simplechat.data.UserProfileEntity userProfile =
                        database.userProfileDao().getProfile();
                    if (userProfile != null && userProfile.getAvatar() != null) {
                        userAvatar = userProfile.getAvatar();
                    }
                    
                    // Создаём новый чат
                    com.example.simplechat.data.ChatEntity newChat =
                        new com.example.simplechat.data.ChatEntity(
                            System.currentTimeMillis(),
                            contact.name,
                            userAvatar, // Аватар из профиля пользователя
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
