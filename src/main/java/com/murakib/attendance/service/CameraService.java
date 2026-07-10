package com.murakib.attendance.service;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class CameraService {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "camera-thread");
        t.setDaemon(true);
        return t;
    });

    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private OpenCVFrameGrabber grabber;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<BufferedImage> latestFrame = new AtomicReference<>();
    private Consumer<BufferedImage> frameConsumer;
    private int cameraIndex = 0;

    public void setCameraIndex(int index) {
        this.cameraIndex = index;
    }

    public boolean isRunning() {
        return running.get() && grabber != null;
    }

    public void start(Consumer<BufferedImage> onFrame) throws Exception {
        stop();
        frameConsumer = onFrame;

        grabber = new OpenCVFrameGrabber(cameraIndex);
        grabber.setImageWidth(640);
        grabber.setImageHeight(480);
        try {
            grabber.start();
            Frame testFrame = null;
            for (int i = 0; i < 10 && testFrame == null; i++) {
                testFrame = grabber.grab();
                if (testFrame == null) {
                    Thread.sleep(200);
                }
            }
            if (testFrame == null) {
                throw new IllegalStateException(cameraInitHint());
            }
        } catch (IllegalStateException e) {
            stop();
            throw e;
        } catch (Exception e) {
            stop();
            throw new IllegalStateException(cameraInitHint(), e);
        }
        running.set(true);

        executor.scheduleAtFixedRate(this::grabFrame, 0, 150, TimeUnit.MILLISECONDS);
    }

    private void grabFrame() {
        if (!running.get() || grabber == null) {
            return;
        }
        try {
            Frame frame = grabber.grab();
            if (frame == null) {
                return;
            }
            BufferedImage image = converter.convert(frame);
            if (image != null) {
                latestFrame.set(image);
                Consumer<BufferedImage> consumer = frameConsumer;
                if (consumer != null) {
                    consumer.accept(image);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void stop() {
        running.set(false);
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception ignored) {
            }
            grabber = null;
        }
    }

    public BufferedImage captureFrame() {
        BufferedImage frame = latestFrame.get();
        if (frame != null) {
            return frame;
        }
        if (grabber != null && running.get()) {
            try {
                Frame raw = grabber.grab();
                return converter.convert(raw);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public Image toFxImage(BufferedImage image) {
        if (image == null) {
            return null;
        }
        return SwingFXUtils.toFXImage(image, null);
    }

    public void shutdown() {
        stop();
        executor.shutdownNow();
    }

    private static String cameraInitHint() {
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            return "تعذّر تشغيل الكاميرا. على macOS: افتح إعدادات النظام ← الخصوصية والأمان ← الكاميرا، "
                    + "ثم فعّل الصلاحية لـ Terminal أو Cursor، وأعد تشغيل التطبيق.";
        }
        return "تعذّر تشغيل الكاميرا. تحقق من توصيل الكاميرا وصلاحيات الوصول.";
    }
}
