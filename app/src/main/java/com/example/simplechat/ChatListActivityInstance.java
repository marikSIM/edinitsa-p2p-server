package com.example.simplechat;

import com.example.simplechat.p2p.P2PClient;

/**
 * Синглтон для хранения экземпляра P2PClient
 * Используется для доступа из других Activity
 */
public class ChatListActivityInstance {
    
    private static P2PClient p2pClient;

    /**
     * Установить экземпляр P2PClient
     */
    public static void setInstance(P2PClient client) {
        p2pClient = client;
    }

    /**
     * Получить экземпляр P2PClient
     */
    public static P2PClient getInstance() {
        return p2pClient;
    }
}
