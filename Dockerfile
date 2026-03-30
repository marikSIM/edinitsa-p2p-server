# Dockerfile для P2P сервера мессенджера ЕДИНИЦА
FROM node:18-alpine

# Рабочая директория
WORKDIR /app

# Копируем package.json и устанавливаем зависимости
COPY package.json ./
RUN npm install --only=production

# Копируем сервер
COPY server-render.js ./

# Порты
EXPOSE 3000
EXPOSE 3001

# Запуск сервера
CMD ["node", "server-render.js"]
