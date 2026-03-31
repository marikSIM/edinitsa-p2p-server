package com.example.simplechat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<Chat> chats;
    private OnChatClickListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy", Locale.getDefault());

    public interface OnChatClickListener {
        void onChatClick(Chat chat);
    }

    public ChatAdapter(List<Chat> chats, OnChatClickListener listener) {
        this.chats = chats;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        Chat chat = chats.get(position);
        holder.bind(chat);
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }

    public void updateChat(int position, Chat chat) {
        chats.set(position, chat);
        notifyItemChanged(position);
    }

    public void addChat(Chat chat) {
        chats.add(0, chat);
        notifyItemInserted(0);
    }

    public Chat getChatAt(int position) {
        return chats.get(position);
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {
        private final TextView chatAvatar;
        private final TextView chatName;
        private final TextView chatLastMessage;
        private final TextView chatTime;
        private final TextView unreadCount;
        private final View onlineIndicator;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            chatAvatar = itemView.findViewById(R.id.chatAvatar);
            chatName = itemView.findViewById(R.id.chatName);
            chatLastMessage = itemView.findViewById(R.id.chatLastMessage);
            chatTime = itemView.findViewById(R.id.chatTime);
            unreadCount = itemView.findViewById(R.id.unreadCount);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
        }

        void bind(Chat chat) {
            chatAvatar.setText(chat.getAvatar());
            chatName.setText(chat.getName());
            
            // Обрезаем длинное последнее сообщение
            String lastMessage = chat.getLastMessage();
            if (lastMessage != null && lastMessage.length() > 50) {
                lastMessage = lastMessage.substring(0, 47) + "...";
            }
            chatLastMessage.setText(lastMessage != null ? lastMessage : "");
            
            // Форматирование времени
            long now = System.currentTimeMillis();
            long diff = now - chat.getLastMessageTime();
            if (diff < 24 * 60 * 60 * 1000) {
                chatTime.setText(timeFormat.format(new Date(chat.getLastMessageTime())));
            } else {
                chatTime.setText(dateFormat.format(new Date(chat.getLastMessageTime())));
            }

            // Индикатор онлайн
            onlineIndicator.setVisibility(chat.isOnline() ? View.VISIBLE : View.GONE);

            // Счётчик непрочитанных
            if (chat.getUnreadCount() > 0) {
                unreadCount.setVisibility(View.VISIBLE);
                unreadCount.setText(String.valueOf(chat.getUnreadCount()));
            } else {
                unreadCount.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onChatClick(chat);
                }
            });
        }
    }
}
