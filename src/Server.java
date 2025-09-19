package Giao_Thuc_TCP;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server extends JFrame {
    private JTextArea logArea;
    private DefaultListModel<String> clientListModel;
    private JLabel statusLabel;
    private JButton startButton, stopButton, clearButton;
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private volatile boolean running = false;
    private final Map<String, Socket> clients = new ConcurrentHashMap<>();

    public Server() {
        setTitle("Server");
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Status
        statusLabel = new JLabel("Server đã dừng.");
        statusLabel.setForeground(Color.BLUE);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(statusLabel, BorderLayout.NORTH);

        // Log + Clients
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Event Log"));

        clientListModel = new DefaultListModel<>();
        JList<String> clientList = new JList<>(clientListModel);
        JScrollPane clientScroll = new JScrollPane(clientList);
        clientScroll.setBorder(BorderFactory.createTitledBorder("Client đang kết nối"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, logScroll, clientScroll);
        splitPane.setDividerLocation(450);
        add(splitPane, BorderLayout.CENTER);

        // Buttons
        JPanel bottomPanel = new JPanel();
        startButton = new JButton("Khởi chạy Server");
        stopButton = new JButton("Dừng Server");
        clearButton = new JButton("Clear Log");

        stopButton.setEnabled(false);

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());
        clearButton.addActionListener(e -> logArea.setText(""));

        bottomPanel.add(startButton);
        bottomPanel.add(stopButton);
        bottomPanel.add(clearButton);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(4000);
            pool = Executors.newCachedThreadPool();
            running = true;

            statusLabel.setText("Server đã khởi chạy");
            statusLabel.setForeground(new Color(0, 128, 0));
            log("Server đang chạy, sẵn sàng nhận kết nối!!");

            startButton.setEnabled(false);
            stopButton.setEnabled(true);

            pool.execute(() -> {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                        String clientName = dis.readUTF();

                        clients.put(clientName, clientSocket);
                        SwingUtilities.invokeLater(() -> clientListModel.addElement(clientName + " (Online)"));

                        log(clientName + " đã kết nối!");
                        pool.execute(() -> handleClient(clientName, clientSocket));
                    } catch (IOException e) {
                        if (running) log("Lỗi kết nối!!" + e.getMessage());
                    }
                }
            });

        } catch (IOException e) {
            log("Không thể kết nối đến Server: " + e.getMessage());
        }
    }

    private void stopServer() {
        try {
            running = false;
            if (serverSocket != null) serverSocket.close();
            if (pool != null) pool.shutdownNow();

            for (Socket s : clients.values()) {
                try { s.close(); } catch (IOException ignored) {}
            }
            clients.clear();
            clientListModel.clear();

            statusLabel.setText("Server đã tắt");
            statusLabel.setForeground(Color.BLUE);
            log("Đã dừng Server");

            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        } catch (IOException e) {
            log("Lỗi dừng Server: " + e.getMessage());
        }
    }

    private void handleClient(String clientName, Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            while (running && !socket.isClosed()) {
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();

                File dir = new File("Server_Files");
                if (!dir.exists()) dir.mkdir();
                File outFile = new File(dir, fileName);

                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[4096];
                    long remaining = fileSize;
                    while (remaining > 0) {
                        int read = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (read == -1) break;
                        fos.write(buffer, 0, read);
                        remaining -= read;
                    }
                }

                log("⬇ File received from " + clientName + ": " + fileName +
                    " (" + String.format("%.2f", fileSize / (1024.0 * 1024.0)) + " MB)");

                broadcastFile(clientName, fileName, new File("Server_Files", fileName));
            }
        } catch (IOException e) {
            log(clientName + "đã ngắt kết nối");
            clients.remove(clientName);
            SwingUtilities.invokeLater(() -> clientListModel.removeElement(clientName + " (Online)"));
        }
    }

    private void broadcastFile(String sender, String fileName, File file) {
        for (Map.Entry<String, Socket> entry : clients.entrySet()) {
            String clientName = entry.getKey();
            Socket s = entry.getValue();
            if (clientName.equals(sender)) continue;

            try {
                DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                dos.writeUTF(fileName);
                dos.writeLong(file.length());

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = fis.read(buffer)) > 0) {
                        dos.write(buffer, 0, read);
                    }
                }
                dos.flush();
                log("Gửi file tới " + clientName + ": " + fileName);
            } catch (IOException e) {
                log("Lỗi gửi file đến " + clientName + ": " + e.getMessage());
            }
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Server().setVisible(true));
    }
}
