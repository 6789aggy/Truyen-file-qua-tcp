package TCP;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;

public class TcpFileServerTLS {
    private final int port;
    private final File receiveDir;
    private final SSLServerSocketFactory ssf;
    private final ExecutorService pool = Executors.newFixedThreadPool(10);

    // In-memory user database: username -> sha256(password)
    // For demo: password stored as SHA-256 hex. In production use bcrypt/argon2.
    private final Map<String, String> users = new ConcurrentHashMap<>();

    public TcpFileServerTLS(int port, String receiveDirPath, String keystorePath, String keystorePassword) throws Exception {
        this.port = port;
        this.receiveDir = new File(receiveDirPath);
        if (!this.receiveDir.exists()) this.receiveDir.mkdirs();
        this.ssf = createSSLServerSocketFactory(keystorePath, keystorePassword);
        // demo users
        users.put("alice", sha256Hex("alicepass"));
        users.put("bob", sha256Hex("bobpass"));
    }

    private SSLServerSocketFactory createSSLServerSocketFactory(String keystorePath, String keystorePassword)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(keystorePath)) {
            ks.load(is, keystorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keystorePassword.toCharArray());

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, new SecureRandom());
        return ctx.getServerSocketFactory();
    }

    public void start() throws IOException {
        try (SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port)) {
            System.out.println("TLS File Server listening on port " + port);
            while (true) {
                SSLSocket client = (SSLSocket) serverSocket.accept();
                pool.submit(() -> handleClient(client));
            }
        } finally {
            pool.shutdown();
        }
    }

    private void handleClient(SSLSocket socket) {
        String clientInfo = socket.getRemoteSocketAddress().toString();
        System.out.println("Connected: " + clientInfo);
        try {
            socket.setSoTimeout(0);
            socket.startHandshake();
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                // AUTH
                int unameLen = dis.readInt();
                byte[] ub = new byte[unameLen];
                dis.readFully(ub);
                String username = new String(ub, StandardCharsets.UTF_8);

                int pwdLen = dis.readInt();
                byte[] pb = new byte[pwdLen];
                dis.readFully(pb);
                String password = new String(pb, StandardCharsets.UTF_8);

                boolean authOk = authenticate(username, password);
                dos.writeBoolean(authOk);
                dos.flush();
                if (!authOk) {
                    System.out.println("Auth failed for " + username + " from " + clientInfo);
                    return;
                }
                System.out.println("Auth OK: " + username + " from " + clientInfo);

                int fileCount = dis.readInt();
                List<Result> results = new ArrayList<>();

                for (int i = 0; i < fileCount; i++) {
                    int nameLen = dis.readInt();
                    byte[] nameBytes = new byte[nameLen];
                    dis.readFully(nameBytes);
                    String filename = new String(nameBytes, StandardCharsets.UTF_8);

                    long fileSize = dis.readLong();
                    System.out.printf("Receiving '%s' (%d bytes) from %s%n", filename, fileSize, clientInfo);

                    File outFile = new File(receiveDir, filename);
                    if (!outFile.getCanonicalPath().startsWith(receiveDir.getCanonicalPath())) {
                        results.add(new Result(filename, "ERROR", "Invalid path"));
                        // skip bytes of fileSize
                        skipFully(dis, fileSize);
                        continue;
                    }

                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        long remaining = fileSize;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(buffer.length, remaining);
                            int read = dis.read(buffer, 0, toRead);
                            if (read == -1) throw new EOFException("Unexpected EOF");
                            fos.write(buffer, 0, read);
                            remaining -= read;
                        }
                    } catch (Exception ex) {
                        results.add(new Result(filename, "ERROR", ex.getMessage()));
                        continue;
                    }

                    // compute md5
                    String md5 = computeMD5(outFile);
                    results.add(new Result(filename, "OK", md5));
                    System.out.println("Saved " + filename + " md5=" + md5);
                }

                // send results back
                dos.writeInt(results.size());
                for (Result r : results) {
                    dos.writeUTF(r.filename);
                    dos.writeUTF(r.status);
                    dos.writeUTF(r.message);
                }
                dos.flush();
            }
        } catch (Exception e) {
            System.err.println("Client error " + clientInfo + ": " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("Connection closed: " + clientInfo);
        }
    }

    private boolean authenticate(String username, String password) {
        String stored = users.get(username);
        if (stored == null) return false;
        try {
            return stored.equalsIgnoreCase(sha256Hex(password));
        } catch (Exception e) {
            return false;
        }
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        long toSkip = n;
        while (toSkip > 0) {
            long skipped = in.skip(toSkip);
            if (skipped <= 0) {
                if (in.read() == -1) throw new EOFException("Unexpected EOF while skipping");
                skipped = 1;
            }
            toSkip -= skipped;
        }
    }

    private static String computeMD5(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) md.update(buf, 0, n);
        }
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static class Result {
        String filename, status, message;
        Result(String f, String s, String m) { filename = f; status = s; message = m; }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java TcpFileServerTLS <port> <receiveDir> <keystore.jks> [keystorePassword]");
            return;
        }
        int port = Integer.parseInt(args[0]);
        String dir = args[1];
        String keystore = args[2];
        String storepass = args.length >= 4 ? args[3] : "changeit";
        TcpFileServerTLS server = new TcpFileServerTLS(port, dir, keystore, storepass);
        server.start();
    }
}
