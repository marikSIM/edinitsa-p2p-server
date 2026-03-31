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
// Map<userId, { lastSeen: number, online: boolean, phone?: string }>
const users = new Map();

// Очередь сообщений для офлайн-пользователей
// Map<userId, Array<message>>
const offlineQueue = new Map();

// Хранилище привязки телефонных номеров к userId
// Map<normalizedPhone, userId>
const phoneToUser = new Map();

// Статистика
const stats = {
  connectedUsers: 0,
  totalMessages: 0,
  startTime: Date.now()
};

/**
 * Нормализация номера телефона
 * Удаляет всё кроме цифр и +, убирает ведущий +
 */
function normalizePhone(phone) {
  if (!phone) return null;
  // Удаляем всё кроме цифр
  const digits = phone.replace(/\D/g, '');
  // Если начинается с 8, заменяем на 7 (для РФ/Казахстана)
  if (digits.startsWith('8') && digits.length === 11) {
    return '7' + digits.slice(1);
  }
  return digits;
}

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

// Привязка номера телефона к userId
app.post('/register-phone', (req, res) => {
  const { userId, phone } = req.body;

  if (!userId || !phone) {
    return res.status(400).json({ error: 'userId и phone обязательны' });
  }

  const normalizedPhone = normalizePhone(phone);
  
  // Проверяем, есть ли уже пользователь с таким номером
  const existingUser = phoneToUser.get(normalizedPhone);
  if (existingUser) {
    console.log(`📞 Номер ${normalizedPhone} уже привязан к ${existingUser}`);
    // Возвращаем существующего userId (номер уже зарегистрирован)
    return res.json({
      success: true,
      userId: existingUser,
      alreadyRegistered: true,
      message: 'Номер уже зарегистрирован'
    });
  }

  // Привязываем номер к userId
  phoneToUser.set(normalizedPhone, userId);
  
  // Обновляем запись пользователя
  if (users.has(userId)) {
    users.get(userId).phone = normalizedPhone;
  }

  console.log(`📞 Номер ${normalizedPhone} привязан к ${userId}`);

  res.json({
    success: true,
    userId: userId,
    alreadyRegistered: false,
    message: 'Номер успешно привязан'
  });
});

// Синхронизация контактов
app.post('/sync-contacts', (req, res) => {
  const { userId, contacts } = req.body;

  if (!userId || !contacts || !Array.isArray(contacts)) {
    return res.status(400).json({ error: 'userId и contacts обязательны' });
  }

  const matches = [];

  // Проверяем каждый контакт
  for (const contact of contacts) {
    const normalizedPhone = normalizePhone(contact.phone);
    if (!normalizedPhone) continue;

    const foundUserId = phoneToUser.get(normalizedPhone);
    
    if (foundUserId && foundUserId !== userId) {
      // Нашли совпадение - получаем информацию о пользователе
      const userInfo = users.get(foundUserId);
      matches.push({
        name: contact.name,
        phone: contact.phone,
        userId: foundUserId,
        displayName: userInfo?.displayName || contact.name,
        online: userInfo?.online || false
      });
    }
  }

  console.log(`🔄 Синхронизация для ${userId}: найдено ${matches.length} контактов`);

  res.json({
    success: true,
    matches: matches,
    totalContacts: contacts.length,
    foundCount: matches.length
  });
});

// Поиск пользователя по номеру телефона
app.post('/find-by-phone', (req, res) => {
  const { phone } = req.body;

  if (!phone) {
    return res.status(400).json({ error: 'phone обязателен' });
  }

  const normalizedPhone = normalizePhone(phone);
  const foundUserId = phoneToUser.get(normalizedPhone);

  if (foundUserId) {
    const userInfo = users.get(foundUserId);
    const isOnline = userInfo?.online && (Date.now() - userInfo.lastSeen < 30000);

    console.log(`🔍 Найден пользователь ${foundUserId} по номеру ${normalizedPhone}`);

    res.json({
      found: true,
      userId: foundUserId,
      displayName: userInfo?.displayName || null,
      online: isOnline
    });
  } else {
    console.log(`🔍 Номер ${normalizedPhone} не найден`);

    res.json({
      found: false,
      message: 'Пользователь с таким номером не найден'
    });
  }
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
    offlineQueues: offlineQueue.size,
    registeredPhones: phoneToUser.size
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
