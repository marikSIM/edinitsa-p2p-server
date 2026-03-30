/**
 * P2P Сервер сигнализации для Render.com
 * Работает через HTTP (без WebSocket)
 *
 * Запуск:
 * 1. npm install
 * 2. node server-render.js
 *
 * Порт: 3000 (HTTP)
 */

const express = require('express');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// Хранилище подключённых пользователей
// Map<userId, { lastSeen: number, online: boolean }>
const users = new Map();

// Очередь сообщений для офлайн-пользователей
// Map<userId, Array<message>>
const offlineQueue = new Map();

// Статистика
const stats = {
  connectedUsers: 0,
  totalMessages: 0,
  startTime: Date.now()
};

// ============================================
// API Endpoints
// ============================================

// Регистрация пользователя
app.post('/register', (req, res) => {
  const userId = uuidv4();
  users.set(userId, { lastSeen: Date.now(), online: true });
  stats.connectedUsers = users.size;
  
  console.log(`📱 Новый пользователь: ${userId}`);
  
  res.json({
    type: 'connected',
    userId: userId,
    message: 'Подключено к серверу сигнализации'
  });
});

// Heartbeat (обновление статуса)
app.post('/heartbeat', (req, res) => {
  const { userId } = req.body;
  
  if (userId && users.has(userId)) {
    users.get(userId).lastSeen = Date.now();
    users.get(userId).online = true;
    res.json({ success: true });
  } else {
    res.status(404).json({ error: 'User not found' });
  }
});

// Поиск пользователя
app.post('/find-user', (req, res) => {
  const { fromId, targetId } = req.body;
  
  const target = users.get(targetId);
  const isOnline = target && target.online && (Date.now() - target.lastSeen < 30000);
  
  console.log(`🔍 Поиск ${targetId}: ${isOnline ? 'онлайн' : 'офлайн'}`);
  
  res.json({
    type: 'user-found',
    userId: targetId,
    online: isOnline
  });
});

// Отправка сообщения
app.post('/send', (req, res) => {
  const { fromId, to, payload } = req.body;
  
  stats.totalMessages++;
  
  const target = users.get(to);
  const isOnline = target && target.online && (Date.now() - target.lastSeen < 30000);
  
  if (isOnline) {
    // Получатель онлайн - сохраняем в очередь для получения
    if (!offlineQueue.has(to)) {
      offlineQueue.set(to, []);
    }
    offlineQueue.get(to).push({
      type: 'message',
      from: fromId,
      payload: payload,
      timestamp: Date.now()
    });
    
    console.log(`📨 Сообщение от ${fromId} к ${to} (ожидает получения)`);
    
    res.json({
      type: 'message-sent',
      to: to,
      messageId: payload.id,
      delivered: false,
      queued: true
    });
  } else {
    // Получатель офлайн - сохраняем в очередь
    if (!offlineQueue.has(to)) {
      offlineQueue.set(to, []);
    }
    offlineQueue.get(to).push({
      type: 'message',
      from: fromId,
      payload: payload,
      timestamp: Date.now()
    });
    
    console.log(`💾 Сообщение сохранено в очереди для ${to}`);
    
    res.json({
      type: 'message-sent',
      to: to,
      messageId: payload.id,
      delivered: false,
      queued: true
    });
  }
});

// Получение сообщений из очереди
app.get('/receive/:userId', (req, res) => {
  const userId = req.params.userId;
  const queue = offlineQueue.get(userId) || [];
  
  if (queue.length > 0) {
    console.log(`📥 Пользователь ${userId} получил ${queue.length} сообщений`);
    // Очищаем очередь после получения
    offlineQueue.delete(userId);
  }
  
  res.json({
    type: 'messages',
    messages: queue
  });
});

// WebRTC сигнализация (offer/answer/ice-candidate)
app.post('/signal', (req, res) => {
  const { fromId, to, type, payload } = req.body;
  
  const target = users.get(to);
  const isOnline = target && target.online && (Date.now() - target.lastSeen < 30000);
  
  if (isOnline) {
    // Сохраняем сигнал для получения
    if (!offlineQueue.has(to)) {
      offlineQueue.set(to, []);
    }
    offlineQueue.get(to).push({
      type: type,
      from: fromId,
      payload: payload,
      timestamp: Date.now()
    });
    
    console.log(`📡 Сигнал ${type} от ${fromId} к ${to}`);
    
    res.json({ success: true, queued: true });
  } else {
    res.status(404).json({ error: 'User offline' });
  }
});

// Статус "печатает..."
app.post('/typing', (req, res) => {
  const { fromId, to } = req.body;
  
  const target = users.get(to);
  const isOnline = target && target.online && (Date.now() - target.lastSeen < 30000);
  
  if (isOnline) {
    if (!offlineQueue.has(to)) {
      offlineQueue.set(to, []);
    }
    offlineQueue.get(to).push({
      type: 'typing',
      from: fromId,
      timestamp: Date.now()
    });
  }
  
  res.json({ success: true });
});

// Статистика
app.get('/stats', (req, res) => {
  // Считаем онлайн пользователей (за последние 30 сек)
  let onlineCount = 0;
  users.forEach((user) => {
    if (user.online && Date.now() - user.lastSeen < 30000) {
      onlineCount++;
    }
  });
  
  res.json({
    connectedUsers: onlineCount,
    totalMessages: stats.totalMessages,
    uptime: Math.floor((Date.now() - stats.startTime) / 1000),
    offlineQueues: offlineQueue.size
  });
});

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: Date.now() });
});

// ============================================
// Очистка старых пользователей
// ============================================

setInterval(() => {
  const now = Date.now();
  users.forEach((user, userId) => {
    if (now - user.lastSeen > 60000) {
      user.online = false;
    }
  });
}, 10000);

// ============================================
// Запуск сервера
// ============================================

app.listen(PORT, () => {
  console.log(`🚀 P2P Server (Render) запущен на порту ${PORT}`);
  console.log(`📊 Stats: http://localhost:${PORT}/stats`);
  console.log(`💓 Health: http://localhost:${PORT}/health`);
});
