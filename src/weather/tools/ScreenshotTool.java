package weather.tools;

import weather.exception.WeatherException;
import weather.model.Reading;
import weather.model.Sensor;
import weather.service.SensorFactory;
import weather.service.Simulator;
import weather.service.WeatherStation;
import weather.ui.MainFrame;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Random;

/**
 * Developer tool (not part of the application): drives the REAL Swing GUI exactly as a user would
 * - typing into fields, clicking buttons, waiting for the simulator thread - and paints each state
 * of the window into a PNG for the project report.
 *
 *   java -cp out weather.tools.ScreenshotTool screenshots
 */
public class ScreenshotTool {

    private static final int SCALE = 2;                 // 2x for crisp text in the PDF
    private static final int TITLE_BAR = 30;

    private static File outDir;
    private static MainFrame frame;
    private static int shotNumber = 0;

    public static void main(String[] args) throws Exception {
        outDir = new File(args.length > 0 ? args[0] : "screenshots");
        outDir.mkdirs();

        WeatherStation station = new WeatherStation();
        seedSensors(station);
        seedHistory(station);

        Simulator simulator = new Simulator(station, new Random(11));

        SwingUtilities.invokeAndWait(() -> {
            frame = new MainFrame(station, simulator);
            frame.setSize(1040, 680);
            frame.setLocation(60, 60);
            frame.setVisible(true);
        });

        pause(800);

        // 1. dashboard with registered sensors and stored history
        shot("main_window");

        // 2. Sensor registration (Create)
        type("idField", "R2");
        select("typeBox", "RAINFALL");
        type("limitField", "25");
        type("locField", "Hill");
        click("Add");
        pause(300);
        shot("sensor_registration");

        // 3. Validation + exception handling: limit outside the legal range of the type
        type("idField", "T9");
        select("typeBox", "TEMPERATURE");
        type("limitField", "500");
        type("locField", "Shed");
        showDialogAfterClick("Add", "validation_error_limit");

        // duplicate id -> DuplicateSensorException
        type("idField", "T1");
        select("typeBox", "TEMPERATURE");
        type("limitField", "30");
        showDialogAfterClick("Add", "validation_error_duplicate");

        // bad sensor id characters
        type("idField", "bad id!");
        showDialogAfterClick("Add", "validation_error_id");

        // 4. Update (limit of T1 lowered so that live readings cross the threshold)
        type("idField", "T1");
        type("limitField", "18");
        click("Update limit");
        pause(300);
        shot("update_limit");

        // 5. Continuous multi-sensor logging on a background thread + threshold alerts
        click("Start");
        pause(3500);
        shot("live_logging_running");

        pause(2500);
        shot("threshold_alerts");
        click("Stop");
        pause(300);

        // 6. Sensor search by id, then by date, then by both
        type("idField", "T1");
        type("dateField", "");
        click("Search");
        pause(300);
        shot("search_by_sensor");

        type("idField", "");
        type("dateField", LocalDate.now().minusDays(1).toString());
        click("Search");
        pause(300);
        shot("search_by_date");

        type("idField", "H1");
        type("dateField", LocalDate.now().toString());
        click("Search");
        pause(300);
        shot("search_by_sensor_and_date");

        // invalid date -> validation message
        type("dateField", "09/10/2026");
        showDialogAfterClick("Search", "validation_error_date");
        type("dateField", "");

        // 7. Sorting
        click("Sort by value");
        pause(300);
        shot("sort_by_value");

        click("Sort by time");
        pause(300);
        shot("sort_by_timestamp");

        // 8. Weather report
        click("Weather report");
        pause(300);
        shot("weather_report");

        // 9. Hourly snapshot chart (array of 24 hourly averages)
        selectTab(2);
        select("chartType", "TEMPERATURE");
        pause(300);
        shot("hourly_chart_temperature");

        select("chartType", "HUMIDITY");
        pause(300);
        shot("hourly_chart_humidity");

        // 10. Delete (readings of one sensor, then the sensor itself)
        selectTab(0);
        type("idField", "P1");
        click("Delete readings");
        pause(300);
        shot("delete_readings");

        type("idField", "R2");
        click("Delete");
        pause(300);
        shot("delete_sensor");

        System.out.println("Saved " + shotNumber + " screenshots to " + outDir.getAbsolutePath());
        simulator.close();
        System.exit(0);
    }

    // ------------------------------------------------------------------ test data

    private static void seedSensors(WeatherStation st) throws WeatherException {
        add(st, "TEMPERATURE", "T1", "30", "Roof");
        add(st, "HUMIDITY", "H1", "75", "Garden");
        add(st, "RAINFALL", "R1", "20", "Field");
        add(st, "PRESSURE", "P1", "1020", "Lab");
        add(st, "WIND", "W1", "40", "Tower");
    }

    private static void add(WeatherStation st, String type, String id, String limit, String loc)
            throws WeatherException {
        st.addSensor(SensorFactory.fromText(type, id, limit, loc));
    }

    /** Two days of past readings, one round every 40 minutes, so search-by-date and the chart have data. */
    private static void seedHistory(WeatherStation st) {
        Random rnd = new Random(7);
        Instant now = Instant.now();

        for (int minutesAgo = 48 * 60; minutesAgo > 30; minutesAgo -= 40) {
            Instant when = now.minus(Duration.ofMinutes(minutesAgo));
            int hour = when.atZone(ZoneId.systemDefault()).getHour();

            for (Sensor s : st.sensors()) {
                st.record(new Reading(s.id(), s.type(), s.sample(rnd, hour), when));
            }
        }
    }

    // ------------------------------------------------------------------ GUI driving helpers

    private static void click(String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton b = findButton(frame, text);

            if (b == null) {
                throw new IllegalStateException("No button " + text);
            }

            b.doClick();
        });
    }

    private static void type(String name, String value) throws Exception {
        SwingUtilities.invokeAndWait(() -> ((JTextField) find(frame, name)).setText(value));
    }

    private static void select(String name, String value) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            @SuppressWarnings("unchecked")
            JComboBox<Object> box = (JComboBox<Object>) find(frame, name);

            for (int i = 0; i < box.getItemCount(); i++) {
                if (box.getItemAt(i).toString().equals(value)) {
                    box.setSelectedIndex(i);
                }
            }
        });
    }

    private static void selectTab(int index) throws Exception {
        SwingUtilities.invokeAndWait(() -> ((JTabbedPane) find(frame, "tabs")).setSelectedIndex(index));
        pause(200);
    }

    /** Clicks a button that opens a modal dialog, captures the dialog, then closes it. */
    private static void showDialogAfterClick(String buttonText, String shotName) throws Exception {
        SwingUtilities.invokeLater(() -> findButton(frame, buttonText).doClick());   // blocks the EDT inside the dialog

        JDialog dialog = null;

        for (int i = 0; i < 50 && dialog == null; i++) {
            pause(100);

            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing()) {
                    dialog = d;
                }
            }
        }

        if (dialog == null) {
            throw new IllegalStateException("dialog did not appear for " + shotName);
        }

        pause(300);
        BufferedImage img = renderWindow(dialog, false);
        save(img, shotName);

        JDialog toClose = dialog;
        SwingUtilities.invokeAndWait(toClose::dispose);
        pause(200);
    }

    private static Component find(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) {
                return c;
            }

            if (c instanceof Container child) {
                Component found = find(child, name);

                if (found != null) {
                    return found;
                }
            }
        }

        if (root instanceof JFrame f) {
            return find(f.getRootPane(), name);
        }

        return null;
    }

    private static JButton findButton(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && text.equals(b.getText())) {
                return b;
            }

            if (c instanceof Container child) {
                JButton found = findButton(child, text);

                if (found != null) {
                    return found;
                }
            }
        }

        if (root instanceof JFrame f) {
            return findButton(f.getRootPane(), text);
        }

        return null;
    }

    private static void pause(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    // ------------------------------------------------------------------ rendering

    private static void shot(String name) throws Exception {
        final BufferedImage[] holder = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = renderWindow(frame, true));
        save(holder[0], name);
    }

    private static void save(BufferedImage img, String name) throws Exception {
        shotNumber++;
        File f = new File(outDir, String.format("%02d_%s.png", shotNumber, name));
        ImageIO.write(img, "png", f);
        System.out.println("  " + f.getName());
    }

    /** Paints the Swing content of a window plus a drawn title bar into an image. */
    private static BufferedImage renderWindow(Window window, boolean showMenuBar) {
        JRootPane root = ((RootPaneContainer) window).getRootPane();
        int w = root.getWidth();
        int h = root.getHeight();

        BufferedImage img = new BufferedImage(w * SCALE, (h + TITLE_BAR) * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.scale(SCALE, SCALE);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // window body
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h + TITLE_BAR);

        // title bar
        g.setPaint(new GradientPaint(0, 0, new Color(236, 236, 236), 0, TITLE_BAR, new Color(214, 214, 214)));
        g.fillRect(0, 0, w, TITLE_BAR);
        g.setColor(new Color(170, 170, 170));
        g.drawLine(0, TITLE_BAR - 1, w, TITLE_BAR - 1);

        Color[] dots = {new Color(255, 95, 86), new Color(255, 189, 46), new Color(39, 201, 63)};

        for (int i = 0; i < dots.length; i++) {
            g.setColor(dots[i]);
            g.fill(new RoundRectangle2D.Double(12 + i * 20, 9, 12, 12, 12, 12));
        }

        String title = window instanceof Frame f ? f.getTitle() : ((Dialog) window).getTitle();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(60, 60, 60));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (w - fm.stringWidth(title)) / 2, 20);

        // the real Swing content
        g.translate(0, TITLE_BAR);
        root.paint(g);

        g.dispose();

        return img;
    }
}
