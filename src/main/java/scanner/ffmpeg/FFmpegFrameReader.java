package scanner.ffmpeg;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Frame reader for saving screenshot (via FFmpeg).
 * Allow ip, credentials and state of stream - or right path, or signal about finished.
 *
 * Try get one frame and save it as image.
 *
 * @author inkarnadin
 */
@RequiredArgsConstructor
public class FFmpegFrameReader implements Supplier<FFmpegState> {

    private final String ip;
    private final String credentials;
    private final FFmpegState state;

    /**
     * Save frame as JPG image.
     *
     * @return true if complete without specified errors, else - false.
     */
    @Override
    @SneakyThrows
    public FFmpegState get() {
        ProcessBuilder builder = createProcess();
        builder.start();

        return FFmpegState.COMPLETE;
    }

    private ProcessBuilder createProcess() {
        String url = (Objects.nonNull(credentials))
                ? String.format("rtsp://%s@%s%s", credentials, ip, state.getPath())
                : String.format("rtsp://%s%s", ip, state.getPath());

        return new ProcessBuilder()
                .redirectErrorStream(true)
                .command(
                        "ffmpeg",
                        "-hide_banner",
                        "-rtsp_transport", "tcp",
                        "-i", url,
                        "-vframes", "1",
                        "-q:v", "2", String.format("result/screen/%s.jpg", ip));
    }

}