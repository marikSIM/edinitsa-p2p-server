package com.example.simplechat.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Утилита для чтения контактов телефона
 */
public class ContactHelper {

    private static final String TAG = "ContactHelper";

    /**
     * Класс для представления контакта
     */
    public static class Contact {
        public String name;
        public String phone;

        public Contact(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }

        public JSONObject toJSON() {
            try {
                JSONObject json = new JSONObject();
                json.put("name", name);
                json.put("phone", phone);
                return json;
            } catch (Exception e) {
                Log.e(TAG, "Ошибка создания JSON", e);
                return null;
            }
        }
    }

    /**
     * Получить все контакты из телефонной книги
     */
    public static List<Contact> getAllContacts(Context context) {
        List<Contact> contacts = new ArrayList<>();

        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = new String[] {
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, projection, null, null, null);
            
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIndex);
                    String phone = cursor.getString(phoneIndex);

                    // Пропускаем контакты без номера
                    if (phone == null || phone.trim().isEmpty()) {
                        continue;
                    }

                    // Очищаем номер от лишних символов
                    phone = phone.replaceAll("[\\s\\-\\(\\)]", "");

                    contacts.add(new Contact(name, phone));
                }
            }

            Log.d(TAG, "Найдено контактов: " + contacts.size());
        } catch (Exception e) {
            Log.e(TAG, "Ошибка чтения контактов", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return contacts;
    }

    /**
     * Получить контакты в формате JSON для отправки на сервер
     */
    public static List<JSONObject> getContactsAsJSON(Context context) {
        List<Contact> contacts = getAllContacts(context);
        List<JSONObject> jsonContacts = new ArrayList<>();

        for (Contact contact : contacts) {
            JSONObject json = contact.toJSON();
            if (json != null) {
                jsonContacts.add(json);
            }
        }

        return jsonContacts;
    }

    /**
     * Проверить, есть ли разрешение на чтение контактов
     */
    public static boolean hasContactPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED;
    }
}
