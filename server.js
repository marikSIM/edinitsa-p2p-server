/**
 * P2P Сервер сигнализации для мессенджера ЕДИНИЦА
 * 
 * ВАЖНО: Этот сервер НЕ хранит сообщения!
 * Он только помогает устройствам найти друг друга и установить прямое соединение.
 * 
 * Запуск:
 * 1. npm install
 * 2. node server.js
 * 
 * Порт: 3000 (WebSocket) + 3001 (HTTP для статистики)
 */

const WebSocket = require('ws');
const http = require('http');
const express = require('express');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');

// ============================================
// КОНФИГУРАЦИЯ
// ============================================

const WS_PORT = 3000;
const HTTP_PORT = 3001;

// Хранилище подключённых пользователей
// Map<userId, WebSocket>
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
// WebSocket Сервер
// ============================================

const wss = new WebSocket.Server({ port: WS_PORT });

console.log(`🚀 P2P Сервер запущен!`);
console.log(`📡 WebSocket: ws://localhost:${WS_PORT}`);
console.log(`📊 HTTP Stats: http://localhost:${HTTP_PORT}`);

wss.on('connection', (ws) => {
  const userId = uuidv4();
  console.log(`📱 Новый клиент подключён: ${userId}`);

  // Регистрируем пользователя
  users.set(userId, ws);
  stats.connectedUsers = users.size;

  // Отправляем пользователю его ID
  ws.send(JSON.stringify({
    type: 'connected',
    userId: userId,
    message: 'Подключено к серверу сигнализации'
  }));

  // Обработка входящих сообщений
  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message);
      handleMessage(userId, ws, data);
    } catch (error) {
      console.error('Ошибка обработки сообщения:', error);
      ws.send(JSON.stringify({
        type: 'error',
        message: 'Неверный формат сообщения'
      }));
    }
  });

  // Обработка отключения
  ws.on('close', () => {
    console.log(`❌ Клиент отключён: ${userId}`);
    users.delete(userId);
    stats.connectedUsers = users.size;
  });

  // Обработка ошибок
  ws.on('error', (error) => {
    console.error(`Ошибка клиента ${userId}:`, error);
  });

  // Heartbeat (проверка соединения)
  ws.isAlive = true;
  ws.on('pong', () => {
    ws.isAlive = true;
  });
});

// ============================================
// Обработка сообщений
// ============================================

function handleMessage(fromId, ws, data) {
  const { type, to, payload } = data;

  console.log(`📨 Сообщение от ${fromId} к ${to}: ${type}`);

  switch (type) {
    // Поиск пользователя по ID
    case 'find-user':
      handleFindUser(fromId, to);
      break;

    // WebRTC Offer (предложение соединения)
    case 'offer':
      sendToUser(to, {
        type: 'offer',
        from: fromId,
        payload: payload
      });
      break;

    // WebRTC Answer (ответ)
    case 'answer':
      sendToUser(to, {
        type: 'answer',
        from: fromId,
        payload: payload
      });
      break;

    // ICE Candidate (сетевая информация)
    case 'ice-candidate':
      sendToUser(to, {
        type: 'ice-candidate',
        from: fromId,
        payload: payload
      });
      break;

    // Сообщение (через сервер если получатель офлайн)
    case 'message':
      stats.totalMessages++;
      sendMessage(fromId, to, payload);
      break;

    // Статус "печатает..."
    case 'typing':
      sendToUser(to, {
        type: 'typing',
        from: fromId
      });
      break;

    // Статус "онлайн"
    case 'online':
      broadcastOnlineStatus(fromId, true);
      break;

    // Статус "офлайн"
    case 'offline':
      broadcastOnlineStatus(fromId, false);
      break;

    default:
      ws.send(JSON.stringify({
        type: 'error',
        message: 'Неизвестный тип сообщения'
      }));
  }
}

// Поиск пользователя
function handleFindUser(fromId, targetId) {
  const targetWs = users.get(targetId);
  
  if (targetWs && targetWs.readyState === WebSocket.OPEN) {
    // Пользователь онлайн
    fromId && users.get(fromId)?.send(JSON.stringify({
      type: 'user-found',
      userId: targetId,
      online: true
    }));
  } else {
    // Пользователь офлайн
    fromId && users.get(fromId)?.send(JSON.stringify({
      type: 'user-found',
      userId: targetId,
      online: false
    }));
  }
}

// Отправка сообщения пользователю
function sendToUser(userId, message) {
  const ws = users.get(userId);
  
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(message));
  } else {
    // Пользователь офлайн - сохраняем в очередь
    if (!offlineQueue.has(userId)) {
      offlineQueue.set(userId, []);
    }
    offlineQueue.get(userId).push(message);
    console.log(`💾 Сообщение сохранено в очередь для ${userId}`);
  }
}

// Отправка сообщения (с очередью для офлайн)
function sendMessage(fromId, to, payload) {
  const toWs = users.get(to);
  
  if (toWs && toWs.readyState === WebSocket.OPEN) {
    toWs.send(JSON.stringify({
      type: 'message',
      from: fromId,
      payload: payload
    }));
    
    // Подтверждение доставки
    users.get(fromId)?.send(JSON.stringify({
      type: 'message-delivered',
      to: to,
      messageId: payload.id
    }));
  } else {
    // Сохраняем в очередь
    if (!offlineQueue.has(to)) {
      offlineQueue.set(to, []);
    }
    offlineQueue.get(to).push({
      type: 'message',
      from: fromId,
      payload: payload
    });
    
    console.log(`💾 Сообщение сохранено в очереди для ${to}`);
  }
}

// Трансляция статуса онлайн
function broadcastOnlineStatus(userId, isOnline) {
  // Можно расширить для трансляции контактам
  console.log(`🟢 ${userId} ${isOnline ? 'онлайн' : 'офлайн'}`);
}

// ============================================
// HTTP Сервер для статистики
// ============================================

const app = express();
app.use(cors());
app.use(express.json());

app.get('/stats', (req, res) => {
  res.json({
    ...stats,
    uptime: Math.floor((Date.now() - stats.startTime) / 1000),
    offlineQueues: offlineQueue.size
  });
});

app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: Date.now() });
});

// Получение очереди сообщений для пользователя
app.get('/queue/:userId', (req, res) => {
  const userId = req.params.userId;
  const queue = offlineQueue.get(userId) || [];
  res.json({ queue });
});

// Очистка очереди после получения
app.delete('/queue/:userId', (req, res) => {
  const userId = req.params.userId;
  offlineQueue.delete(userId);
  res.json({ success: true });
});

http.createServer(app).listen(HTTP_PORT, () => {
  console.log(`📊 HTTP сервер запущен на порту ${HTTP_PORT}`);
});

// ============================================
// Heartbeat (проверка живых соединений)
// ============================================

const interval = setInterval(() => {
  wss.clients.forEach((ws) => {
    if (ws.isAlive === false) {
      return ws.terminate();
    }
    ws.isAlive = false;
    ws.ping();
  });
}, 30000);

wss.on('close', () => {
  clearInterval(interval);
});

// ============================================
// Обработка завершения работы
// ============================================

process.on('SIGTERM', () => {
  console.log('👋 Завершение работы сервера...');
  wss.close();
  process.exit(0);
});

process.on('SIGINT', () => {
  console.log('👋 Завершение работы сервера...');
  wss.close();
  process.exit(0);
});
