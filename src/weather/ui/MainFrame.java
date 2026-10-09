package weather.ui;

import weather.exception.WeatherException;
import weather.exception.StorageException;
import weather.model.Alert;
import weather.model.Reading;
import weather.model.Sensor;
import weather.model.SensorType;
import weather.service.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * The VIEW + CONTROLLER. Rules it follows:
 *  1. All Swing calls happen on the Event Dispatch Thread (EDT).
 *  2. Worker threads never touch components; they call listener methods which hop to the EDT.
 *  3. Bursts of events are COALESCED into one repaint (see scheduleRefresh).
 */
public class MainFrame extends JFrame implements WeatherListener {

    private final WeatherStation station;
    private final Simulator simulator;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "weather-io");
        t.setDaemon(true);

        return t;
        });

    private final JTextField idField = new JTextField(8);
    private final JTextField limitField = new JTextField(5);
    private final JTextField locField = new JTextField(8);
    private final JTextField dateField = new JTextField(9);
    private final JComboBox<SensorType> typeBox = new JComboBox<>(SensorType.values());
    private final JButton startStop = new JButton("Start");
    private final JLabel status = new JLabel(" Ready");

    private final SensorTableModel tableModel;
    private final JTable table;
    private final DefaultListModel<String> alertModel = new DefaultListModel<>();
    private final JTextArea output = new JTextArea(12, 60);
    private final HourlyChartPanel chart = new HourlyChartPanel();
    private final JComboBox<SensorType> chartType = new JComboBox<>(SensorType.values());

    private final JTabbedPane tabs = new JTabbedPane();

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AtomicBoolean refreshPending = new AtomicBoolean(false);

    public MainFrame(WeatherStation station, Simulator simulator) {
        super("Weather Station");
        this.station = station;
        this.simulator = simulator;
        this.tableModel = new SensorTableModel(station);
        this.table = new JTable(tableModel);

        buildUi();
        nameComponents();
        station.addListener(this);
        refreshNow();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {      // anonymous subclass of an adapter
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                shutdown();
            }
        });

        setSize(900, 620);
        setLocationRelativeTo(null);
    }

    /** Component names let the screenshot tool (and tests) find controls without reflection. */
    private void nameComponents() {
        idField.setName("idField");
        limitField.setName("limitField");
        locField.setName("locField");
        dateField.setName("dateField");
        typeBox.setName("typeBox");
        chartType.setName("chartType");
        table.setName("sensorTable");
        output.setName("output");
        tabs.setName("tabs");
    }

    // ------------------------------------------------------------------ layout
    private void buildUi() {
        setJMenuBar(buildMenu());

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        form.add(new JLabel("ID")); form.add(idField);
        form.add(new JLabel("Type")); form.add(typeBox);
        form.add(new JLabel("Limit")); form.add(limitField);
        form.add(new JLabel("Location")); form.add(locField);
        form.add(button("Add", this::onAdd));
        form.add(button("Update limit", this::onUpdateLimit));
        form.add(button("Delete", this::onDelete));
        typeBox.addActionListener(e -> limitField.setToolTipText(
                "Empty = default " + ((SensorType) typeBox.getSelectedItem()).defaultLimit()));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        startStop.addActionListener(e -> onStartStop());
        controls.add(startStop);
        controls.add(new JLabel("Date"));
        controls.add(dateField);
        controls.add(button("Search", this::onSearch));
        controls.add(button("Sort by value", this::onSort));
        controls.add(button("Sort by time", this::onSortByTime));
        controls.add(button("Weather report", this::onReport));
        controls.add(button("Delete readings", this::onDeleteReadings));
        controls.add(button("Clear all data", this::onClear));
        controls.add(new JLabel("  Interval (ms)"));
        JSlider slider = new JSlider(100, 3000, (int) simulator.intervalMillis());
        slider.setMajorTickSpacing(1000);
        slider.setPaintTicks(true);
        slider.addChangeListener(e -> {
            if (!slider.getValueIsAdjusting()) {
                simulator.setIntervalMillis(slider.getValue());
            }
        });

        controls.add(slider);

        JPanel north = new JPanel(new BorderLayout());
        north.add(form, BorderLayout.NORTH);
        north.add(controls, BorderLayout.SOUTH);

        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }

            int viewRow = table.getSelectedRow();

            if (viewRow < 0) {
                return;
            }

            Sensor s = tableModel.sensorAt(table.convertRowIndexToModel(viewRow));

            if (s != null) {
                idField.setText(s.id());
                limitField.setText(String.valueOf(s.limit()));
            }
        });

        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                     boolean focus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);

                if (!sel) {
                    Object st = t.getValueAt(r, 5);
                    comp.setForeground("ALERT".equals(st) ? new Color(190, 30, 30) : Color.BLACK);
                }

                return comp;
            }
        });

        JList<String> alertList = new JList<>(alertModel);
        JSplitPane dashboard = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), titled("Alerts (newest first)", new JScrollPane(alertList)));
        dashboard.setResizeWeight(0.6);

        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel chartTab = new JPanel(new BorderLayout());
        JPanel chartTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        chartTop.add(new JLabel("Type"));
        chartTop.add(chartType);
        chartType.addActionListener(e -> refreshChart());
        chartTab.add(chartTop, BorderLayout.NORTH);
        chartTab.add(chart, BorderLayout.CENTER);

        tabs.addTab("Dashboard", dashboard);
        tabs.addTab("Output", new JScrollPane(output));
        tabs.addTab("Hourly chart", chartTab);
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 2) refreshChart();
        });

        add(north, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(item("Save sensors...", this::onSaveSensors));
        file.add(item("Load sensors...", this::onLoadSensors));
        file.addSeparator();
        file.add(item("Export readings (CSV)...", this::onExport));
        file.addSeparator();
        file.add(item("Exit", this::shutdown));
        JMenu help = new JMenu("Help");
        help.add(item("About", () -> JOptionPane.showMessageDialog(this,
                "Weather Station case study\nOOP, collections, generics, streams,\n"
                        + "concurrency, I/O and Swing in one program.")));
        bar.add(file);
        bar.add(help);

        return bar;
    }

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());

        return b;
    }

    private static JMenuItem item(String text, Runnable action) {
        JMenuItem i = new JMenuItem(text);
        i.addActionListener(e -> action.run());

        return i;
    }

    private static JComponent titled(String title, JComponent c) {
        c.setBorder(BorderFactory.createTitledBorder(title));

        return c;
    }

    // ------------------------------------------------------------------ actions (controller)
    private void onAdd() {
        try {
            Sensor s = SensorFactory.fromText(String.valueOf(typeBox.getSelectedItem()),
                    idField.getText(), limitField.getText(), locField.getText());
            station.addSensor(s);
            setStatus("Added " + s);
        } catch (WeatherException ex) {
            error(ex.getMessage());
        }
    }

    private void onUpdateLimit() {
        try {
            Sensor s = station.findSensor(idField.getText().trim())
                    .orElseThrow(() -> new weather.exception.SensorNotFoundException(idField.getText()));
            s.setLimit(Double.parseDouble(limitField.getText().trim()));
            setStatus("Updated " + s);
            refreshNow();
        } catch (NumberFormatException ex) {
            error("Limit is not a number");
        } catch (WeatherException ex) {
            error(ex.getMessage());
        }
    }

    private void onDelete() {
        try {
            station.removeSensor(idField.getText().trim());
            setStatus("Deleted " + idField.getText().trim());
        } catch (WeatherException ex) {
            error(ex.getMessage());
        }
    }

    private void onStartStop() {
        if (simulator.isRunning()) {
            simulator.stop();
            startStop.setText("Start");
            setStatus("Stopped");
        } else {
            simulator.start();
            startStop.setText("Stop");
            setStatus("Running");
        }
    }

    private void onSearch() {
        String id = idField.getText().trim();
        LocalDate date = null;

        if (!dateField.getText().isBlank()) {
            try {
                date = LocalDate.parse(dateField.getText().trim());
            } catch (DateTimeParseException ex) {
                error("Date must look like 2026-10-09 (yyyy-MM-dd)");

                return;
            }
        }

        List<Reading> found = station.search(id, date);
        Double avg = id.isEmpty() ? null : station.average(id);

        String title = "SEARCH  sensor=\"" + (id.isEmpty() ? "*" : id) + "\"  date="
                + (date == null ? "*" : date) + "  ->  " + found.size() + " readings"
                + (avg == null ? "" : String.format("   (average %.2f)", avg));

        show(title, found);
    }

    private void onSort() {
        show("SORTED by value (low to high)", station.sortedByValue());
    }

    private void onSortByTime() {
        show("SORTED by timestamp (oldest first)", station.sortedByTime());
    }

    private void onReport() {
        output.setText(StatisticsService.fullReport(station));
        output.setCaretPosition(0);
        showOutputTab();
    }

    private void onDeleteReadings() {
        int n = station.deleteReadings(idField.getText().trim());
        setStatus("Deleted " + n + " readings of " + idField.getText().trim());
        refreshNow();
    }

    /** Print a titled list of readings, with the timestamp so the user can see the ordering. */
    private void show(String title, List<Reading> readings) {
        StringBuilder sb = new StringBuilder(title).append("\n");

        for (Reading r : readings) {
            sb.append(String.format("%-8s %10.2f %-6s  %s%n",
                    r.sensorId(), r.value(), r.type().unit(), TIME_FORMAT.format(r.time())));
        }

        output.setText(sb.toString());
        output.setCaretPosition(0);
        showOutputTab();
    }

    private void showOutputTab() {
        tabs.setSelectedIndex(1);
    }

    private void onClear() {
        station.clearData();
        alertModel.clear();
        refreshNow();
        setStatus("Data cleared");
    }

    private void onSaveSensors() {
        Path p = choose(true);

        if (p == null) {
            return;
        }

        try {
            PersistenceService.saveSensors(p, station.sensors());
            setStatus("Saved to " + p);
        } catch (StorageException ex) {
            error(ex.getMessage());
        }
    }

    private void onLoadSensors() {
        Path p = choose(false);

        if (p == null) {
            return;
        }

        try {
            station.replaceSensors(PersistenceService.loadSensors(p));
            setStatus("Loaded " + p);
        } catch (StorageException ex) {
            error(ex.getMessage());
        }
    }

    private void onExport() {
        Path p = choose(true);

        if (p == null) {
            return;
        }

        setStatus("Exporting...");
        PersistenceService.exportCsvAsync(p, station.allReadings(), io)
                .whenComplete((n, err) -> SwingUtilities.invokeLater(() -> {   // back to the EDT
                    if (err != null) {
                        error(String.valueOf(err.getCause() != null ? err.getCause().getMessage() : err));
                    } else setStatus("Exported " + n + " readings to " + p);
                }));
    }

    private Path choose(boolean save) {
        JFileChooser fc = new JFileChooser();
        int r = save ? fc.showSaveDialog(this) : fc.showOpenDialog(this);

        if (r != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        File f = fc.getSelectedFile();

        return f.toPath();
    }

    private void refreshChart() {
        SensorType t = (SensorType) chartType.getSelectedItem();
        chart.setData(t, station.hourlyAverage(t));
    }

    private void shutdown() {
        simulator.close();
        io.shutdown();
        dispose();
        System.exit(0);
    }

    // ------------------------------------------------------------------ WeatherListener (called off-EDT)
    @Override
    public void onReading(Reading r) {
        scheduleRefresh();
    }

    @Override
    public void onSensorsChanged() {
        scheduleRefresh();
    }

    @Override
    public void onAlert(Alert a) {
        SwingUtilities.invokeLater(() -> {
            alertModel.add(0, a.toString());

            while (alertModel.size() > WeatherStation.MAX_ALERTS) {
                alertModel.removeElementAt(alertModel.size() - 1);
            }
        });
    }

    /** Coalesce: 100 readings in a burst -> ONE table refresh. */
    private void scheduleRefresh() {
        if (refreshPending.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                refreshPending.set(false);
                refreshNow();
            });
        }
    }

    private void refreshNow() {
        int sel = table.getSelectedRow();
        tableModel.refresh();

        if (sel >= 0 && sel < table.getRowCount()) {
            table.setRowSelectionInterval(sel, sel);
        }

        setStatus(simulator.isRunning() ? "Running - " + station.totalReadings() + " readings stored"
                                        : "Idle - " + station.totalReadings() + " readings stored");
    }

    private void setStatus(String s) {
        status.setText(" " + s);
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
