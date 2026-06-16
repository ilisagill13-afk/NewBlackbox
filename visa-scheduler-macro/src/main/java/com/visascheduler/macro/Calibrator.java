package com.visascheduler.macro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time interactive setup: takes a full-screen screenshot, shows it in a
 * window, and asks the user to click the top-left then bottom-right corner
 * of the area on their AIS appointment page that should be watched for
 * changes (typically the calendar / "no appointments available" message area).
 *
 * Run with: java -jar visa-scheduler-macro-1.0.0.jar --calibrate
 *
 * Before running this, open your real Chrome, log in to AIS, and navigate to
 * the appointment/reschedule page so the screenshot captures the right state.
 */
public final class Calibrator {

    private static final Logger log = LoggerFactory.getLogger(Calibrator.class);

    private Calibrator() {}

    public static void run() throws Exception {
        System.out.println();
        System.out.println("=== Visa Macro Monitor — Calibration ===");
        System.out.println("1. Make sure your Chrome window with the AIS appointment page");
        System.out.println("   is open and VISIBLE on screen right now.");
        System.out.println("2. A screenshot window will open in 3 seconds.");
        System.out.println("3. Click the TOP-LEFT corner of the calendar/appointment area,");
        System.out.println("   then click the BOTTOM-RIGHT corner.");
        System.out.println("4. The window closes automatically after the 2nd click.");
        System.out.println();
        Thread.sleep(3000);

        Robot robot = new Robot();
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage screenshot = robot.createScreenCapture(screenRect);

        List<Point> clicks = new ArrayList<>();
        JFrame frame = new JFrame(
                "Click TOP-LEFT then BOTTOM-RIGHT of the appointment area (closes after 2 clicks)");
        JLabel label = new JLabel(new ImageIcon(screenshot));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clicks.add(e.getPoint());
                System.out.println("Recorded click #" + clicks.size() + " at: "
                        + e.getPoint().x + "," + e.getPoint().y);
                if (clicks.size() >= 2) {
                    frame.dispose();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(label);
        frame.setContentPane(scrollPane);
        frame.setSize(Math.min(1400, screenRect.width), Math.min(900, screenRect.height));
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        while (frame.isVisible()) {
            Thread.sleep(150);
        }

        if (clicks.size() < 2) {
            System.out.println("Calibration cancelled — need exactly 2 clicks. Run again.");
            return;
        }

        Point p1 = clicks.get(0);
        Point p2 = clicks.get(1);
        int x = Math.min(p1.x, p2.x);
        int y = Math.min(p1.y, p2.y);
        int w = Math.abs(p2.x - p1.x);
        int h = Math.abs(p2.y - p1.y);

        if (w < 20 || h < 20) {
            System.out.println("Region too small (" + w + "x" + h + ") — calibration aborted.");
            return;
        }

        MacroConfig.saveCalibration(x, y, w, h);
        System.out.println();
        System.out.println("Saved region: x=" + x + " y=" + y + " w=" + w + " h=" + h);
        System.out.println("calibration.properties written. You can now run the monitor normally:");
        System.out.println("  java -jar visa-scheduler-macro-1.0.0.jar");
        log.info("Calibration complete: x={} y={} w={} h={}", x, y, w, h);
    }
}
