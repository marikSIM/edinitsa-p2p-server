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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        executorService = Executors.newSingleThreadExecutor();
        p2pClient = ChatListActivityInstance.getInstance();

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

    private void loadContacts() {
        progressBar.setVisibility(View.VISIBLE);

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

                    // Проверяем кто в приложении
                    if (p2pClient != null && p2pClient.isConnected()) {
                        p2pClient.syncContacts(contacts);
                    }

                    progressBar.setVisibility(View.GONE);
                    filterResults("");
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
        Intent intent = new Intent(this, P2PChatActivity.class);
        intent.putExtra("user_id", contact.userId);
        intent.putExtra("chat_name", contact.name);
        intent.putExtra("is_online", contact.isOnline);
        startActivity(intent);
        finish();
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
        String message = "Привет! Скачай мессенджер ЕДИНИЦА - приватное P2P общение: https://edinitsa.app";
        
        Intent smsIntent = new Intent(android.content.Intent.ACTION_SENDTO);
        smsIntent.setData(android.net.Uri.parse("smsto:" + phone));
        smsIntent.putExtra("sms_body", message);
        
        if (smsIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(smsIntent);
        }
    }

    private void shareInviteLink() {
        String shareText = "Привет! Скачай мессенджер ЕДИНИЦА - приватное P2P общение: https://edinitsa.app";
        
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
