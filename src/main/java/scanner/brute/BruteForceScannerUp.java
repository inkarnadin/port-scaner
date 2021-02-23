package scanner.brute;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class BruteForceScannerUp {

    private final ExecutorService executorService = Executors.newFixedThreadPool(20);

    private final static long EXEC_TIMEOUT = 2500L;
    private final static long TERMINATION_TIMEOUT = 500L;

    @SneakyThrows
    public void brute(String ip, String[] passwords) {
        if (execEmptyBruteTask(ip)) {
            log.info("{} => {}", ip, "auth not required");
            return;
        }

        List<CompletableFuture<AuthStateStore>> futures = Arrays.stream(passwords)
                .map(f -> createBruteTask(ip, f))
                .collect(Collectors.toList());

        List<AuthStateStore> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        List<AuthStateStore> authList = results.stream()
                .filter(AuthStateStore::isAuth)
                .collect(Collectors.toList());
        int size = authList.size();

        if (size > 0) {
            log.info("{} => {}", ip, (size == 1)
                    ? authList.get(0).getCredentials().orElse("auth not required")
                    : "auth not required");

            executorService.awaitTermination(TERMINATION_TIMEOUT, TimeUnit.MILLISECONDS);
        }
    }

    private CompletableFuture<AuthStateStore> createBruteTask(String ip, String password) {
        CompletableFuture<AuthStateStore> future = new CompletableFuture<AuthStateStore>()
                .completeOnTimeout(AuthStateStore.BAD_AUTH, EXEC_TIMEOUT, TimeUnit.MILLISECONDS);
        CompletableFuture.runAsync(() -> new BruteTask(future, ip, password), executorService);
        return future;
    }

    private boolean execEmptyBruteTask(String ip) {
        CompletableFuture<AuthStateStore> future = new CompletableFuture<AuthStateStore>()
                .completeOnTimeout(AuthStateStore.BAD_AUTH, EXEC_TIMEOUT, TimeUnit.MILLISECONDS);

        CompletableFuture.runAsync(() -> new BruteTask(future, ip, null), executorService);

        AuthStateStore result = future.join();
        return (result.getState() == AuthState.AUTH);
    }

}