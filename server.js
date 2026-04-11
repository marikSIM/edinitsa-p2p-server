/**
 * P2P Сервер сигнализации для мессенджера ЕДИНИЦА
 * 
 * ЕДИНСТВЕННЫЙ сервер — домашний, на 40ГБ RAM / 1ТБ SSD
 * 
 * Запуск:
 * 1. npm install
 * 2. node server.js
 * 
 * Порты:
 * - 3000: WebSocket + HTTP API
 * - 3001: HTTP Stats + OTA обновления
 */

const WebSocket = require('ws');
const http = require('http');
const express = require('express');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');
const path = require('path');
const fs = require('fs');

// ============================================
// КОНФИГУРАЦИЯ
// ============================================

const WS_PORT = 3000;
const HTTP_API_PORT = 3000;
const HTTP_STATS_PORT = 3001;

// Лимиты под сервер 40GB RAM / 1TB SSD
const MAX_MESSAGE_SIZE = 20 * 1024 * 1024; // 20 MB
const MAX_QUEUE_SIZE = 1000;
const MAX_CONNECTIONS_PER_IP = 100;
const MAX_CONNECTIONS_TOTAL = 10000;
const MESSAGE_RATE_LIMIT = 1000;
const CLEANUP_INTERVAL = 300000; // 5 минут

// ============================================
// НОРМАЛИЗАЦИЯ ТЕЛЕФОНОВ
// ============================================

function normalizePhone(phone) {
  if (!phone) return null;
  let cleaned = phone.replace(/[^\d+]/g, '');
  if (cleaned.startsWith('+')) cleaned = cleaned.substring(1);
  if (cleaned.startsWith('8') && cleaned.length === 11) {
    cleaned = '7' + cleaned.substring(1);
  }
  return cleaned;
}

// ============================================
// ХРАНИЛИЩА
// ============================================

// Map<userId, { ws?: WebSocket, lastSeen: number, online: boolean, phone?: string }>
const users = new Map();

// Map<userId, Array<message>>
const offlineQueue = new Map();

// Map<normalizedPhone, userId>
const phoneToUser = new Map();

// Map<sessionToken, { userId, createdAt, lastUsed }>
const sessions = new Map();

// Группы: Map<groupId, { name, description, avatar, createdBy, members: Set, createdAt }>
const groups = new Map();
const GROUPS_FILE = path.join(__dirname, 'groups_data.json');

// Rate limiting
const clientMessageCounts = new Map();
const bannedIPs = new Set();
const connectionCountsByIP = new Map();

// Статистика
const stats = {
  connectedUsers: 0,
  totalMessages: 0,
  startTime: Date.now(),
  lastMinuteMessages: 0
};

// ============================================
// SQLITE (кэш сообщений, сессий)
// ============================================

const sqlite3 = require('sqlite3').verbose();
const DB_PATH = path.join(__dirname, 'server_cache.db');
let db;

function initDatabase() {
  return new Promise((resolve, reject) => {
    db = new sqlite3.Database(DB_PATH, (err) => {
      if (err) {
        console.error('⚠️ SQLite ошибка подключения:', err.message);
        resolve();
        return;
      }
      console.log(`💾 База данных подключена: ${DB_PATH}`);
      
      db.serialize(() => {
        db.run(`CREATE TABLE IF NOT EXISTS sessions (
          token TEXT PRIMARY KEY,
          user_id TEXT NOT NULL,
          created_at INTEGER NOT NULL,
          last_used INTEGER NOT NULL
        )`, (err) => {
          if (err) console.error('Ошибка создания таблицы sessions:', err.message);
        });

        db.run(`CREATE TABLE IF NOT EXISTS message_cache (
          id TEXT PRIMARY KEY,
          from_id TEXT NOT NULL,
          to_id TEXT NOT NULL,
          payload TEXT,
          created_at INTEGER NOT NULL,
          delivered INTEGER DEFAULT 0
        )`, (err) => {
          if (err) console.error('Ошибка создания таблицы message_cache:', err.message);
        });

        db.run(`CREATE TABLE IF NOT EXISTS settings (
          key TEXT PRIMARY KEY,
          value TEXT
        )`, (err) => {
          if (err) console.error('Ошибка создания таблицы settings:', err.message);
        });

        resolve();
      });
    });
  });
}

// ============================================
// ЗАГРУЗКА/СОХРАНЕНИЕ ГРУПП
// ============================================

function loadGroups() {
  try {
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

// ============================================
// ГЕНЕРАЦИЯ СЕССИЙ
// ============================================

function generateSessionToken() {
  const crypto = require('crypto');
  return crypto.randomBytes(32).toString('hex');
}

// ============================================
// ОБРАБОТКА WEBSOCKET СООБЩЕНИЙ
// ============================================

function handleWSMessage(fromId, ws, data) {
  const { type, to, payload, phone, targetId, contacts, groupId, userId, name, avatar, description, members } = data;

  const user = users.get(fromId);
  if (user) {
    user.lastSeen = Date.now();
  }

  switch (type) {
    case 'register':
      handleWSRegister(fromId, ws, data);
      break;

    case 'heartbeat':
      ws.send(JSON.stringify({ type: 'heartbeat' }));
      break;

    case 'message':
      stats.totalMessages++;
      stats.lastMinuteMessages++;
      handleWSMessageSend(fromId, to, payload);
      break;

    case 'find-user':
      handleWSFindUser(fromId, targetId);
      break;

    case 'typing':
      sendWSMessage(to, { type: 'typing', from: fromId });
      break;

    case 'sync-contacts':
      handleWSSyncContacts(fromId, contacts);
      break;

    case 'register-phone':
      handleWSRegisterPhone(fromId, phone);
      break;

    case 'find-by-phone':
      handleWSFindByPhone(fromId, phone);
      break;

    case 'group-create':
      handleGroupCreate(fromId, ws, data);
      break;

    case 'group-message':
      stats.totalMessages++;
      stats.lastMinuteMessages++;
      handleGroupMessage(fromId, ws, data);
      break;

    case 'group-invite':
      handleGroupInvite(fromId, ws, data);
      break;

    case 'group-leave':
      handleGroupLeave(fromId, ws, data);
      break;

    case 'group-delete-message':
      handleGroupDeleteMessage(fromId, ws, data);
      break;

    case 'offer':
    case 'answer':
    case 'ice-candidate':
    case 'video_offer':
    case 'video_ready':
    case 'video_chunk':
    case 'video_complete':
    case 'video_ack':
    case 'video_cancel':
      sendWSMessage(to, { type, from: fromId, payload });
      break;

    default:
      ws.send(JSON.stringify({ type: 'error', message: 'Неизвестный тип' }));
  }
}

function handleWSRegister(userId, ws, data) {
  if (data.token) {
    const session = sessions.get(data.token);
    if (session) {
      session.lastUsed = Date.now();
      ws.send(JSON.stringify({ type: 'registered', userId: session.userId }));
    }
  } else if (data.phone) {
    const normalized = normalizePhone(data.phone);
    const existingUserId = phoneToUser.get(normalized);
    if (existingUserId) {
      const token = generateSessionToken();
      sessions.set(token, { userId: existingUserId, createdAt: Date.now(), lastUsed: Date.now() });
      ws.send(JSON.stringify({ type: 'session-created', userId: existingUserId, sessionToken: token }));
    }
  }
}

function handleWSMessageSend(fromId, to, payload) {
  const toUser = users.get(to);
  if (toUser && toUser.ws && toUser.ws.readyState === WebSocket.OPEN) {
    toUser.ws.send(JSON.stringify({ type: 'message', from: fromId, payload }));
    const fromUser = users.get(fromId);
    if (fromUser && fromUser.ws) {
      fromUser.ws.send(JSON.stringify({ type: 'message-delivered', to, messageId: payload?.id }));
    }
  } else {
    if (!offlineQueue.has(to)) offlineQueue.set(to, []);
    const queue = offlineQueue.get(to);
    if (queue.length < MAX_QUEUE_SIZE) {
      queue.push({ type: 'message', from: fromId, payload, timestamp: Date.now() });
    }
  }
}

function handleWSFindUser(fromId, targetId) {
  const fromUser = users.get(fromId);
  if (!fromUser) return;
  const targetUser = users.get(targetId);
  const isOnline = targetUser && targetUser.online && (Date.now() - targetUser.lastSeen < 30000);
  fromUser.ws?.send(JSON.stringify({ type: 'user-found', userId: targetId, online: isOnline }));
}

function handleWSSyncContacts(fromId, contacts) {
  if (!contacts || !Array.isArray(contacts)) return;
  const foundUsers = [];
  for (const contact of contacts) {
    const normalized = normalizePhone(contact.phone);
    if (normalized && phoneToUser.has(normalized)) {
      const foundUserId = phoneToUser.get(normalized);
      if (foundUserId !== fromId) {
        const foundUser = users.get(foundUserId);
        foundUsers.push({
          userId: foundUserId,
          phone: contact.phone,
          name: contact.name,
          online: foundUser?.online && (Date.now() - foundUser.lastSeen < 30000)
        });
      }
    }
  }
  const fromUser = users.get(fromId);
  fromUser?.ws?.send(JSON.stringify({ type: 'contacts-synced', contacts: foundUsers }));
}

function handleWSRegisterPhone(userId, phone) {
  const normalized = normalizePhone(phone);
  if (normalized) {
    phoneToUser.set(normalized, userId);
    const user = users.get(userId);
    if (user) user.phone = normalized;
    users.get(userId)?.ws?.send(JSON.stringify({ type: 'phone-registered', phone }));
  }
}

function handleWSFindByPhone(fromId, phone) {
  const normalized = normalizePhone(phone);
  const foundUserId = phoneToUser.get(normalized);
  const fromUser = users.get(fromId);
  if (foundUserId) {
    const foundUser = users.get(foundUserId);
    const isOnline = foundUser?.online && (Date.now() - foundUser.lastSeen < 30000);
    fromUser?.ws?.send(JSON.stringify({ type: 'phone-found', userId: foundUserId, online: isOnline }));
  } else {
    fromUser?.ws?.send(JSON.stringify({ type: 'phone-found', userId: null, online: false }));
  }
}

function sendWSMessage(userId, message) {
  const user = users.get(userId);
  if (user && user.ws && user.ws.readyState === WebSocket.OPEN) {
    user.ws.send(JSON.stringify(message));
  } else {
    if (!offlineQueue.has(userId)) offlineQueue.set(userId, []);
    offlineQueue.get(userId).push(message);
  }
}

// ============================================
// ГРУППЫ (WebSocket handlers)
// ============================================

function handleGroupCreate(fromId, ws, data) {
  const { groupId, name, avatar, description, members } = data;
  const serverGroupId = groupId || uuidv4();
  console.log(`👥 Создание группы: ${name} от ${fromId.substring(0, 8)}`);
  groups.set(serverGroupId, {
    name, description: description || '', avatar: avatar || '👥',
    createdBy: fromId, members: new Set(members || [fromId]), createdAt: Date.now()
  });
  saveGroups();
  for (const memberId of (members || [fromId])) {
    sendWSMessage(memberId, {
      type: 'group-created', groupId: serverGroupId, name, avatar: avatar || '👥',
      description: description || '', createdBy: fromId, members: members || [fromId]
    });
  }
  ws.send(JSON.stringify({ type: 'group-created', groupId: serverGroupId, name }));
}

function handleGroupMessage(fromId, ws, data) {
  const { groupId, payload } = data;
  const group = groups.get(groupId);
  if (!group) { ws.send(JSON.stringify({ type: 'error', message: 'Группа не найдена' })); return; }
  if (!group.members.has(fromId)) { ws.send(JSON.stringify({ type: 'error', message: 'Вы не участник' })); return; }
  for (const memberId of group.members) {
    if (memberId !== fromId) {
      sendWSMessage(memberId, { type: 'group-message', groupId, payload: { ...payload, serverSenderId: fromId } });
    }
  }
}

function handleGroupInvite(fromId, ws, data) {
  const { groupId, userId } = data;
  const group = groups.get(groupId);
  if (!group) return;
  if (group.members.has(userId)) return;
  group.members.add(userId);
  saveGroups();
  sendWSMessage(userId, {
    type: 'group-invited', groupId, name: group.name, avatar: group.avatar,
    description: group.description, createdBy: group.createdBy, invitedBy: fromId,
    members: Array.from(group.members)
  });
  for (const memberId of group.members) {
    if (memberId !== fromId && memberId !== userId) {
      sendWSMessage(memberId, { type: 'group-member-added', groupId, userId, name: group.name });
    }
  }
}

function handleGroupLeave(fromId, ws, data) {
  const { groupId } = data;
  const group = groups.get(groupId);
  if (!group) return;
  group.members.delete(fromId);
  saveGroups();
  for (const memberId of group.members) {
    sendWSMessage(memberId, { type: 'group-member-left', groupId, userId: fromId });
  }
  if (group.members.size === 0) {
    groups.delete(groupId);
    saveGroups();
  }
}

function handleGroupDeleteMessage(fromId, ws, data) {
  const { groupId, messageId } = data;
  const group = groups.get(groupId);
  if (!group) return;
  for (const memberId of group.members) {
    sendWSMessage(memberId, { type: 'group-message-deleted', groupId, messageId, deletedBy: fromId });
  }
}

// ============================================
// HTTP API СЕРВЕР (порт 3000)
// ============================================

const apiApp = express();
apiApp.use(cors());
apiApp.use(express.json({ limit: '25mb' }));

// Middleware авторизации
apiApp.use((req, res, next) => {
  const token = req.headers['authorization']?.replace('Bearer ', '');
  if (token) {
    const session = sessions.get(token);
    if (session && Date.now() - session.lastUsed < 24 * 60 * 60 * 1000) {
      session.lastUsed = Date.now();
      req.authUserId = session.userId;
    } else {
      sessions.delete(token);
    }
  }
  next();
});

// --- POST /register ---
apiApp.post('/register', (req, res) => {
  const userId = uuidv4();
  const sessionToken = generateSessionToken();
  users.set(userId, { lastSeen: Date.now(), online: true, sessionToken });
  sessions.set(sessionToken, { userId, createdAt: Date.now(), lastUsed: Date.now() });
  console.log(`🆕 Регистрация: ${userId.substring(0, 8)}`);
  res.json({ success: true, userId, sessionToken });
});

// --- POST /login ---
apiApp.post('/login', (req, res) => {
  const { phone } = req.body;
  const normalized = normalizePhone(phone);
  let userId = normalized ? phoneToUser.get(normalized) : null;
  
  if (!userId) {
    userId = uuidv4();
    if (normalized) phoneToUser.set(normalized, userId);
  }
  
  const sessionToken = generateSessionToken();
  sessions.set(sessionToken, { userId, createdAt: Date.now(), lastUsed: Date.now() });
  users.set(userId, { lastSeen: Date.now(), online: true, phone: normalized, sessionToken });
  
  console.log(`🔐 Вход: ${userId.substring(0, 8)} (телефон: ${phone})`);
  res.json({ success: true, userId, sessionToken });
});

// --- POST /send ---
apiApp.post('/send', (req, res) => {
  const { fromId, to, payload } = req.body;
  if (!fromId || !to) return res.status(400).json({ error: 'Missing fromId, to' });
  
  stats.totalMessages++;
  const msgPayload = payload || {};
  
  if (!offlineQueue.has(to)) offlineQueue.set(to, []);
  offlineQueue.get(to).push({ type: 'message', from: fromId, payload: msgPayload, timestamp: Date.now() });
  
  // Если получатель онлайн через WebSocket — отправляем сразу
  const toUser = users.get(to);
  if (toUser && toUser.ws && toUser.ws.readyState === WebSocket.OPEN) {
    toUser.ws.send(JSON.stringify({ type: 'message', from: fromId, payload: msgPayload }));
  }
  
  res.json({ type: 'message-sent', to, messageId: msgPayload.id || null, delivered: false, queued: true });
});

// --- GET /receive/:userId ---
apiApp.get('/receive/:userId', (req, res) => {
  const userId = req.params.userId;
  const queue = offlineQueue.get(userId) || [];
  if (queue.length > 0) {
    console.log(`📥 ${userId.substring(0, 8)} получил ${queue.length} сообщений`);
    offlineQueue.delete(userId);
  }
  res.json({ messages: queue, count: queue.length });
});

// --- DELETE /queue/:userId ---
apiApp.delete('/queue/:userId', (req, res) => {
  offlineQueue.delete(req.params.userId);
  res.json({ success: true });
});

// --- POST /heartbeat ---
apiApp.post('/heartbeat', (req, res) => {
  const { userId } = req.body;
  if (userId) {
    const user = users.get(userId);
    if (user) { user.lastSeen = Date.now(); user.online = true; }
  }
  res.json({ success: true });
});

// --- POST /find-user ---
apiApp.post('/find-user', (req, res) => {
  const { targetId } = req.body;
  if (!targetId) return res.status(400).json({ error: 'Missing targetId' });
  const targetUser = users.get(targetId);
  const isOnline = targetUser && targetUser.online && (Date.now() - targetUser.lastSeen < 30000);
  res.json({ type: 'user-found', userId: targetId, online: isOnline });
});

// --- POST /find-by-phone ---
apiApp.post('/find-by-phone', (req, res) => {
  const { phone } = req.body;
  const normalized = normalizePhone(phone);
  const foundUserId = normalized ? phoneToUser.get(normalized) : null;
  if (foundUserId) {
    const foundUser = users.get(foundUserId);
    const isOnline = foundUser?.online && (Date.now() - foundUser.lastSeen < 30000);
    res.json({ type: 'phone-found', userId: foundUserId, online: isOnline });
  } else {
    res.json({ type: 'phone-found', userId: null, online: false });
  }
});

// --- POST /sync-contacts ---
apiApp.post('/sync-contacts', (req, res) => {
  const { userId, contacts } = req.body;
  if (!contacts || !Array.isArray(contacts)) return res.json({ success: true, found: [] });
  const foundUsers = [];
  for (const contact of contacts) {
    const normalized = normalizePhone(contact.phone);
    if (normalized && phoneToUser.has(normalized)) {
      const fId = phoneToUser.get(normalized);
      if (fId !== userId) {
        const fUser = users.get(fId);
        foundUsers.push({
          userId: fId, phone: contact.phone, name: contact.name,
          online: fUser?.online && (Date.now() - fUser.lastSeen < 30000)
        });
      }
    }
  }
  res.json({ success: true, found: foundUsers });
});

// --- POST /register-phone ---
apiApp.post('/register-phone', (req, res) => {
  const { userId, phone } = req.body;
  const normalized = normalizePhone(phone);
  if (normalized) {
    phoneToUser.set(normalized, userId);
    const user = users.get(userId);
    if (user) user.phone = normalized;
  }
  res.json({ success: true });
});

// --- POST /typing ---
apiApp.post('/typing', (req, res) => {
  const { fromId, to } = req.body;
  if (to) {
    if (!offlineQueue.has(to)) offlineQueue.set(to, []);
    offlineQueue.get(to).push({ type: 'typing', from: fromId, timestamp: Date.now() });
  }
  res.json({ success: true });
});

// --- POST /signal ---
apiApp.post('/signal', (req, res) => {
  const { fromId, to, type, payload } = req.body;
  if (!to || !type) return res.status(400).json({ error: 'Missing to, type' });
  if (!offlineQueue.has(to)) offlineQueue.set(to, []);
  offlineQueue.get(to).push({ type: 'signal', signalType: type, from: fromId, payload, timestamp: Date.now() });
  const toUser = users.get(to);
  const delivered = toUser?.ws?.readyState === WebSocket.OPEN;
  if (delivered) toUser.ws.send(JSON.stringify({ type, from: fromId, payload }));
  res.json({ success: true, delivered });
});

// ============================================
// ГРУППЫ HTTP ENDPOINTS
// ============================================

apiApp.post('/groups/create', (req, res) => {
  const { creatorId, name, avatar, description, members } = req.body;
  const groupId = uuidv4();
  groups.set(groupId, {
    name: name || 'Группа', description: description || '', avatar: avatar || '👥',
    createdBy: creatorId, members: new Set([creatorId, ...(members || [])]), createdAt: Date.now()
  });
  saveGroups();
  for (const m of groups.get(groupId).members) {
    if (!offlineQueue.has(m)) offlineQueue.set(m, []);
    offlineQueue.get(m).push({
      type: 'group-created', groupId, name, avatar: avatar || '👥',
      description: description || '', createdBy: creatorId,
      members: Array.from(groups.get(groupId).members), timestamp: Date.now()
    });
  }
  res.json({ success: true, groupId });
});

apiApp.post('/groups/send', (req, res) => {
  const { fromId, groupId, payload } = req.body;
  const group = groups.get(groupId);
  if (!group) return res.status(404).json({ success: false, error: 'Группа не найдена' });
  if (!group.members.has(fromId)) return res.status(403).json({ success: false, error: 'Не участник' });
  let delivered = 0;
  for (const m of group.members) {
    if (m !== fromId) {
      if (!offlineQueue.has(m)) offlineQueue.set(m, []);
      offlineQueue.get(m).push({ type: 'group-message', groupId, payload: { ...payload, serverSenderId: fromId }, timestamp: Date.now() });
      delivered++;
    }
  }
  stats.totalMessages++;
  res.json({ success: true, delivered });
});

apiApp.post('/groups/invite', (req, res) => {
  const { fromId, groupId, userId } = req.body;
  const group = groups.get(groupId);
  if (!group) return res.status(404).json({ success: false, error: 'Группа не найдена' });
  if (group.members.has(userId)) return res.json({ success: true, message: 'Уже в группе' });
  group.members.add(userId);
  saveGroups();
  if (!offlineQueue.has(userId)) offlineQueue.set(userId, []);
  offlineQueue.get(userId).push({ type: 'group-invited', groupId, name: group.name, avatar: group.avatar, invitedBy: fromId, members: Array.from(group.members), timestamp: Date.now() });
  res.json({ success: true });
});

apiApp.post('/groups/leave', (req, res) => {
  const { userId, groupId } = req.body;
  const group = groups.get(groupId);
  if (!group) return res.status(404).json({ success: false, error: 'Группа не найдена' });
  group.members.delete(userId);
  saveGroups();
  if (group.members.size === 0) { groups.delete(groupId); saveGroups(); }
  res.json({ success: true });
});

apiApp.post('/groups/list', (req, res) => {
  const { userId } = req.body;
  const userGroups = [];
  for (const [gid, gd] of groups.entries()) {
    if (gd.members.has(userId)) {
      userGroups.push({ groupId: gid, name: gd.name, avatar: gd.avatar, description: gd.description, memberCount: gd.members.size, createdAt: gd.createdAt });
    }
  }
  res.json({ success: true, groups: userGroups });
});

apiApp.post('/groups/members', (req, res) => {
  const { groupId } = req.body;
  const group = groups.get(groupId);
  if (!group) return res.status(404).json({ success: false, error: 'Группа не найдена' });
  res.json({ success: true, members: Array.from(group.members), name: group.name });
});

apiApp.post('/groups/remove', (req, res) => {
  const { fromId, groupId, userIdToRemove } = req.body;
  const group = groups.get(groupId);
  if (!group) return res.status(404).json({ success: false, error: 'Группа не найдена' });
  group.members.delete(userIdToRemove);
  saveGroups();
  res.json({ success: true });
});

apiApp.post('/groups/update-role', (req, res) => {
  res.json({ success: true, message: 'Роль обновлена' });
});

apiApp.post('/groups/create-invite', (req, res) => {
  const { groupId } = req.body;
  res.json({ success: true, inviteLink: `${groupId}?invite=${uuidv4().substring(0, 8)}` });
});

apiApp.post('/groups/revoke-invite', (req, res) => {
  res.json({ success: true, message: 'Ссылка отозвана' });
});

apiApp.post('/groups/update', (req, res) => {
  const { groupId, name, avatar, description } = req.body;
  const group = groups.get(groupId);
  if (!group) return res.status(404).json({ success: false, error: 'Группа не найдена' });
  if (name) group.name = name;
  if (avatar) group.avatar = avatar;
  if (description !== undefined) group.description = description;
  saveGroups();
  res.json({ success: true });
});

apiApp.post('/groups/delete-message', (req, res) => {
  const { fromId, groupId, messageId } = req.body;
  const group = groups.get(groupId);
  if (!group) return res.status(404).json({ success: false, error: 'Группа не найдена' });
  for (const m of group.members) {
    if (!offlineQueue.has(m)) offlineQueue.set(m, []);
    offlineQueue.get(m).push({ type: 'group-message-deleted', groupId, messageId, deletedBy: fromId, timestamp: Date.now() });
  }
  res.json({ success: true });
});

// ============================================
// OTA ОБНОВЛЕНИЯ (порт 3001)
// ============================================

const statsApp = express();
statsApp.use(cors());
statsApp.use(express.json());

const UPDATES_DIR = path.join(__dirname, 'updates');

// Создаём папку updates если нет
if (!fs.existsSync(UPDATES_DIR)) {
  fs.mkdirSync(UPDATES_DIR, { recursive: true });
}

statsApp.get('/stats', (req, res) => {
  const onlineCount = [...users.values()].filter(u => u.online && Date.now() - u.lastSeen < 30000).length;
  res.json({
    connectedUsers: onlineCount,
    totalMessages: stats.totalMessages,
    uptime: Math.floor((Date.now() - stats.startTime) / 1000),
    offlineQueues: offlineQueue.size,
    registeredPhones: phoneToUser.size,
    groupsCount: groups.size
  });
});

statsApp.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: Date.now() });
});

statsApp.get('/api/version.json', (req, res) => {
  res.set({
    'Cache-Control': 'no-cache, no-store, must-revalidate',
    'Pragma': 'no-cache',
    'Expires': '0'
  });
  const versionPath = path.join(__dirname, 'version.json');
  if (fs.existsSync(versionPath)) {
    res.sendFile(versionPath);
  } else {
    res.status(404).json({ error: 'version.json not found' });
  }
});

statsApp.get('/updates/:filename', (req, res) => {
  const filePath = path.join(UPDATES_DIR, req.params.filename);
  if (fs.existsSync(filePath)) {
    res.sendFile(filePath);
  } else {
    res.status(404).json({ error: 'File not found' });
  }
});

statsApp.get('/downloads/app-release.apk', (req, res) => {
  const filePath = path.join(UPDATES_DIR, 'app-release.apk');
  if (fs.existsSync(filePath)) {
    res.sendFile(filePath);
  } else {
    res.status(404).json({ error: 'APK not found' });
  }
});

// ============================================
// АВТООЧИСТКА
// ============================================

setInterval(() => {
  const now = Date.now();
  users.forEach((user, userId) => {
    if (now - user.lastSeen > 60000) {
      user.online = false;
    }
  });
  
  // Очистка старых очередей (> 10 мин)
  offlineQueue.forEach((queue, userId) => {
    const valid = queue.filter(msg => now - (msg.timestamp || 0) < 600000);
    if (valid.length < queue.length) {
      offlineQueue.set(userId, valid);
    }
    if (valid.length === 0) offlineQueue.delete(userId);
  });
  
  // Очистка rate limit
  clientMessageCounts.clear();
  
  // Очистка старых сессий (> 24ч)
  sessions.forEach((session, token) => {
    if (now - session.lastUsed > 24 * 60 * 60 * 1000) sessions.delete(token);
  });
  
  // Очистка банов IP (> 1ч)
  bannedIPs.clear();
}, CLEANUP_INTERVAL);

// ============================================
// HEARTBEAT WEBSOCKET
// ============================================

const heartbeatInterval = setInterval(() => {
  wss.clients.forEach((ws) => {
    if (ws.isAlive === false) return ws.terminate();
    ws.isAlive = false;
    ws.ping();
  });
}, 30000);

// ============================================
// ЗАПУСК
// ============================================

async function start() {
  await initDatabase();
  
  // Создаём единый HTTP сервер для Express
  const httpServer = http.createServer(apiApp);
  
  // WebSocket через тот же HTTP сервер
  const wssNew = new WebSocket.Server({ server: httpServer });
  
  // Обработчики WebSocket
  wssNew.on('connection', (ws, req) => {
    const clientIP = req.socket.remoteAddress;
    const ipCount = connectionCountsByIP.get(clientIP) || 0;
    if (ipCount >= MAX_CONNECTIONS_PER_IP) { ws.close(1008, 'Too many connections'); return; }
    connectionCountsByIP.set(clientIP, ipCount + 1);
    
    const userId = uuidv4();
    console.log(`📱 WS клиент: ${userId.substring(0, 8)}`);
    users.set(userId, { ws, lastSeen: Date.now(), online: true });
    ws.send(JSON.stringify({ type: 'connected', userId, message: 'Подключено' }));
    
    ws.on('message', (message) => {
      try { handleWSMessage(userId, ws, JSON.parse(message)); } catch(e) { console.error('WS parse error:', e.message); }
    });
    ws.on('close', () => {
      const user = users.get(userId);
      if (user) { user.online = false; user.ws = null; }
      const c = connectionCountsByIP.get(clientIP) || 0;
      connectionCountsByIP.set(clientIP, Math.max(0, c - 1));
    });
    ws.isAlive = true;
    ws.on('pong', () => { ws.isAlive = true; });
  });
  
  // Запускаем единый сервер на порту 3000
  httpServer.listen(WS_PORT, '0.0.0.0', () => {
    console.log('🚀 ЕДИНИЦА Сервер запущен!');
    console.log(`📡 WebSocket: ws://0.0.0.0:${WS_PORT}`);
    console.log(`🌐 HTTP API: http://0.0.0.0:${WS_PORT}`);
    console.log(`📦 OTA: http://0.0.0.0:${HTTP_STATS_PORT}/updates/`);
    console.log(`💾 SQLite: ${DB_PATH}`);
    
    // Stats + OTA сервер на отдельном порту (после основного)
    const statsServer = http.createServer(statsApp);
    statsServer.listen(HTTP_STATS_PORT, '0.0.0.0', () => {
      console.log(`📊 HTTP Stats сервер: http://0.0.0.0:${HTTP_STATS_PORT}`);
    });
    statsServer.on('error', (err) => {
      console.error('❌ Ошибка запуска Stats сервера:', err.message);
    });
  });
}

start();

// ============================================
// ГЛОБАЛЬНАЯ ОБРАБОТКА ОШИБОК
// ============================================

process.on('uncaughtException', (err) => {
  console.error('💥 КРИТИЧЕСКАЯ ОШИБКА:', err.message);
  console.error(err.stack);
});

process.on('unhandledRejection', (reason) => {
  console.error('💥 НЕОБРАБОТАННЫЙ PROMISE:', reason);
});

// ============================================
// ЗАВЕРШЕНИЕ
// ============================================

process.on('SIGTERM', () => {
  console.log('👋 Завершение...');
  clearInterval(heartbeatInterval);
  if (db) db.close();
  process.exit(0);
});

process.on('SIGINT', () => {
  console.log('👋 Завершение...');
  clearInterval(heartbeatInterval);
  if (db) db.close();
  process.exit(0);
});
