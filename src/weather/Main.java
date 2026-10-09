package weather;

import weather.exception.WeatherException;
import weather.model.Reading;
import weather.service.*;
import weather.ui.MainFrame;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Entry point.
 *   java -cp out weather.Main            -> Swing GUI
 *   java -cp out weather.Main --demo 5   -> headless: simulate for 5 seconds, print a report
 */
public class Main {
    public static void main(String[] args) throws Exception {
        WeatherStation station = new WeatherStation();
        seed(station);
        Simulator sim = new Simulator(station);

        boolean demo = args.length > 0 && args[0].equals("--demo");

        if (demo || GraphicsEnvironment.isHeadless()) {
            int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 5;
            runDemo(station, sim, seconds);

            return;
        }

        SwingUtilities.invokeLater(() -> new MainFrame(station, sim).setVisible(true));
    }

    private static void seed(WeatherStation st) throws WeatherException {
        st.addSensor(SensorFactory.fromText("TEMPERATURE", "T1", "30", "Roof"));
        st.addSensor(SensorFactory.fromText("HUMIDITY",    "H1", "70", "Garden"));
        st.addSensor(SensorFactory.fromText("RAINFALL",    "R1", "20", "Field"));
        st.addSensor(SensorFactory.fromText("PRESSURE",    "P1", "1020", "Lab"));
        st.addSensor(SensorFactory.fromText("WIND",        "W1", "40", "Tower"));
    }

    private static void runDemo(WeatherStation st, Simulator sim, int seconds) throws Exception {
        st.addListener(new WeatherListener() {
            @Override
            public void onAlert(weather.model.Alert a) {
                System.out.println(a);
            }
        });

        sim.setIntervalMillis(200);
        sim.start();
        Thread.sleep(seconds * 1000L);
        sim.stop();
        sim.drain(2000);
        List<Reading> all = st.allReadings();
        Map<String, Double> limits = st.sensors().stream()
                .collect(Collectors.toMap(s -> s.id(), s -> s.limit()));
        System.out.println(StatisticsService.report(all, limits));
        sim.close();
    }
}
