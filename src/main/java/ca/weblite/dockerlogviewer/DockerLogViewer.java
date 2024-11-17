package ca.weblite.dockerlogviewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedList;

public class DockerLogViewer {

    private static final int MAX_LOG_ENTRIES = 1000; // maximum entries in the LinkedList
    private static final LinkedList<String> logList = new LinkedList<>();
    private static final DefaultTableModel tableModel = new DefaultTableModel(new String[]{"Log Entry"}, 0);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();

    private static final Object lock = new Object();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(DockerLogViewer::createAndShowGUI);

        // Thread to read logs from stdin
        new Thread(DockerLogViewer::readLogsFromStdin).start();
    }

    private static void readLogsFromStdin() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                //synchronized (lock) {
                    if (logList.size() >= MAX_LOG_ENTRIES) {
                        logList.removeFirst();
                        EventQueue.invokeLater(() -> {
                            tableModel.removeRow(0); // keep JTable in sync with LinkedList
                        });
                    }
                    logList.add(line);
                    final String fline = line;
                    EventQueue.invokeLater(()-> {
                        tableModel.addRow(new Object[]{fline});
                            }

                    );
                //}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Docker Log Viewer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        // Top pane - JTable for logs
        JTable logTable = new JTable(tableModel);
        JScrollPane tableScrollPane = new JScrollPane(logTable);

        // Bottom pane - JTextArea for JSON pretty printing
        JTextArea jsonTextArea = new JTextArea();
        jsonTextArea.setEditable(false);
        jsonTextArea.setLineWrap(true);
        jsonTextArea.setWrapStyleWord(true);
        JScrollPane textScrollPane = new JScrollPane(jsonTextArea);

        // Split pane to hold the table and text area
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScrollPane, textScrollPane);
        splitPane.setResizeWeight(0.7);

        // Add listener for row selection to show prettified JSON
        logTable.getSelectionModel().addListSelectionListener(event -> {
            int selectedRow = logTable.getSelectedRow();
            if (selectedRow >= 0) {
                String logEntry = (String) tableModel.getValueAt(selectedRow, 0);
                try {
                    JsonNode jsonNode = mapper.readTree(logEntry);
                    String prettyJson = writer.writeValueAsString(jsonNode);
                    jsonTextArea.setText(prettyJson);
                } catch (Exception e) {
                    try {
                        JsonNode jsonNode = mapper.readTree(logEntry.substring(logEntry.indexOf("{")));
                        String prettyJson = writer.writeValueAsString(jsonNode);
                        jsonTextArea.setText(prettyJson);
                    } catch (Exception ex) {
                        jsonTextArea.setText("Invalid JSON format:\n" + logEntry);
                    }

                }
            }
        });

        frame.add(splitPane);
        frame.setVisible(true);
    }
}
