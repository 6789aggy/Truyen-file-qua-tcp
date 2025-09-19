package Giao_Thuc_TCP;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;

public class Client extends JFrame {
    private JTextArea logArea;
    private JTextField nameField;
    private JButton connectButton, chooseButton, sendButton, openFolderButton;
    private Socket socket;
    private DataOutputStream dos;
    private DataInputStream dis;
    private File selectedFile;
    private File saveDir;

    public Client() {
        setTitle("Client");
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new GridLayout(1, 2));
        topPanel.add(new JLabel("Tên Client:"));
        nameField = new JTextField("Client");
        topPanel.add(nameField);
        add(topPanel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        connectButton = new JButton("Kết nối Server");
        chooseButton = new JButton("Chọn File");
        sendButton = new JButton("Gửi File");
        openFolderButton = new JButton("Mở thư mục chứa file");
        openFolderButton.setEnabled(false);

        connectButton.addActionListener(e -> connectServer());
        chooseButton.addActionListener(e -> chooseFile());
        sendButton.addActionListener(e -> sendFile());
        openFolderButton.addActionListener(e -> openSaveFolder());

        buttonPanel.add(connectButton);
        buttonPanel.add(chooseButton);
        buttonPanel.add(sendButton);
        buttonPanel.add(openFolderButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void connectServer() {
        try {
            socket = new Socket("127.0.0.1", 4000);
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());

            String clientName = nameField.getText().trim();
            if (clientName.isEmpty()) clientName = "Client";

            dos.writeUTF(clientName);
            dos.flush();

            saveDir = new File("Files_" + clientName);
            if (!saveDir.exists()) saveDir.mkdir();

            openFolderButton.setEnabled(true);

            log("[" + clientName + "] Kết nối thành công tới server!");

            new Thread(this::listenForFiles).start();
        } catch (IOException e) {
            log("Lỗi kết nối: " + e.getMessage());
        }
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            log("Đã chọn file: " + selectedFile.getName() +
                " (" + selectedFile.length() + " bytes ~ " +
                String.format("%.2f", selectedFile.length() / (1024.0 * 1024.0)) + " MB)");
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

                log("Đã nhận file: " + fileName +
                    " (" + fileSize + " bytes ~ " +
                    String.format("%.2f", fileSize / (1024.0 * 1024.0)) + " MB)");
            }
        } catch (IOException e) {
            log("Ngắt kết nối khỏi server: " + e.getMessage());
        }
    }

    private void openSaveFolder() {
        if (saveDir == null || !saveDir.exists()) {
            log("Chưa có thư mục lưu file!");
            return;
        }
        try {
            Desktop.getDesktop().open(saveDir);
            log("Đã mở thư mục: " + saveDir.getAbsolutePath());
        } catch (IOException e) {
            log("Không thể mở thư mục: " + e.getMessage());
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Client().setVisible(true));
    }
}
