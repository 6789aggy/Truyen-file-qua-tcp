package Giao_Thuc_TCP;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server extends JFrame {
    private JTextArea logArea;
    private JButton startButton, stopButton;
    private JTextField ipField, portField;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;

    // Map socket -> tên client
    private final ConcurrentHashMap<Socket, String> clientNames = new ConcurrentHashMap<>();

    public Server() {
        setTitle("TCP Server");
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new GridLayout(2, 2));
        topPanel.add(new JLabel("Server IP:"));
        ipField = new JTextField("127.0.0.1");
        topPanel.add(ipField);
        topPanel.add(new JLabel("Port:"));
        portField = new JTextField("4000");
        topPanel.add(portField);
        add(topPanel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
        stopButton.setEnabled(false);

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());

        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void startServer() {
        String ip = ipField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());

        serverThread = new Thread(() -> {
            try {
                InetAddress bindAddr = InetAddress.getByName(ip);
                serverSocket = new ServerSocket(port, 50, bindAddr);
                running = true;

                log("Server đang chạy trên địa chỉ: " + ip + "; cổng: " + port);

                while (running) {
                    Socket socket = serverSocket.accept();
                    new Thread(() -> initClient(socket)).start();
                }
            } catch (IOException ex) {
                log("Lỗi: " + ex.getMessage());
            } finally {
                stopServer();
            }
        });

        serverThread.start();
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        ipField.setEnabled(false);
        portField.setEnabled(false);
    }

    private void stopServer() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (Socket s : clientNames.keySet()) {
                s.close();
            }
            clientNames.clear();
        } catch (IOException ignored) {}

        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        ipField.setEnabled(true);
        portField.setEnabled(true);
        log("Server đã dừng");
    }

    private void initClient(Socket socket) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            String clientName = dis.readUTF();
            clientNames.put(socket, clientName);

            log(clientName + " đã kết nối");

            handleClient(socket, dis);
        } catch (IOException e) {
            log("Không thể khởi tạo client: " + e.getMessage());
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleClient(Socket socket, DataInputStream dis) {
        try {
            while (true) {
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();

                String senderName = clientNames.get(socket);
                log(senderName + " gửi file: " + fileName + " (" + fileSize + " bytes)");

                byte[] fileData = new byte[(int) fileSize];
                dis.readFully(fileData);

                for (Socket s : clientNames.keySet()) {
                    if (s != socket && !s.isClosed()) {
                        try {
                            DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                            dos.writeUTF(fileName);
                            dos.writeLong(fileSize);
                            dos.write(fileData);
                            dos.flush();

                            String receiverName = clientNames.get(s);
                            log("✔ " + senderName + " đã gửi file cho " + receiverName + " thành công");
                        } catch (IOException e) {
                            log("Lỗi gửi file tới client: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            String clientName = clientNames.get(socket);
            log(clientName + " ngắt kết nối");
            clientNames.remove(socket);
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Server().setVisible(true));
    }
}
