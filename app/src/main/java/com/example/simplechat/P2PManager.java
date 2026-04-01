package com.example.simplechat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.simplechat.p2p.P2PClient;

import org.json.JSONObject;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Единый менеджер P2P соединений для всего приложения
 * 
 * Проблемы которые решает:
 * - P2PClient создавался в каждой Activity → утечки памяти
 * - userId генерировался заново → пропадала привязка номера
 * - Множественные подключения к серверу
 * 
 * Решение:
 * - Один экземпляр на всё приложение
 * - userId сохраняется в SharedPreferences навсегда
 * - Одно подключение к серверу
 */
public class P2PManager {
    
    private static final String TAG = "P2PManager";
    
    // Singleton instance
    private static P2PManager instance;
    
    // P2P Client
    private P2PClient client;
    
    // Application context (не Activity!)
    private final Context appContext;
    
    // SharedPreferences для сохранения userId
    private final SharedPreferences prefs;
    
    // Ключи SharedPreferences
    private static final String PREFS_NAME = "p2p_prefs";
    private static final String KEY_USER_ID = "user_id";
    
    // Слушатели событий (CopyOnWriteArrayList для потокобезопасности)
    private final List<P2PEventListener> listeners = new CopyOnWriteArrayList<>();
    
    // Handler для callbacks в UI поток
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    // Флаг подключения
    private boolean isConnected = false;
    
    /**
     * Интерфейс для слушателей событий
     */
    public interface P2PEventListener {
        void onConnected(String userId);
        void onDisconnected();
        void onMessageReceived(String from, JSONObject payload);
        void onUserFound(String userId, boolean online);
        void onTyping(String from);
        void onMessageDelivered(String to, String messageId);
        void onWebRTCOffer(String from, JSONObject payload);
        void onWebRTCAnswer(String from, JSONObject payload);
        void onWebRTCIceCandidate(String from, JSONObject payload);
        void onError(String error);
        void onContactsSynced(List<JSONObject> matches);
        void onPhoneRegistered(String userId, boolean alreadyRegistered);
    }
    
    /**
     * Приватный конструктор (Singleton)
     */
    private P2PManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Получить единый экземпляр P2PManager
     */
    public static synchronized P2PManager getInstance(Context context) {
        if (instance == null) {
            instance = new P2PManager(context);
        }
        return instance;
    }
    
    /**
     * Получить или создать userId
     * 
     * @return userId из SharedPreferences или новый сгенерированный
     */
    public String getUserId() {
        String userId = prefs.getString(KEY_USER_ID, null);
        
        if (userId == null) {
            // Генерируем новый и сохраняем НАВСЕГДА
            userId = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_USER_ID, userId).apply();
            Log.d(TAG, "✅ Сгенерирован новый userId: " + userId);
        } else {
            Log.d(TAG, "✅ Используем существующий userId: " + userId.substring(0, 8));
        }
        
        return userId;
    }
    
    /**
     * Подключиться к серверу
     * 
     * @param listener Слушатель событий для текущей Activity
     */
    public void connect(P2PEventListener listener) {
        // Если уже подключено — просто добавляем слушателя
        if (client != null && isConnected) {
            Log.d(TAG, "Уже подключено, добавляем слушателя");
            if (listener != null) addListener(listener);
            return;
        }
        
        // Получаем userId (из памяти или генерируем новый)
        String userId = getUserId();

        // Создаём новый P2PClient
        client = new P2PClient();

        // Добавляем внутреннего слушателя для рассылки событий
        client.addEventListener(new P2PClient.P2PEventListener() {
            @Override
            public void onConnected(String newUserId) {
                isConnected = true;
                
                // Если сервер вернул другой userId — сохраняем его
                if (!newUserId.equals(userId)) {
                    prefs.edit().putString(KEY_USER_ID, newUserId).apply();
                    Log.d(TAG, "Сервер вернул новый userId: " + newUserId);
                }
                
                Log.d(TAG, "✅ Подключено к серверу");
                
                // Рассылаем всем слушателям
                for (P2PEventListener l : listeners) {
                    handler.post(() -> l.onConnected(newUserId));
                }
            }
            
            @Override
            public void onDisconnected() {
                isConnected = false;
                Log.d(TAG, "❌ Отключено от сервера");
                
                for (P2PEventListener l : listeners) {
                    handler.post(() -> l.onDisconnected());
                }
            }
            
            @Override
            public void onMessageReceived(String from, JSONObject payload) {
                Log.d(TAG, "📨 Сообщение от: " + from.substring(0, 8));
                
                for (P2PEventListener l : listeners) {
                    handler.post(() -> l.onMessageReceived(from, payload));
                }
            }
            
            @Override
            public void onUserFound(String userId, boolean online) {
                for (P2PEventListener l : listeners) {
                    handler.post(() -> l.onUserFound(userId, online));
                }
            }
            
            @Override
            public void onTyping(String from) {
                for (P2PEventListener l : listeners) {
                    handler.post(() -> l.onTyping(from));
                }
            }
            
            @Override
            public void onMessageDelivered(String to, String messageId) {
                for (P2PEventListener l : listeners) {
                    handler.post(() -> l.onMessageDelivered(to, messageId));
                }
            }
            
            @Override
            public void onWebRTCOffer(String from, JSONObject payload) {
                for (P2PEventListener l : listeners) {
                    handler.post(() -> l.onWebRTCOffer(from, payload));
                }
            }
            
            @Override
            public void onWebRTCAnswer(String from, JSONObject payload) {
                for (P2PEventListener l : listeners) {
                    handler.post(() -> l.onWebRTCAnswer(from, payload));
                }
            }
            
            @Override
            public void onWebRTCIceCandidate(String from, JSONObject payload) {
                for (P2PEventListener l : listeners) {
                    handler.post(() -> l.onWebRTCIceCandidate(from, payload));
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Ошибка: " + error);
                
                for (P2PEventListener l : listeners) {
                    handler.post(() -> l.onError(error));
                }
            }
            
            @Override
            public void onContactsSynced(List<JSONObject> matches) {
                for (P2PEventListener l : listeners) {
                    handler.post(() -> l.onContactsSynced(matches));
                }
            }
            
            @Override
            public void onPhoneRegistered(String userId, boolean alreadyRegistered) {
                for (P2PEventListener l : listeners) {
                    handler.post(() -> l.onPhoneRegistered(userId, alreadyRegistered));
                }
            }
        });
        
        // Подключаемся к серверу
        client.connect();
        
        // Добавляем внешнего слушателя
        if (listener != null) addListener(listener);
        
        Log.d(TAG, "🚀 Подключение запущено");
    }
    
    /**
     * Отключиться от сервера
     * 
     * ВАЖНО: Вызывать только при полном выходе из приложения!
     */
    public void disconnect() {
        if (client != null) {
            client.disconnect();
            client = null;
            isConnected = false;
            listeners.clear();
            Log.d(TAG, "👋 Отключено от сервера");
        }
    }
    
    /**
     * Добавить слушателя событий
     */
    public void addListener(P2PEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            Log.d(TAG, "👂 Добавлен слушатель, всего: " + listeners.size());
        }
    }
    
    /**
     * Удалить слушателя событий
     */
    public void removeListener(P2PEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
            Log.d(TAG, "👂 Удалён слушатель, всего: " + listeners.size());
        }
    }
    
    /**
     * Отправить сообщение
     */
    public void sendMessage(String toUserId, JSONObject payload) {
        if (client != null && isConnected) {
            client.sendMessage(toUserId, payload);
        } else {
            Log.w(TAG, "⚠️ Нет соединения для отправки сообщения");
        }
    }
    
    /**
     * Найти пользователя
     */
    public void findUser(String targetId) {
        if (client != null && isConnected) {
            client.findUser(targetId);
        }
    }
    
    /**
     * Отправить статус "печатает"
     */
    public void sendTyping(String toUserId) {
        if (client != null && isConnected) {
            client.sendTyping(toUserId);
        }
    }
    
    /**
     * Синхронизировать контакты
     */
    public void syncContacts(List<JSONObject> contacts) {
        if (client != null && isConnected) {
            client.syncContacts(contacts);
        }
    }
    
    /**
     * Зарегистрировать номер телефона
     */
    public void registerPhone(String phone) {
        if (client != null && isConnected) {
            client.registerPhone(phone);
        }
    }
    
    /**
     * Проверка подключения
     */
    public boolean isConnected() {
        return isConnected && client != null;
    }
}
