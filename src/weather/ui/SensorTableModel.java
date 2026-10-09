package weather.ui;

import weather.model.Reading;
import weather.model.Sensor;
import weather.service.WeatherStation;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CONCEPT: Swing MVC — a TableModel adapts the station data to the JTable (the view never owns data). */
public class SensorTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"ID", "Type", "Location", "Limit", "Latest", "Status"};

    private final WeatherStation station;
    private List<Sensor> rows = new ArrayList<>();
    private Map<String, Reading> latest = Map.of();

    public SensorTableModel(WeatherStation station) {
        this.station = station;
    }

    /** Must be called on the EDT. */
    public void refresh() {
        rows = new ArrayList<>(station.sensors());
        latest = station.latest();
        fireTableDataChanged();
    }

    public Sensor sensorAt(int row) {
        return row >= 0 && row < rows.size() ? rows.get(row) : null;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int c) {
        return COLUMNS[c];
    }

    @Override
    public Class<?> getColumnClass(int c) {
        return String.class;
    }

    @Override
    public Object getValueAt(int row, int col) {
        Sensor s = rows.get(row);
        Reading r = latest.get(s.id());

        return switch (col) {
            case 0 -> s.id();
            case 1 -> s.type() + " (" + s.describe() + ")";
            case 2 -> s.location();
            case 3 -> String.format("%.1f %s", s.limit(), s.type().unit());
            case 4 -> r == null ? "-" : r.formatted();
            case 5 -> r == null ? "waiting" : (r.exceeds(s.limit()) ? "ALERT" : "OK");
            default -> "";
        };
    }
}
