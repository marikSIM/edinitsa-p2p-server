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

// Хранилище сессий (token -> { userId, createdAt, lastUsed })
// ⚠️ Для продакшена используйте Redis или базу данных
const sessions = new Map();

// Статистика
const stats = {
  connectedUsers: 0,
  totalMessages: 0,
  startTime: Date.now()
};

/**
 * Генерация session token
 */
function generateSessionToken() {
  const crypto = require('crypto');
  return crypto.randomBytes(32).toString('hex');
}

/**
 * Middleware для проверки авторизации
 * Добавляет userId в req.body если токен валиден
 */
function authMiddleware(req, res, next) {
  const token = req.headers['authorization']?.replace('Bearer ', '');
  
  if (!token) {
    // Разрешаем запросы без токена для обратной совместимости
    // Но в будущем можно вернуть: return res.status(401).json({ error: 'Token required' });
    return next();
  }
  
  const session = sessions.get(token);
  if (session && Date.now() - session.lastUsed < 24 * 60 * 60 * 1000) { // 24 часа
    session.lastUsed = Date.now();
    // Добавляем userId в body для последующих обработчиков
    req.authUserId = session.userId;
    console.log(`🔐 Авторизация: token валиден для ${session.userId}`);
  } else {
    sessions.delete(token);
    console.log(`⚠️ Авторизация: token невалиден`);
  }
  
  next();
}

// Применяем middleware ко всем запросам
app.use(authMiddleware);

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

// Вход пользователя (привязка номера + генерация sessionToken)
app.post('/login', (req, res) => {
  const { phone } = req.body;

  if (!phone) {
    return res.status(400).json({ error: 'phone обязателен' });
  }

  const normalizedPhone = normalizePhone(phone);
  
  // Проверяем, есть ли уже пользователь с таким номером
  let userId = phoneToUser.get(normalizedPhone);
  let isNewUser = false;
  
  if (!userId) {
    // Новый пользователь - создаём userId
    userId = uuidv4();
    phoneToUser.set(normalizedPhone, userId);
    isNewUser = true;
    console.log(`🆕 Новый пользователь: ${userId} (номер: ${normalizedPhone})`);
  } else {
    console.log(`🔐 Вход пользователя: ${userId} (номер: ${normalizedPhone})`);
  }
  
  // Регистрируем пользователя в users
  users.set(userId, { lastSeen: Date.now(), online: true, phone: normalizedPhone });
  stats.connectedUsers = users.size;
  
  // Генерируем sessionToken
  const sessionToken = generateSessionToken();
  sessions.set(sessionToken, {
    userId: userId,
    createdAt: Date.now(),
    lastUsed: Date.now()
  });
  
  console.log(`🔑 SessionToken создан для ${userId}`);

  res.json({
    userId: userId,
    sessionToken: sessionToken,
    isNewUser: isNewUser,
    message: isNewUser ? 'Регистрация успешна' : 'Вход выполнен'
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
  
  // ⚠️ ПРОВЕРКА АВТОРИЗАЦИИ для продакшена
  if (req.authUserId && req.authUserId !== userId) {
    console.log(`⚠️ Попытка синхронизации от чужого имени: ${userId} (токен: ${req.authUserId})`);
    return res.status(403).json({ error: 'Forbidden: userId mismatch' });
  }

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
      const isOnline = userInfo?.online && (Date.now() - userInfo.lastSeen < 30000);
      
      matches.push({
        name: contact.name,
        phone: contact.phone,
        userId: foundUserId,
        displayName: userInfo?.displayName || contact.name,
        online: isOnline || false,
        normalizedPhone: normalizedPhone // Добавляем для отладки
      });
      
      console.log(`✅ Найдено совпадение: ${contact.name} (${normalizedPhone}) -> ${foundUserId}`);
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
      online: isOnline,
      phone: phone // Возвращаем оригинальный номер
    });
  } else {
    console.log(`🔍 Номер ${normalizedPhone} не найден`);

    res.json({
      found: false,
      message: 'Пользователь с таким номером не найден'
    });
  }
});

// Поиск пользователя по номеру телефона (упрощённая версия для быстрого поиска)
app.get('/find-by-phone/:phone', (req, res) => {
  const phone = req.params.phone;
  const normalizedPhone = normalizePhone(phone);
  const foundUserId = phoneToUser.get(normalizedPhone);

  if (foundUserId) {
    const userInfo = users.get(foundUserId);
    const isOnline = userInfo?.online && (Date.now() - userInfo.lastSeen < 30000);

    res.json({
      found: true,
      userId: foundUserId,
      online: isOnline
    });
  } else {
    res.json({ found: false });
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

  if (!fromId || !to) {
    return res.status(400).json({ error: 'Missing required fields: fromId, to' });
  }

  // ⚠️ ПРОВЕРКА АВТОРИЗАЦИИ для продакшена
  // Если токен есть в заголовке, проверяем что fromId совпадает с userId из токена
  if (req.authUserId && req.authUserId !== fromId) {
    console.log(`⚠️ Попытка отправки от чужого имени: ${fromId} (токен: ${req.authUserId})`);
    return res.status(403).json({ error: 'Forbidden: userId mismatch' });
  }

  stats.totalMessages++;

  const target = users.get(to);
  const isOnline = target && target.online && (Date.now() - target.lastSeen < 30000);

  const messagePayload = payload || {};

  if (isOnline) {
    // Получатель онлайн - сохраняем в очередь для получения
    if (!offlineQueue.has(to)) {
      offlineQueue.set(to, []);
    }
    offlineQueue.get(to).push({
      type: 'message',
      from: fromId,
      payload: messagePayload,
      timestamp: Date.now()
    });

    console.log(`📨 Сообщение от ${fromId?.substring(0, 8)} к ${to?.substring(0, 8)} (ожидает получения)`);

    res.json({
      type: 'message-sent',
      to: to,
      messageId: messagePayload.id || null,
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
      payload: messagePayload,
      timestamp: Date.now()
    });

    console.log(`💾 Сообщение сохранено в очереди для ${to?.substring(0, 8)}`);

    res.json({
      type: 'message-sent',
      to: to,
      messageId: messagePayload.id || null,
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
// Регистрация (guest)
// ============================================

app.post('/register', (req, res) => {
  const userId = uuidv4();
  const sessionToken = generateSessionToken();

  users.set(userId, {
    lastSeen: Date.now(),
    online: true,
    sessionToken: sessionToken
  });

  console.log(`🆕 Новый пользователь зарегистрирован: ${userId.substring(0, 8)}`);

  res.json({
    success: true,
    userId: userId,
    sessionToken: sessionToken
  });
});

// ============================================
// WebRTC сигнализация
// ============================================

app.post('/signal', (req, res) => {
  const { fromId, to, type, payload } = req.body;

  if (!to || !type) {
    return res.status(400).json({ success: false, error: 'Missing required fields' });
  }

  const target = users.get(to);
  const isOnline = target && target.online && (Date.now() - target.lastSeen < 30000);

  if (isOnline) {
    if (!offlineQueue.has(to)) {
      offlineQueue.set(to, []);
    }
    offlineQueue.get(to).push({
      type: 'signal',
      signalType: type,
      from: fromId,
      payload: payload,
      timestamp: Date.now()
    });
    console.log(`📡 Signal ${type} от ${fromId?.substring(0, 8)} к ${to.substring(0, 8)} — доставлен в очередь`);
  } else {
    console.log(`⏳ Signal ${type} от ${fromId?.substring(0, 8)} к ${to.substring(0, 8)} — пользователь офлайн, сохранён`);
    if (!offlineQueue.has(to)) {
      offlineQueue.set(to, []);
    }
    offlineQueue.get(to).push({
      type: 'signal',
      signalType: type,
      from: fromId,
      payload: payload,
      timestamp: Date.now()
    });
  }

  res.json({ success: true, delivered: isOnline });
});

// ============================================
// Группы
// ============================================

// Хранилище групп
const groups = new Map();
const GROUPS_FILE = require('path').join(__dirname, 'groups_data.json');

function loadGroups() {
  try {
    const fs = require('fs');
    if (fs.existsSync(GROUPS_FILE)) {
      const data = fs.readFileSync(GROUPS_FILE, 'utf8');
      const parsed = JSON.parse(data);
      for (const [groupId, groupData] of Object.entries(parsed)) {
        groups.set(groupId, {
          name: groupData.name,
          description: groupData.description || '',
          avatar: groupData.avatar || '👥',
          createdBy: groupData.createdBy,
          members: new Set(groupData.members),
          createdAt: groupData.createdAt
        });
      }
      console.log(`✅ Загружено ${groups.size} групп из файла`);
    }
  } catch (err) {
    console.warn('⚠️ Не удалось загрузить группы:', err.message);
  }
}

function saveGroups() {
  try {
    const fs = require('fs');
    const toSave = {};
    for (const [groupId, groupData] of groups.entries()) {
      toSave[groupId] = {
        name: groupData.name,
        description: groupData.description,
        avatar: groupData.avatar,
        createdBy: groupData.createdBy,
        members: Array.from(groupData.members),
        createdAt: groupData.createdAt
      };
    }
    fs.writeFileSync(GROUPS_FILE, JSON.stringify(toSave, null, 2));
  } catch (err) {
    console.error('❌ Ошибка сохранения групп:', err.message);
  }
}

loadGroups();

// POST /groups/create — Создать группу
app.post('/groups/create', (req, res) => {
  const { creatorId, name, avatar, description, members } = req.body;
  const groupId = uuidv4();

  groups.set(groupId, {
    name: name || 'Группа',
    description: description || '',
    avatar: avatar || '👥',
    createdBy: creatorId,
    members: new Set([creatorId, ...(members || [])]),
    createdAt: Date.now()
  });

  saveGroups();

  // Добавляем уведомления всем участникам
  for (const memberId of groups.get(groupId).members) {
    if (!offlineQueue.has(memberId)) {
      offlineQueue.set(memberId, []);
    }
    offlineQueue.get(memberId).push({
      type: 'group-created',
      groupId,
      name: name,
      avatar: avatar || '👥',
      description: description || '',
      createdBy: creatorId,
      members: Array.from(groups.get(groupId).members),
      timestamp: Date.now()
    });
  }

  console.log(`👥 Группа создана: ${name} (${groupId.substring(0, 8)}) от ${creatorId?.substring(0, 8)}`);
  res.json({ success: true, groupId });
});

// POST /groups/send — Отправить сообщение в группу
app.post('/groups/send', (req, res) => {
  const { fromId, groupId, payload } = req.body;

  const group = groups.get(groupId);
  if (!group) {
    return res.status(404).json({ success: false, error: 'Группа не найдена' });
  }

  if (!group.members.has(fromId)) {
    return res.status(403).json({ success: false, error: 'Вы не участник этой группы' });
  }

  let deliveredCount = 0;
  for (const memberId of group.members) {
    if (memberId !== fromId) {
      if (!offlineQueue.has(memberId)) {
        offlineQueue.set(memberId, []);
      }
      offlineQueue.get(memberId).push({
        type: 'group-message',
        groupId,
        payload: { ...payload, serverSenderId: fromId },
        timestamp: Date.now()
      });
      deliveredCount++;
    }
  }

  stats.totalMessages++;
  console.log(`💬 Сообщение в группу ${groupId.substring(0, 8)} от ${fromId?.substring(0, 8)} — доставлено ${deliveredCount} участникам`);
  res.json({ success: true, delivered: deliveredCount });
});

// POST /groups/invite — Пригласить в группу
app.post('/groups/invite', (req, res) => {
  const { fromId, groupId, userId } = req.body;

  const group = groups.get(groupId);
  if (!group) {
    return res.status(404).json({ success: false, error: 'Группа не найдена' });
  }

  if (group.members.has(userId)) {
    return res.json({ success: true, message: 'Уже в группе' });
  }

  group.members.add(userId);
  saveGroups();

  // Уведомляем приглашённого
  if (!offlineQueue.has(userId)) {
    offlineQueue.set(userId, []);
  }
  offlineQueue.get(userId).push({
    type: 'group-invited',
    groupId,
    name: group.name,
    avatar: group.avatar,
    description: group.description,
    createdBy: group.createdBy,
    invitedBy: fromId,
    members: Array.from(group.members),
    timestamp: Date.now()
  });

  // Уведомляем остальных участников
  for (const memberId of group.members) {
    if (memberId !== fromId && memberId !== userId) {
      if (!offlineQueue.has(memberId)) {
        offlineQueue.set(memberId, []);
      }
      offlineQueue.get(memberId).push({
        type: 'group-member-added',
        groupId,
        userId,
        name: group.name,
        timestamp: Date.now()
      });
    }
  }

  console.log(`➕ ${userId?.substring(0, 8)} приглашён в группу ${groupId.substring(0, 8)}`);
  res.json({ success: true });
});

// POST /groups/leave — Покинуть группу
app.post('/groups/leave', (req, res) => {
  const { userId, groupId } = req.body;

  const group = groups.get(groupId);
  if (!group) {
    return res.status(404).json({ success: false, error: 'Группа не найдена' });
  }

  group.members.delete(userId);
  saveGroups();

  // Уведомляем остальных
  for (const memberId of group.members) {
    if (!offlineQueue.has(memberId)) {
      offlineQueue.set(memberId, []);
    }
    offlineQueue.get(memberId).push({
      type: 'group-member-left',
      groupId,
      userId,
      timestamp: Date.now()
    });
  }

  // Если группа пуста — удаляем
  if (group.members.size === 0) {
    groups.delete(groupId);
    saveGroups();
    console.log(`🗑️ Группа ${groupId.substring(0, 8)} удалена (нет участников)`);
  }

  console.log(`➖ ${userId?.substring(0, 8)} покинул группу ${groupId.substring(0, 8)}`);
  res.json({ success: true });
});

// POST /groups/list — Получить список групп пользователя
app.post('/groups/list', (req, res) => {
  const { userId } = req.body;

  const userGroups = [];
  for (const [groupId, groupData] of groups.entries()) {
    if (groupData.members.has(userId)) {
      userGroups.push({
        groupId,
        name: groupData.name,
        avatar: groupData.avatar,
        description: groupData.description,
        createdBy: groupData.createdBy,
        memberCount: groupData.members.size,
        createdAt: groupData.createdAt
      });
    }
  }

  res.json({ success: true, groups: userGroups });
});

// POST /groups/members — Получить участников группы
app.post('/groups/members', (req, res) => {
  const { groupId } = req.body;

  const group = groups.get(groupId);
  if (!group) {
    return res.status(404).json({ success: false, error: 'Группа не найдена' });
  }

  res.json({
    success: true,
    members: Array.from(group.members),
    name: group.name,
    avatar: group.avatar
  });
});

// POST /groups/remove — Удалить участника из группы
app.post('/groups/remove', (req, res) => {
  const { fromId, groupId, userIdToRemove } = req.body;

  const group = groups.get(groupId);
  if (!group) {
    return res.status(404).json({ success: false, error: 'Группа не найдена' });
  }

  group.members.delete(userIdToRemove);
  saveGroups();

  // Уведомляем удалённого
  if (!offlineQueue.has(userIdToRemove)) {
    offlineQueue.set(userIdToRemove, []);
  }
  offlineQueue.get(userIdToRemove).push({
    type: 'group-member-removed',
    groupId,
    name: group.name,
    timestamp: Date.now()
  });

  // Уведомляем остальных
  for (const memberId of group.members) {
    if (!offlineQueue.has(memberId)) {
      offlineQueue.set(memberId, []);
    }
    offlineQueue.get(memberId).push({
      type: 'group-member-removed',
      groupId,
      userId: userIdToRemove,
      name: group.name,
      timestamp: Date.now()
    });
  }

  console.log(`🚫 ${userIdToRemove?.substring(0, 8)} удалён из группы ${groupId.substring(0, 8)}`);
  res.json({ success: true });
});

// POST /groups/update-role — Обновить роль участника (заглушка)
app.post('/groups/update-role', (req, res) => {
  const { groupId, userId, role } = req.body;
  // В текущей реализации роли не используются, просто отвечаем OK
  console.log(`🔄 Роль ${userId?.substring(0, 8)} обновлена на ${role} в группе ${groupId?.substring(0, 8)}`);
  res.json({ success: true, message: 'Роль обновлена' });
});

// POST /groups/create-invite — Создать ссылку-приглашение (заглушка)
app.post('/groups/create-invite', (req, res) => {
  const { groupId, usageLimit, expiresAt } = req.body;
  const inviteLink = `${groupId}?invite=${uuidv4().substring(0, 8)}`;
  console.log(`🔗 Ссылка-приглашение создана для группы ${groupId?.substring(0, 8)}`);
  res.json({ success: true, inviteLink });
});

// POST /groups/revoke-invite — Отозвать ссылку-приглашение (заглушка)
app.post('/groups/revoke-invite', (req, res) => {
  const { groupId, inviteLink } = req.body;
  console.log(`🔗 Ссылка-приглашение отозвана для группы ${groupId?.substring(0, 8)}`);
  res.json({ success: true, message: 'Ссылка отозвана' });
});

// POST /groups/update — Обновить настройки группы
app.post('/groups/update', (req, res) => {
  const { groupId, name, avatar, description } = req.body;

  const group = groups.get(groupId);
  if (!group) {
    return res.status(404).json({ success: false, error: 'Группа не найдена' });
  }

  if (name) group.name = name;
  if (avatar) group.avatar = avatar;
  if (description !== undefined) group.description = description;

  saveGroups();

  // Уведомляем всех участников
  for (const memberId of group.members) {
    if (!offlineQueue.has(memberId)) {
      offlineQueue.set(memberId, []);
    }
    offlineQueue.get(memberId).push({
      type: 'group-updated',
      groupId,
      name: group.name,
      avatar: group.avatar,
      description: group.description,
      timestamp: Date.now()
    });
  }

  console.log(`✏️ Группа ${groupId.substring(0, 8)} обновлена`);
  res.json({ success: true });
});

// POST /groups/delete-message — Удалить сообщение в группе
app.post('/groups/delete-message', (req, res) => {
  const { fromId, groupId, messageId } = req.body;

  const group = groups.get(groupId);
  if (!group) {
    return res.status(404).json({ success: false, error: 'Группа не найдена' });
  }

  // Уведомляем всех участников об удалении
  for (const memberId of group.members) {
    if (!offlineQueue.has(memberId)) {
      offlineQueue.set(memberId, []);
    }
    offlineQueue.get(memberId).push({
      type: 'group-message-deleted',
      groupId,
      messageId,
      deletedBy: fromId,
      timestamp: Date.now()
    });
  }

  console.log(`🗑️ Сообщение ${messageId} удалено из группы ${groupId.substring(0, 8)}`);
  res.json({ success: true });
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

// Слушаем все сетевые интерфейсы (0.0.0.0) для доступа из локальной сети
app.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 P2P Server запущен на порту ${PORT}`);
  console.log(`📊 Stats: http://localhost:${PORT}/stats`);
  console.log(`💓 Health: http://localhost:${PORT}/health`);
  console.log(`🌐 Локальный IP: http://10.186.229.109:${PORT}`);
});
