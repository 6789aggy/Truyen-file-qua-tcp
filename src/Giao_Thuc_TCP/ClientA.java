package Giao_Thuc_TCP;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;

public class ClientA extends JFrame {
    private JTextArea logArea;
    private JTextField ipField, portField;
    private JButton connectButton, chooseButton, sendButton;
    private Socket socket;
    private DataOutputStream dos;
    private DataInputStream dis;
    private File selectedFile;

    // Thư mục lưu file cho Client A
    private final File saveDir = new File("ClientA_Files");

    public ClientA() {
        setTitle("TCP File Client A");
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
        connectButton = new JButton("Kết nối Server");
        chooseButton = new JButton("Chọn File");
        sendButton = new JButton("Gửi File");

        connectButton.addActionListener(e -> connectServer());
        chooseButton.addActionListener(e -> chooseFile());
        sendButton.addActionListener(e -> sendFile());

        buttonPanel.add(connectButton);
        buttonPanel.add(chooseButton);
        buttonPanel.add(sendButton);
        add(buttonPanel, BorderLayout.SOUTH);

        if (!saveDir.exists()) saveDir.mkdir();
    }

    private void connectServer() {
        try {
            socket = new Socket(ipField.getText().trim(), Integer.parseInt(portField.getText().trim()));
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());
            log("Kết nối thành công tới server!");

            new Thread(this::listenForFiles).start();
        } catch (IOException e) {
            log("Lỗi kết nối: " + e.getMessage());
        }
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            log("Đã chọn file: " + selectedFile.getName());
        }
    }

    private void sendFile() {
        if (selectedFile == null || !selectedFile.exists()) {
            log("Chưa chọn file!");
            return;
        }
        try (FileInputStream fis = new FileInputStream(selectedFile)) {
            dos.writeUTF(selectedFile.getName());
            dos.writeLong(selectedFile.length());

            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) > 0) {
                dos.write(buffer, 0, read);
            }
            dos.flush();
            log("Đã gửi file: " + selectedFile.getName());
        } catch (IOException e) {
            log("Lỗi gửi file: " + e.getMessage());
        }
    }

    private void listenForFiles() {
        try {
            while (true) {
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();
                File outFile = new File(saveDir, fileName);

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
                log("Đã nhận file: " + fileName + " (" + fileSize + " bytes)");
            }
        } catch (IOException e) {
            log("Ngắt kết nối khỏi server: " + e.getMessage());
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientA().setVisible(true));
    }
}
