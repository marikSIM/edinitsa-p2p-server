package com.example.simplechat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Адаптер для списка контактов
 */
public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {

    private List<ContactItem> contacts = new ArrayList<>();
    private OnContactClickListener listener;

    public interface OnContactClickListener {
        void onContactClick(ContactItem contact);
        void onMessageClick(ContactItem contact);
    }

    public ContactAdapter(OnContactClickListener listener) {
        this.listener = listener;
    }

    public static class ContactItem {
        public String name;
        public String phone;
        public String userId;
        public boolean isInApp;  // Пользователь в приложении
        public boolean isOnline; // Онлайн сейчас

        public ContactItem(String name, String phone, String userId, boolean isInApp, boolean isOnline) {
            this.name = name;
            this.phone = phone;
            this.userId = userId;
            this.isInApp = isInApp;
            this.isOnline = isOnline;
        }

        public static ContactItem fromJSON(JSONObject json) {
            try {
                String name = json.optString("name", "Контакт");
                String phone = json.optString("phone", "");
                String userId = json.optString("userId", "");
                boolean isOnline = json.optBoolean("online", false);
                
                return new ContactItem(name, phone, userId, true, isOnline);
            } catch (Exception e) {
                return null;
            }
        }

        // Первая буква имени для аватара
        public String getAvatarLetter() {
            if (name != null && !name.isEmpty()) {
                return name.substring(0, 1).toUpperCase();
            }
            return "?";
        }
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        ContactItem contact = contacts.get(position);
        holder.bind(contact);
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    public void setContacts(List<ContactItem> contacts) {
        this.contacts = contacts;
        notifyDataSetChanged();
    }

    public void addContacts(List<ContactItem> contacts) {
        this.contacts.addAll(contacts);
        notifyDataSetChanged();
    }

    public void clear() {
        contacts.clear();
        notifyDataSetChanged();
    }

    public List<ContactItem> getInAppContacts() {
        List<ContactItem> result = new ArrayList<>();
        for (ContactItem contact : contacts) {
            if (contact.isInApp) {
                result.add(contact);
            }
        }
        return result;
    }

    class ContactViewHolder extends RecyclerView.ViewHolder {
        private TextView avatarText;
        private View avatarView;
        private TextView contactNameText;
        private TextView phoneText;
        private ImageView statusIcon;
        private Button actionButton;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarText = itemView.findViewById(R.id.avatarText);
            avatarView = itemView.findViewById(R.id.avatarView);
            contactNameText = itemView.findViewById(R.id.contactNameText);
            phoneText = itemView.findViewById(R.id.phoneText);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            actionButton = itemView.findViewById(R.id.actionButton);
        }

        public void bind(ContactItem contact) {
            // Аватар - первая буква
            avatarText.setText(contact.getAvatarLetter());

            // Имя
            contactNameText.setText(contact.name);

            // Телефон
            phoneText.setText(contact.phone);

            // Статус
            if (contact.isInApp) {
                statusIcon.setVisibility(View.VISIBLE);
                statusIcon.setImageResource(R.drawable.ic_check);
                
                if (contact.isOnline) {
                    actionButton.setVisibility(View.VISIBLE);
                    actionButton.setText("Написать");
                } else {
                    actionButton.setVisibility(View.VISIBLE);
                    actionButton.setText("Написать");
                }
            } else {
                statusIcon.setVisibility(View.GONE);
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setText("Пригласить");
            }

            // Клики
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onContactClick(contact);
                }
            });

            actionButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMessageClick(contact);
                }
            });
        }
    }
}
