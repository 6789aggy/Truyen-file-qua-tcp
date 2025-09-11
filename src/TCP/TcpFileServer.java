package TCP;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class TcpFileServer extends JFrame {
    private JTextArea logArea;
    private JButton startButton;
    private ServerSocket serverSocket;
    private int port = 5000;
    private String saveDir = "C:\\Users\\Admin\\FileSave\\"; 

    public TcpFileServer() {
        setTitle("TCP File Server");
        setSize(400, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        startButton = new JButton("Start Server");
        startButton.addActionListener(e -> startServer());
        add(startButton, BorderLayout.SOUTH);
    }

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                log("Server đang chạy trên cổng " + port);

                while (true) {
                    Socket socket = serverSocket.accept();
                    log("Client kết nối: " + socket.getInetAddress());

                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                    String fileName = dis.readUTF();
                    long fileSize = dis.readLong();

                    File outFile = new File(saveDir + fileName);
                    FileOutputStream fos = new FileOutputStream(outFile);

                    byte[] buffer = new byte[4096];
                    int read;
                    long totalRead = 0;

                    while ((read = dis.read(buffer)) > 0) {
                        fos.write(buffer, 0, read);
                        totalRead += read;
                        if (totalRead >= fileSize) break;
                    }

                    fos.close();
                    dis.close();
                    socket.close();

                    log("Đã nhận file: " + outFile.getAbsolutePath());
                }
            } catch (IOException ex) {
                log("Lỗi: " + ex.getMessage());
            }
        }).start();
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TcpFileServer().setVisible(true));
    }
}
