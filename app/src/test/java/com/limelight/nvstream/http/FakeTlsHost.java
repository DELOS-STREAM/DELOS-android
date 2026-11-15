package com.limelight.nvstream.http;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Test helper: a minimal HTTPS host on the loopback interface that presents one self-signed
 * certificate, asks for (but does not require) a client certificate like a GameStream host, and
 * answers every request with the same body. Records each request that got through the handshake.
 */
final class FakeTlsHost implements Closeable {
    static final class Request {
        /** For example {@code /pair?updateState=1&...}. */
        final String target;
        /** The certificate chain the client presented, or null. */
        final Certificate[] clientCertificates;

        Request(String target, Certificate[] clientCertificates) {
            this.target = target;
            this.clientCertificates = clientCertificates;
        }
    }

    final List<Request> requests = new CopyOnWriteArrayList<>();
    private final SSLServerSocket socket;
    private final Thread thread;
    private final byte[] body;

    FakeTlsHost(TestIdentity identity, String body) throws Exception {
        this.body = body.getBytes(StandardCharsets.UTF_8);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("host", identity.key, new char[0], new Certificate[]{identity.cert});
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(keyStore, new char[0]);
        // Any client certificate completes the handshake; a real host decides afterwards
        // whether that certificate belongs to a paired client.
        TrustManager acceptAnyClient = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), new TrustManager[]{acceptAnyClient}, null);

        socket = (SSLServerSocket) context.getServerSocketFactory()
                .createServerSocket(0, 16, InetAddress.getLoopbackAddress());
        socket.setWantClientAuth(true);
        thread = new Thread(this::serve, "FakeTlsHost");
        thread.setDaemon(true);
        thread.start();
    }

    int port() {
        return socket.getLocalPort();
    }

    private void serve() {
        while (!socket.isClosed()) {
            try (SSLSocket connection = (SSLSocket) socket.accept()) {
                connection.setSoTimeout(10_000);
                connection.startHandshake();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.ISO_8859_1));
                String requestLine = reader.readLine();
                if (requestLine == null) {
                    continue;
                }
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    // Headers are not needed.
                }
                Certificate[] clientCertificates;
                try {
                    clientCertificates = connection.getSession().getPeerCertificates();
                } catch (SSLPeerUnverifiedException none) {
                    clientCertificates = null;
                }
                requests.add(new Request(requestLine.split(" ")[1], clientCertificates));
                OutputStream out = connection.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/xml\r\nContent-Length: "
                        + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                out.write(body);
                out.flush();
            } catch (IOException e) {
                // A client that refused the certificate, or the socket was closed: next connection.
            }
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
        try {
            thread.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
