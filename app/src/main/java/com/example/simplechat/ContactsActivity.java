package com.example.simplechat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.simplechat.p2p.P2PClient;
import com.example.simplechat.utils.ContactHelper;
import com.example.simplechat.data.AppDatabase;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Экран контактов - синхронизация и отображение контактов
 */
public class ContactsActivity extends AppCompatActivity implements ContactAdapter.OnContactClickListener {

    private static final String TAG = "ContactsActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private RecyclerView contactsRecyclerView;
    private ContactAdapter contactAdapter;
    private TextView statusText;
    private TextView totalContactsText;
    private TextView inAppContactsText;
    private TextView onlineContactsText;
    private TextView registeredPhoneText;
    private Button registerPhoneButton;
    private LinearLayout phoneRegisterCard;
    private LinearLayout statsCard;
    private LinearLayout emptyState;
    private ProgressBar progressBar;
    private ImageButton refreshButton;
    private ImageButton backButton;

    private P2PClient p2pClient;
    private AppDatabase database;
    private ExecutorService executorService;
    private Handler handler = new Handler(Looper.getMainLooper());

    private boolean isRegistered = false;
    private List<ContactAdapter.ContactItem> allContacts = new ArrayList<>();
    private List<ContactAdapter.ContactItem> inAppContacts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_contacts);
            Log.d(TAG, "Layout установлен");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка setContentView", e);
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Инициализация
        database = AppDatabase.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();
        
        // Получаем P2PClient из синглтона
        p2pClient = ChatListActivityInstance.getInstance();
        Log.d(TAG, "P2PClient: " + (p2pClient != null ? "OK" : "NULL"));

        // Если P2PClient не инициализирован, показываем ошибку
        if (p2pClient == null) {
            Toast.makeText(this, "Сначала откройте главный экран", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            // Инициализация view
            initViews();
            Log.d(TAG, "View инициализированы");

            // Настройка кликов
            setupClickListeners();
            Log.d(TAG, "Клики настроены");

            // Настройка адаптера
            setupAdapter();
            Log.d(TAG, "Адаптер настроен");

            // Добавляем слушателя P2PClient
            setupP2PListener();
            Log.d(TAG, "P2P слушатель настроен");

            // Запрос разрешений и синхронизация
            checkPermissionsAndSync();
            Log.d(TAG, "Синхронизация запущена");
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка инициализации", e);
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initViews() {
        contactsRecyclerView = findViewById(R.id.contactsRecyclerView);
        statusText = findViewById(R.id.statusText);
        totalContactsText = findViewById(R.id.totalContactsText);
        inAppContactsText = findViewById(R.id.inAppContactsText);
        onlineContactsText = findViewById(R.id.onlineContactsText);
        registeredPhoneText = findViewById(R.id.registeredPhoneText);
        registerPhoneButton = findViewById(R.id.registerPhoneButton);
        phoneRegisterCard = findViewById(R.id.phoneRegisterCard);
        statsCard = findViewById(R.id.statsCard);
        emptyState = findViewById(R.id.emptyState);
        progressBar = findViewById(R.id.progressBar);
        refreshButton = findViewById(R.id.refreshButton);
        backButton = findViewById(R.id.backButton);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        refreshButton.setOnClickListener(v -> syncContacts());
        registerPhoneButton.setOnClickListener(v -> showRegisterPhoneDialog());
    }

    private void setupAdapter() {
        contactAdapter = new ContactAdapter(this);
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setAdapter(contactAdapter);
    }

    private void setupP2PListener() {
        p2pClient.addEventListener(new P2PClient.P2PEventListener() {
            @Override
            public void onContactsSynced(List<JSONObject> matches) {
                handler.post(() -> {
                    try {
                        Log.d(TAG, "📥 Получено совпадений: " + matches.size());
                        
                        inAppContacts.clear();
                        for (JSONObject match : matches) {
                            ContactAdapter.ContactItem item = ContactAdapter.ContactItem.fromJSON(match);
                            if (item != null) {
                                inAppContacts.add(item);
                                Log.d(TAG, "✅ Контакт в приложении: " + item.name + " (userId: " + item.userId + ")");
                            }
                        }
                        
                        // Объединяем все контакты с найденными userId
                        List<ContactAdapter.ContactItem> mergedContacts = mergeContactsWithMatches(allContacts, inAppContacts);
                        contactAdapter.setContacts(mergedContacts);
                        updateStats(mergedContacts);
                        
                        progressBar.setVisibility(View.GONE);
                        emptyState.setVisibility(mergedContacts.isEmpty() ? View.VISIBLE : View.GONE);
                        statusText.setText("Найдено: " + inAppContacts.size() + " в приложении из " + allContacts.size());
                        
                        if (inAppContacts.isEmpty()) {
                            Toast.makeText(ContactsActivity.this, 
                                "Контакты синхронизированы, но никого из контактов нет в приложении", 
                                Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(ContactsActivity.this, 
                                "🎉 Найдено " + inAppContacts.size() + " контактов в приложении!", 
                                Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка обработки контактов", e);
                        Toast.makeText(ContactsActivity.this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onPhoneRegistered(String userId, boolean alreadyRegistered) {
                handler.post(() -> {
                    if (alreadyRegistered) {
                        registeredPhoneText.setText("✅ Номер уже привязан");
                        registeredPhoneText.setVisibility(View.VISIBLE);
                        registerPhoneButton.setEnabled(false);
                    } else {
                        registeredPhoneText.setText("✅ Номер привязан");
                        registeredPhoneText.setVisibility(View.VISIBLE);
                        registerPhoneButton.setEnabled(true);
                    }
                    isRegistered = true;
                    Toast.makeText(ContactsActivity.this, "Номер привязан", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                handler.post(() -> {
                    Toast.makeText(ContactsActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Ошибка синхронизации");
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
        });
    }

    /**
     * Объединяет все контакты с найденными (у которых есть userId)
     * И ОБНОВЛЯЕТ allContacts с userId
     */
    private List<ContactAdapter.ContactItem> mergeContactsWithMatches(
            List<ContactAdapter.ContactItem> allContacts,
            List<ContactAdapter.ContactItem> inAppContacts) {

        List<ContactAdapter.ContactItem> result = new ArrayList<>();

        // Создаём карту найденных контактов по нормализованному номеру
        java.util.Map<String, ContactAdapter.ContactItem> inAppMap = new java.util.HashMap<>();
        for (ContactAdapter.ContactItem item : inAppContacts) {
            String normalizedPhone = normalizePhone(item.phone);
            inAppMap.put(normalizedPhone, item);
        }

        // Для каждого контакта из телефонной книги
        for (int i = 0; i < allContacts.size(); i++) {
            ContactAdapter.ContactItem contact = allContacts.get(i);
            String normalizedPhone = normalizePhone(contact.phone);
            ContactAdapter.ContactItem inAppContact = inAppMap.get(normalizedPhone);

            if (inAppContact != null) {
                // Нашли совпадение - обновляем allContacts с userId
                allContacts.set(i, inAppContact);
                result.add(inAppContact);
                Log.d(TAG, "🔄 Обновлён контакт: " + contact.name + " userId=" + inAppContact.userId);
            } else {
                // Не нашли - добавляем как обычный контакт (без userId)
                ContactAdapter.ContactItem newItem = new ContactAdapter.ContactItem(
                    contact.name, contact.phone, null, false, false
                );
                result.add(newItem);
            }
        }

        Log.d(TAG, "📊 Итого контактов: " + result.size() + ", в приложении: " + inAppContacts.size());
        return result;
    }

    private void updateStats(List<ContactAdapter.ContactItem> contacts) {
        int total = contacts.size();
        int inApp = 0;
        int online = 0;

        for (ContactAdapter.ContactItem contact : contacts) {
            if (contact.isInApp) {
                inApp++;
                if (contact.isOnline) {
                    online++;
                }
            }
        }

        totalContactsText.setText(String.valueOf(total));
        inAppContactsText.setText(String.valueOf(inApp));
        onlineContactsText.setText(String.valueOf(online));
    }

    private void checkPermissionsAndSync() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_CONTACTS},
                PERMISSION_REQUEST_CODE);
        } else {
            syncContacts();
        }
    }

    private void syncContacts() {
        progressBar.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        statusText.setText("Чтение контактов...");

        executorService.execute(() -> {
            try {
                List<JSONObject> contacts = ContactHelper.getContactsAsJSON(ContactsActivity.this);
                
                handler.post(() -> {
                    allContacts.clear();
                    for (JSONObject contact : contacts) {
                        try {
                            String name = contact.optString("name", "Контакт");
                            String phone = contact.optString("phone", "");
                            allContacts.add(new ContactAdapter.ContactItem(name, phone, null, false, false));
                        } catch (Exception e) {
                            // Пропускаем проблемные контакты
                        }
                    }
                    totalContactsText.setText(String.valueOf(allContacts.size()));
                    statusText.setText("Отправка на сервер...");
                });

                if (p2pClient != null && p2pClient.isConnected()) {
                    p2pClient.syncContacts(contacts);
                } else {
                    handler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        statusText.setText("Нет подключения к серверу");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка синхронизации", e);
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Ошибка: " + e.getMessage());
                });
            }
        });
    }

    private void showRegisterPhoneDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Привязать номер телефона");
        builder.setMessage("Введите ваш номер телефона для привязки к аккаунту");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("+7 999 123-45-67");
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        builder.setView(input);

        builder.setPositiveButton("Привязать", (dialog, which) -> {
            String phone = input.getText().toString().trim();
            if (!phone.isEmpty() && p2pClient != null) {
                p2pClient.registerPhone(phone);
            } else {
                Toast.makeText(this, "Введите номер", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("8") && digits.length() == 11) {
            return "7" + digits.substring(1);
        }
        return digits;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                syncContacts();
            } else {
                Toast.makeText(this, "Разрешение на чтение контактов обязательно", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void onContactClick(ContactAdapter.ContactItem contact) {
        if (contact.isInApp) {
            openChat(contact);
        } else {
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
        new AlertDialog.Builder(this)
            .setTitle("Пригласить в ЕДИНИЦУ")
            .setMessage(contact.name + " ещё не в приложении. Пригласить?")
            .setPositiveButton("SMS", (dialog, which) -> sendInviteSMS(contact.phone))
            .setNegativeButton("Поделиться", (dialog, which) -> shareInviteLink())
            .setNeutralButton("Отмена", null)
            .show();
    }

    private void sendInviteSMS(String phone) {
        String message = "Привет! Скачай мессенджер ЕДИНИЦА - приватное P2P общение без серверов: https://disk.yandex.ru/d/nSHjVI06gi_xzg";
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.SEND_SMS},
                PERMISSION_REQUEST_CODE + 1);
            return;
        }

        Intent smsIntent = new Intent(android.content.Intent.ACTION_SENDTO);
        smsIntent.setData(android.net.Uri.parse("smsto:" + phone));
        smsIntent.putExtra("sms_body", message);
        
        if (smsIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(smsIntent);
        }
    }

    private void shareInviteLink() {
        String shareText = "Привет! Скачай мессенджер ЕДИНИЦА - приватное P2P общение без серверов: https://disk.yandex.ru/d/nSHjVI06gi_xzg";
        
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
