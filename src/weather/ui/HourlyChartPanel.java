package weather.ui;

import weather.model.SensorType;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

/** CONCEPT: custom painting — override paintComponent, Graphics2D, antialiasing, coordinate mapping. */
public class HourlyChartPanel extends JPanel {
    private double[] data = new double[24];
    private SensorType type = SensorType.TEMPERATURE;

    public HourlyChartPanel() {
        setPreferredSize(new Dimension(600, 300));
        setBackground(Color.WHITE);
        java.util.Arrays.fill(data, Double.NaN);
    }

    public void setData(SensorType type, double[] hourly) {
        this.type = type;
        this.data = hourly.clone();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int left = 50, right = 20, top = 30, bottom = 35;
            int pw = w - left - right, ph = h - top - bottom;

            g.setColor(Color.DARK_GRAY);
            g.drawString("Average " + type + " by hour of day (" + type.unit() + ")", left, 18);

            // scale the y-axis to the DATA (with 10% padding) so small variations stay visible
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;

            for (double v : data) {
                if (!Double.isNaN(v)) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
            }

            if (min > max) {                       // no data at all: fall back to the legal range
                min = type.min();
                max = type.max();
            }

            double pad = Math.max((max - min) * 0.1, 1.0);
            min -= pad;
            max += pad;

            g.setColor(new Color(225, 225, 225));

            for (int i = 0; i <= 4; i++) {
                int y = top + ph - (int) (ph * i / 4.0);
                g.drawLine(left, y, left + pw, y);
                g.setColor(Color.GRAY);
                g.drawString(String.format("%.1f", min + (max - min) * i / 4.0), 4, y + 4);
                g.setColor(new Color(225, 225, 225));
            }

            g.setColor(Color.GRAY);

            for (int hour = 0; hour < 24; hour += 3) {
                int x = left + (int) (pw * hour / 23.0);
                g.drawString(String.valueOf(hour), x - 4, h - 15);
            }

            g.drawLine(left, top, left, top + ph);
            g.drawLine(left, top + ph, left + pw, top + ph);

            Path2D.Double line = new Path2D.Double();
            boolean started = false;
            g.setColor(new Color(30, 110, 200));

            for (int hour = 0; hour < 24; hour++) {
                if (Double.isNaN(data[hour])) {
                    started = false;
                    continue;
                }

                double x = left + pw * hour / 23.0;
                double y = top + ph - ph * (data[hour] - min) / (max - min);

                if (!started) {
                    line.moveTo(x, y);
                    started = true;
                } else {
                    line.lineTo(x, y);
                }

                g.fillOval((int) x - 3, (int) y - 3, 6, 6);
            }

            g.setStroke(new BasicStroke(2f));
            g.draw(line);
        } finally {
            g.dispose();
        }
    }
}
