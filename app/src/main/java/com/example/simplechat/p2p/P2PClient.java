package com.example.simplechat.p2p;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * P2P Клиент для соединения с сервером на Render.com (HTTP)
 *
 * Обрабатывает:
 * - Подключение к серверу через HTTP
 * - Отправку/получение сообщений
 * - WebRTC сигнализацию (offer/answer/ICE)
 * - Очередь офлайн-сообщений
 * - Heartbeat (поддержание соединения)
 */
public class P2PClient {

    private static final String TAG = "P2PClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // URL сервера сигнализации
    // ДЛЯ РАЗРАБОТКИ - локальный сервер (HTTP):
    private static final String SERVER_URL = "http://10.0.2.2:3000";

    // ДЛЯ ПРОДАКШЕНА - Render.com (HTTPS):
    // private static final String SERVER_URL = "https://edinitsa-p2p-server.onrender.com";

    private final OkHttpClient httpClient;
    private final Handler handler;
    private String userId;
    private boolean isConnected = false;

    // Слушатели событий
    private final List<P2PEventListener> eventListeners = new CopyOnWriteArrayList<>();

    // Heartbeat интервал (10 секунд для тестов)
    private static final long HEARTBEAT_INTERVAL = 10 * 1000; // 10 секунд
    private Runnable heartbeatRunnable;

    /**
     * Интерфейс для получения событий
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

    public P2PClient() {
        this.httpClient = createUnsafeOkHttpClient();
        this.handler = new Handler(Looper.getMainLooper());
    }

    /**
     * Создаёт OkHttpClient который игнорирует SSL ошибки
     * ТОЛЬКО ДЛЯ РАЗРАБОТКИ! Не использовать в продакшене.
     */
    private static OkHttpClient createUnsafeOkHttpClient() {
        try {
            // Создаём TrustManager который не проверяет сертификаты
            final TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
                        throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                        throws CertificateException {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
            };

            // Устанавливаем TrustManager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Создаём SSLSocketFactory
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            // Создаём OkHttpClient
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
            builder.connectTimeout(30, TimeUnit.SECONDS);
            builder.readTimeout(30, TimeUnit.SECONDS);
            builder.writeTimeout(30, TimeUnit.SECONDS);
            builder.retryOnConnectionFailure(true);

            return builder.build();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Подключиться к серверу
     */
    public void connect() {
        if (isConnected) {
            Log.d(TAG, "Уже подключено");
            return;
        }

        Log.d(TAG, "Подключение к серверу: " + SERVER_URL);

        new Thread(() -> {
            try {
                // Регистрация пользователя
                Request request = new Request.Builder()
                    .url(SERVER_URL + "/register")
                    .post(RequestBody.create("{}", JSON))
                    .build();

                Response response = httpClient.newCall(request).execute();
                String responseBody = response.body().string();

                if (response.isSuccessful()) {
                    JSONObject data = new JSONObject(responseBody);
                    userId = data.getString("userId");
                    isConnected = true;

                    Log.d(TAG, "Подключено, userId: " + userId);

                    handler.post(() -> {
                        for (P2PEventListener listener : eventListeners) {
                            listener.onConnected(userId);
                        }
                    });

                    // Запускаем heartbeat
                    startHeartbeat();
                } else {
                    Log.e(TAG, "Ошибка регистрации: " + responseBody);
                    handler.post(() -> {
                        for (P2PEventListener listener : eventListeners) {
                            listener.onError("Ошибка регистрации: " + responseBody);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка подключения", e);
                handler.post(() -> {
                    for (P2PEventListener listener : eventListeners) {
                        listener.onError(e.getMessage());
                    }
                });
                reconnect();
            }
        }).start();
    }

    /**
     * Отключиться от сервера
     */
    public void disconnect() {
        stopHeartbeat();
        if (isConnected) {
            sendOnlineStatus(false);
        }
        isConnected = false;
        Log.d(TAG, "Отключено");
    }

    /**
     * Добавить слушателя событий
     */
    public void addEventListener(P2PEventListener listener) {
        eventListeners.add(listener);
    }

    /**
     * Удалить слушателя событий
     */
    public void removeEventListener(P2PEventListener listener) {
        eventListeners.remove(listener);
    }

    // ============================================
    // Отправка сообщений
    // ============================================

    /**
     * Отправить сообщение пользователю
     */
    public void sendMessage(String toUserId, JSONObject payload) {
        if (!isConnected || userId == null) {
            Log.w(TAG, "Нет соединения");
            return;
        }

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("fromId", userId);
                json.put("to", toUserId);
                json.put("payload", payload);

                Request request = new Request.Builder()
                    .url(SERVER_URL + "/send")
                    .post(RequestBody.create(json.toString(), JSON))
                    .build();

                Response response = httpClient.newCall(request).execute();
                String responseBody = response.body().string();

                Log.d(TAG, "Сообщение отправлено: " + toUserId);
            } catch (IOException e) {
                Log.e(TAG, "Ошибка отправки сообщения", e);
            } catch (JSONException e) {
                Log.e(TAG, "Ошибка создания JSON", e);
            }
        }).start();
    }

    /**
     * Найти пользователя
     */
    public void findUser(String targetId) {
        if (!isConnected || userId == null) {
            Log.w(TAG, "Нет соединения");
            return;
        }

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("fromId", userId);
                json.put("targetId", targetId);

                Request request = new Request.Builder()
                    .url(SERVER_URL + "/find-user")
                    .post(RequestBody.create(json.toString(), JSON))
                    .build();

                Response response = httpClient.newCall(request).execute();
                String responseBody = response.body().string();

                JSONObject data = new JSONObject(responseBody);
                boolean online = data.getBoolean("online");

                handler.post(() -> {
                    for (P2PEventListener listener : eventListeners) {
                        listener.onUserFound(targetId, online);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Ошибка поиска пользователя", e);
            }
        }).start();
    }

    /**
     * Отправить статус "печатает..."
     */
    public void sendTyping(String toUserId) {
        if (!isConnected || userId == null) return;

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("fromId", userId);
                json.put("to", toUserId);

                Request request = new Request.Builder()
                    .url(SERVER_URL + "/typing")
                    .post(RequestBody.create(json.toString(), JSON))
                    .build();

                httpClient.newCall(request).execute();
            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки typing", e);
            }
        }).start();
    }

    /**
     * Отправить статус онлайн/офлайн
     */
    public void sendOnlineStatus(boolean online) {
        if (!isConnected || userId == null) return;

        // В HTTP версии просто логируем
        Log.d(TAG, "Статус: " + (online ? "онлайн" : "офлайн"));
    }

    // ============================================
    // WebRTC Сигнализация
    // ============================================

    /**
     * Отправить WebRTC Offer
     */
    public void sendWebRTCOffer(String toUserId, JSONObject offer) {
        sendSignal(toUserId, "offer", offer);
    }

    /**
     * Отправить WebRTC Answer
     */
    public void sendWebRTCAnswer(String toUserId, JSONObject answer) {
        sendSignal(toUserId, "answer", answer);
    }

    /**
     * Отправить ICE Candidate
     */
    public void sendWebRTCIceCandidate(String toUserId, JSONObject candidate) {
        sendSignal(toUserId, "ice-candidate", candidate);
    }

    private void sendSignal(String toUserId, String type, JSONObject payload) {
        if (!isConnected || userId == null) {
            Log.w(TAG, "Нет соединения для WebRTC сигнала");
            return;
        }

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("fromId", userId);
                json.put("to", toUserId);
                json.put("type", type);
                json.put("payload", payload);

                Request request = new Request.Builder()
                    .url(SERVER_URL + "/signal")
                    .post(RequestBody.create(json.toString(), JSON))
                    .build();

                httpClient.newCall(request).execute();
                Log.d(TAG, "WebRTC сигнал отправлен: " + type);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки WebRTC сигнала", e);
            }
        }).start();
    }

    // ============================================
    // Получение сообщений (polling)
    // ============================================

    /**
     * Получить новые сообщения
     */
    public void receiveMessages() {
        if (!isConnected || userId == null) return;

        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                    .url(SERVER_URL + "/receive/" + userId)
                    .get()
                    .build();

                Response response = httpClient.newCall(request).execute();
                String responseBody = response.body().string();

                JSONObject data = new JSONObject(responseBody);
                org.json.JSONArray messages = data.getJSONArray("messages");

                for (int i = 0; i < messages.length(); i++) {
                    JSONObject msg = messages.getJSONObject(i);
                    String type = msg.getString("type");

                    if ("message".equals(type)) {
                        String from = msg.getString("from");
                        JSONObject payload = msg.getJSONObject("payload");
                        handler.post(() -> {
                            for (P2PEventListener listener : eventListeners) {
                                listener.onMessageReceived(from, payload);
                            }
                        });
                    } else if ("offer".equals(type)) {
                        String from = msg.getString("from");
                        JSONObject payload = msg.getJSONObject("payload");
                        handler.post(() -> {
                            for (P2PEventListener listener : eventListeners) {
                                listener.onWebRTCOffer(from, payload);
                            }
                        });
                    } else if ("answer".equals(type)) {
                        String from = msg.getString("from");
                        JSONObject payload = msg.getJSONObject("payload");
                        handler.post(() -> {
                            for (P2PEventListener listener : eventListeners) {
                                listener.onWebRTCAnswer(from, payload);
                            }
                        });
                    } else if ("ice-candidate".equals(type)) {
                        String from = msg.getString("from");
                        JSONObject payload = msg.getJSONObject("payload");
                        handler.post(() -> {
                            for (P2PEventListener listener : eventListeners) {
                                listener.onWebRTCIceCandidate(from, payload);
                            }
                        });
                    } else if ("typing".equals(type)) {
                        String from = msg.getString("from");
                        handler.post(() -> {
                            for (P2PEventListener listener : eventListeners) {
                                listener.onTyping(from);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения сообщений", e);
            }
        }).start();
    }

    // ============================================
    // Heartbeat
    // ============================================

    private void startHeartbeat() {
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                sendHeartbeat();
                receiveMessages(); // Получаем сообщения при каждом heartbeat
                handler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        };
        handler.post(heartbeatRunnable);
        Log.d(TAG, "Heartbeat запущен");
    }

    private void stopHeartbeat() {
        if (heartbeatRunnable != null) {
            handler.removeCallbacks(heartbeatRunnable);
            Log.d(TAG, "Heartbeat остановлен");
        }
    }

    private void sendHeartbeat() {
        if (!isConnected || userId == null) return;

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("userId", userId);

                Request request = new Request.Builder()
                    .url(SERVER_URL + "/heartbeat")
                    .post(RequestBody.create(json.toString(), JSON))
                    .build();

                Response response = httpClient.newCall(request).execute();
                Log.d(TAG, "Heartbeat отправлен");
            } catch (Exception e) {
                Log.e(TAG, "Ошибка heartbeat", e);
            }
        }).start();
    }

    // ============================================
    // Утилиты
    // ============================================

    private void reconnect() {
        Log.d(TAG, "Переподключение через 5 секунд...");
        new Handler(Looper.getMainLooper()).postDelayed(this::connect, 5000);
    }

    /**
     * Проверка подключения
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Получить ID пользователя
     */
    public String getUserId() {
        return userId;
    }

    // ============================================
    // Синхронизация контактов
    // ============================================

    /**
     * Зарегистрировать номер телефона
     */
    public void registerPhone(String phone) {
        if (!isConnected || userId == null) {
            Log.w(TAG, "Нет соединения для регистрации номера");
            handler.post(() -> {
                for (P2PEventListener listener : eventListeners) {
                    listener.onError("Сначала дождитесь подключения P2P");
                }
            });
            return;
        }

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("userId", userId);
                json.put("phone", phone);

                Request request = new Request.Builder()
                    .url(SERVER_URL + "/register-phone")
                    .post(RequestBody.create(json.toString(), JSON))
                    .build();

                Response response = httpClient.newCall(request).execute();
                String responseBody = response.body().string();

                // Проверяем успешность ответа
                if (!response.isSuccessful()) {
                    throw new Exception("Ошибка сервера: " + responseBody);
                }

                JSONObject result = new JSONObject(responseBody);
                boolean alreadyRegistered = result.optBoolean("alreadyRegistered", false);
                String resultUserId = result.optString("userId", userId);

                handler.post(() -> {
                    for (P2PEventListener listener : eventListeners) {
                        listener.onPhoneRegistered(resultUserId, alreadyRegistered);
                    }
                });

                Log.d(TAG, "Номер зарегистрирован: " + (alreadyRegistered ? "уже был" : "успешно"));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка регистрации номера", e);
                handler.post(() -> {
                    for (P2PEventListener listener : eventListeners) {
                        listener.onError("Ошибка: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    /**
     * Синхронизировать контакты с сервером
     * @param contacts Список контактов: [{"name": "Имя", "phone": "+79991234567"}, ...]
     */
    public void syncContacts(List<JSONObject> contacts) {
        if (!isConnected || userId == null) {
            Log.w(TAG, "Нет соединения для синхронизации контактов");
            handler.post(() -> {
                for (P2PEventListener listener : eventListeners) {
                    listener.onError("Сначала дождитесь подключения P2P");
                }
            });
            return;
        }

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("userId", userId);
                org.json.JSONArray contactsArray = new org.json.JSONArray();
                for (JSONObject contact : contacts) {
                    contactsArray.put(contact);
                }
                json.put("contacts", contactsArray);

                Request request = new Request.Builder()
                    .url(SERVER_URL + "/sync-contacts")
                    .post(RequestBody.create(json.toString(), JSON))
                    .build();

                Response response = httpClient.newCall(request).execute();
                String responseBody = response.body().string();

                // Проверяем успешность ответа
                if (!response.isSuccessful()) {
                    throw new Exception("Ошибка сервера: " + responseBody);
                }

                JSONObject result = new JSONObject(responseBody);
                org.json.JSONArray matches = result.optJSONArray("matches");
                
                List<JSONObject> matchesList = new ArrayList<>();
                if (matches != null) {
                    for (int i = 0; i < matches.length(); i++) {
                        matchesList.add(matches.getJSONObject(i));
                    }
                }

                handler.post(() -> {
                    for (P2PEventListener listener : eventListeners) {
                        listener.onContactsSynced(matchesList);
                    }
                });

                Log.d(TAG, "Синхронизация контактов: найдено " + matchesList.size() + " совпадений");
            } catch (Exception e) {
                Log.e(TAG, "Ошибка синхронизации контактов", e);
                handler.post(() -> {
                    for (P2PEventListener listener : eventListeners) {
                        listener.onError("Ошибка синхронизации: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    /**
     * Найти пользователя по номеру телефона
     */
    public void findByPhone(String phone) {
        if (!isConnected || userId == null) {
            Log.w(TAG, "Нет соединения для поиска по номеру");
            return;
        }

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("phone", phone);

                Request request = new Request.Builder()
                    .url(SERVER_URL + "/find-by-phone")
                    .post(RequestBody.create(json.toString(), JSON))
                    .build();

                Response response = httpClient.newCall(request).execute();
                String responseBody = response.body().string();

                JSONObject result = new JSONObject(responseBody);
                boolean found = result.getBoolean("found");

                if (found) {
                    String foundUserId = result.getString("userId");
                    boolean online = result.getBoolean("online");
                    handler.post(() -> {
                        for (P2PEventListener listener : eventListeners) {
                            listener.onUserFound(foundUserId, online);
                        }
                    });
                }

                Log.d(TAG, "Поиск по номеру: " + (found ? "найден" : "не найден"));
            } catch (Exception e) {
                Log.e(TAG, "Ошибка поиска по номеру", e);
                handler.post(() -> {
                    for (P2PEventListener listener : eventListeners) {
                        listener.onError(e.getMessage());
                    }
                });
            }
        }).start();
    }
}
