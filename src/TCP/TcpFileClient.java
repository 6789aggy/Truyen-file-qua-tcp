package TCP;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class TcpFileClient extends JFrame {
    private JTextField ipField, portField;
    private JTextArea logArea;
    private JButton chooseButton, sendButton;
    private File selectedFile;

    public TcpFileClient() {
        setTitle("TCP File Client");
        setSize(400, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new GridLayout(2, 2));
        topPanel.add(new JLabel("Server IP:"));
        ipField = new JTextField("127.0.0.1");
        topPanel.add(ipField);
        topPanel.add(new JLabel("Port:"));
        portField = new JTextField("5000");
        topPanel.add(portField);
        add(topPanel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        chooseButton = new JButton("Chọn File");
        chooseButton.addActionListener(e -> chooseFile());
        bottomPanel.add(chooseButton);

        sendButton = new JButton("Gửi File");
        sendButton.addActionListener(e -> sendFile());
        bottomPanel.add(sendButton);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void chooseFile() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = fc.getSelectedFile();
            log("Đã chọn file: " + selectedFile.getAbsolutePath());
        }
    }

    private void sendFile() {
        if (selectedFile == null) {
            log("Chưa chọn file!");
            return;
        }

        String serverIP = ipField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());

        new Thread(() -> {
            try (Socket socket = new Socket(serverIP, port)) {
                log("Đã kết nối tới server...");

                FileInputStream fis = new FileInputStream(selectedFile);
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

                dos.writeUTF(selectedFile.getName());
                dos.writeLong(selectedFile.length());

                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) > 0) {
                    dos.write(buffer, 0, read);
                }

                fis.close();
                dos.close();
                dos.flush();
                socket.close();

                log("File đã gửi thành công!");
            } catch (IOException ex) {
                log("Lỗi: " + ex.getMessage());
            }
        }).start();
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TcpFileClient().setVisible(true));
    }
}

