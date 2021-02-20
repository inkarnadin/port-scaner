package scanner.brute;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.CancellationException;

@Slf4j
public class RTSPConnector {

    private final static String CRCL = "\r\n";
    private final static String SPACE = " ";

    private final static int PORT = 554;
    private final static int TIMEOUT = 2000;

    private final static String success = "RTSP/1.0 200 OK";
    private final static String failure = "RTSP/1.0 401"; // Unauthorized, Authorization Required
    private final static String not_found = "RTSP/1.0 404"; // Error, Not Found
    private final static String session_not_found = "RTSP/1.0 454"; // Session Not Found
    private final static String bad_request = "RTSP/1.0 400"; // Bad request
    private final static String invalid_param = "RTSP/1.0 451"; // Parameter Not Understood, Invalid Parameter
    private final static String unknown = "RTSP/1.0 418"; // null

    @SneakyThrows
    public static AuthState describe(String ip, String credentials) {
        String statusLine = "";
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, PORT), TIMEOUT);

            Sender sender = new Sender(socket, credentials);
            RTSPBuilder builder = new RTSPBuilder(ip, credentials);

            statusLine = sender.send(builder.baseRequest);
            if (statusLine.contains(not_found) || statusLine.contains(unknown))
                statusLine = sender.send(builder.baseRequestNotFound);

            return statusLine.equals(success)
                    ? AuthState.AUTH
                    : statusLine.contains(session_not_found) || statusLine.contains(bad_request) || statusLine.contains(invalid_param) || statusLine.equals(unknown)
                        ? AuthState.UNKNOWN_STATE
                        : AuthState.NOT_AUTH;
        } catch (SocketTimeoutException ste) {
            throw new CancellationException();
        } catch (IOException xep) {
            log.warn("{}: {}/{}", ip, xep.getMessage(), statusLine);
            return AuthState.NOT_AUTH;
        }
    }

    @RequiredArgsConstructor
    private static class RTSPBuilder {

        @Getter
        private String baseRequest;
        @Getter
        private String baseRequestNotFound;

        RTSPBuilder(String ip, String credentials) {
            credentials = Objects.nonNull(credentials) ? credentials + "@" : "";
            baseRequest = new StringBuilder()
                    .append("DESCRIBE").append(SPACE).append("rtsp://").append(credentials).append(ip).append(":").append(PORT)
                    .append("/").append(SPACE).append("RTSP/1.0").append(CRCL)
                    .append("CSeq:").append(SPACE).append("1").append(CRCL)
                    .append("Content-Type:").append(SPACE).append("application/sdp").append(CRCL)
                    .append(CRCL)
                    .toString();
            baseRequestNotFound = new StringBuilder()
                    .append("DESCRIBE").append(SPACE).append("rtsp://").append(credentials).append(ip).append(":").append(PORT)
                    .append("/Streaming/Channels/101").append(SPACE).append("RTSP/1.0").append(CRCL)
                    .append("CSeq:").append(SPACE).append("1").append(CRCL)
                    .append("Content-Type:").append(SPACE).append("application/sdp").append(CRCL)
                    .append(CRCL)
                    .toString();
        }
    }

    private static class Sender {

        private final String ip;
        private final String credentials;

        private final Socket socket;

        @SneakyThrows
        public Sender(Socket socket, String credentials) {
            this.ip = socket.getInetAddress().getHostAddress();
            this.credentials = credentials;

            this.socket = socket;
        }

        @SneakyThrows
        private String send(String request) {
            // FIXME its bad code
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            bufferedWriter.write(request);
            bufferedWriter.flush();

            String statusLine = bufferedReader.readLine();
            log.debug("{} ({}) => {}", ip, credentials.length() != 0 ? credentials : "empty", statusLine);

            return Objects.nonNull(statusLine) ? statusLine : "RTSP/1.0 418 Null";
        }

    }

}