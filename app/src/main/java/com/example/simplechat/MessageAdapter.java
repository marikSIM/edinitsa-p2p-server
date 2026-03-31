package com.example.simplechat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private static final int VIEW_TYPE_MEDIA_SENT = 3;
    private static final int VIEW_TYPE_MEDIA_RECEIVED = 4;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private List<Message> messages;
    private OnMessageClickListener listener;

    public interface OnMessageClickListener {
        void onMessageClick(Message message);
    }

    public MessageAdapter(List<Message> messages, OnMessageClickListener listener) {
        this.messages = messages;
        this.listener = listener;
    }

    public MessageAdapter(List<Message> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        boolean hasMedia = message.getMediaPath() != null && !message.getMediaPath().isEmpty();
        
        if (hasMedia) {
            return message.isSentByMe() ? VIEW_TYPE_MEDIA_SENT : VIEW_TYPE_MEDIA_RECEIVED;
        } else {
            return message.isSentByMe() ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_SENT) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_sent, parent, false);
        } else if (viewType == VIEW_TYPE_RECEIVED) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_received, parent, false);
        } else if (viewType == VIEW_TYPE_MEDIA_SENT) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_media, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_media, parent, false);
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.messageText.setText(message.getText());
        holder.messageTime.setText(timeFormat.format(new Date(message.getTimestamp())));
        
        // Отображение медиа
        if (holder.mediaImage != null && holder.mediaText != null) {
            if (message.getMediaPath() != null && !message.getMediaPath().isEmpty()) {
                File mediaFile = new File(message.getMediaPath());
                if (mediaFile.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(mediaFile.getAbsolutePath());
                    holder.mediaImage.setImageBitmap(bitmap);
                    holder.mediaImage.setVisibility(View.VISIBLE);
                    holder.mediaText.setVisibility(View.GONE);
                    holder.messageText.setVisibility(message.getText() != null && !message.getText().isEmpty() ? View.VISIBLE : View.GONE);
                } else {
                    holder.mediaImage.setVisibility(View.GONE);
                    holder.mediaText.setVisibility(View.VISIBLE);
                    holder.mediaText.setText(message.getText());
                    holder.messageText.setVisibility(View.GONE);
                }
            } else {
                holder.mediaImage.setVisibility(View.GONE);
                holder.mediaText.setVisibility(View.GONE);
                holder.messageText.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView messageTime;
        ImageView mediaImage;
        TextView mediaText;

        ViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            messageTime = itemView.findViewById(R.id.messageTime);
            mediaImage = itemView.findViewById(R.id.mediaImage);
            mediaText = itemView.findViewById(R.id.mediaText);
        }
    }
}
