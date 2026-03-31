package com.example.simplechat.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Hashtable;

/**
 * Утилита для генерации QR-кодов
 */
public class QRCodeGenerator {

    /**
     * Сгенерировать QR-код из строки
     * @param content Содержимое QR-кода
     * @param size Размер в пикселях
     * @return Bitmap с QR-кодом
     */
    public static Bitmap generateQRCode(String content, int size) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            
            Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
            
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            
            return bitmap;
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Сгенерировать QR-код для userId
     * @param userId ID пользователя
     * @param size Размер в пикселях
     * @return Bitmap с QR-кодом
     */
    public static Bitmap generateUserQRCode(String userId, int size) {
        // Формируем строку для QR-кода: edinitsa://user?userId=xxx
        String content = "edinitsa://user?userId=" + userId;
        return generateQRCode(content, size);
    }
}
