package com.classdrop.service;

import io.nayuki.qrcodegen.QrCode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class QrService {

    public BufferedImage generateQr(String url, int size) {
        Objects.requireNonNull(url, "url cannot be null");
        QrCode qr = QrCode.encodeText(url, QrCode.Ecc.MEDIUM);
        int border = 2;
        int qrSize = qr.size;
        int totalModules = qrSize + border * 2;
        int scale = Math.max(1, size / totalModules);
        int imgSize = totalModules * scale;

        BufferedImage image = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_RGB);
        int white = 0xFFFFFF;
        int black = 0x000000;

        for (int y = 0; y < imgSize; y++) {
            for (int x = 0; x < imgSize; x++) {
                int moduleX = (x / scale) - border;
                int moduleY = (y / scale) - border;
                boolean isDark = moduleX >= 0 && moduleX < qrSize && moduleY >= 0 && moduleY < qrSize
                        && qr.getModule(moduleX, moduleY);
                image.setRGB(x, y, isDark ? black : white);
            }
        }
        return image;
    }

    public byte[] generateQrPngBytes(String url, int size) throws IOException {
        BufferedImage image = generateQr(url, size);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    public void saveQrPng(String url, Path outputPath, int size) throws IOException {
        byte[] bytes = generateQrPngBytes(url, size);
        Files.write(outputPath, bytes);
    }
}
