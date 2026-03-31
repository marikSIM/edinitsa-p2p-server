package com.example.simplechat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Адаптер для списка групп
 */
public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {

    private List<Group> groups;
    private OnGroupClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

    public interface OnGroupClickListener {
        void onGroupClick(Group group);
        void onGroupDelete(Group group);
    }

    public GroupAdapter(List<Group> groups, OnGroupClickListener listener) {
        this.groups = groups;
        this.listener = listener;
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        Group group = groups.get(position);
        holder.bind(group);
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    public void updateGroups(List<Group> newGroups) {
        groups.clear();
        groups.addAll(newGroups);
        notifyDataSetChanged();
    }

    public void removeGroup(int position) {
        groups.remove(position);
        notifyItemRemoved(position);
    }

    public Group getGroupAt(int position) {
        return groups.get(position);
    }

    class GroupViewHolder extends RecyclerView.ViewHolder {
        private final TextView groupAvatar;
        private final TextView groupName;
        private final TextView groupCreatedAt;
        private final TextView memberCount;
        private final ImageButton deleteButton;

        GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            groupAvatar = itemView.findViewById(R.id.groupAvatar);
            groupName = itemView.findViewById(R.id.groupName);
            groupCreatedAt = itemView.findViewById(R.id.groupCreatedAt);
            memberCount = itemView.findViewById(R.id.memberCount);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }

        void bind(Group group) {
            groupAvatar.setText(group.getAvatar());
            groupName.setText(group.getName());
            groupCreatedAt.setText("Создана: " + dateFormat.format(new Date(group.getCreatedAt())));
            memberCount.setText(group.getMemberCount() + " участник(ов)");

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGroupClick(group);
                }
            });

            deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGroupDelete(group);
                }
            });
        }
    }
}
