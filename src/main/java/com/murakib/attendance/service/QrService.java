package com.murakib.attendance.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.awt.image.BufferedImage;
import java.util.Optional;

public class QrService {

    private final MultiFormatReader reader = new MultiFormatReader();

    public Optional<String> decode(BufferedImage image) {
        if (image == null) {
            return Optional.empty();
        }
        try {
            BinaryBitmap bitmap = new BinaryBitmap(
                    new HybridBinarizer(new BufferedImageLuminanceSource(image))
            );
            Result result = reader.decode(bitmap);
            return Optional.ofNullable(result.getText()).map(String::trim);
        } catch (NotFoundException e) {
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
