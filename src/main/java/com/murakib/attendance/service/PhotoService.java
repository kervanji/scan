package com.murakib.attendance.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.murakib.attendance.config.AppPaths;
import com.murakib.attendance.model.EventType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PhotoService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("HH-mm-ss");

    public BufferedImage generateQrImage(String content, int size) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    public Path saveAttendancePhoto(BufferedImage image, String employeeCode, EventType eventType, LocalDateTime time) throws Exception {
        LocalDate date = time.toLocalDate();
        Path folder = AppPaths.photosForDate(date);
        Files.createDirectories(folder);

        String fileName = employeeCode + "_" + eventType.name() + "_" + time.format(FILE_TIME) + ".jpg";
        Path filePath = folder.resolve(fileName);
        ImageIO.write(image, "jpg", filePath.toFile());
        return filePath;
    }

    public Path saveQrCard(BufferedImage qrImage, String employeeCode) throws Exception {
        Path folder = AppPaths.config().resolve("qr_cards");
        Files.createDirectories(folder);
        Path filePath = folder.resolve(employeeCode + "_qr.png");
        ImageIO.write(qrImage, "png", filePath.toFile());
        return filePath;
    }
}
