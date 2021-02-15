package scanner;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import scanner.http.Converter;
import scanner.http.IpV4Address;
import scanner.http.IpV4Range;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
public class CameraScanner {

    private final List<InetSocketAddress> addresses = new ArrayList<>();
    private final Converter converter = new Converter();

    public void prepareSinglePortScanning(String rangeAsString, int port) {
        addresses.clear();
        IpV4Range rangeContainer = new IpV4Range(rangeAsString);
        List<IpV4Address> range = rangeContainer.range();
        for (IpV4Address address : range)
            addresses.add(converter.convert(address, port));
    }

    @SneakyThrows
    public void scanning() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        HashSet<CameraScanExecutor> callables = new HashSet<>();

        for (InetSocketAddress address : addresses)
            callables.add(new CameraScanExecutor(address));

        List<Future<Optional<String>>> futures = executorService.invokeAll(callables);
        for (Future<Optional<String>> future : futures) {
            Optional<String> result = future.get();
            if (result.isPresent()) {
                String value = result.get();
                log.info(value);

                CVEScanner.scanning(value);
            }
        }
    }

}
