package com.edo.backend.stt;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FFmpegTranscoder implements Closeable {
    private final Process process;
    private final OutputStream stdin;
    private final InputStream stdout;
    private final InputStream stderr;

    public FFmpegTranscoder() {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpegCmd());
            // stdin(임의 컨테이너/코덱) → s16le mono 16kHz → stdout
            // -re는 제거(실시간 지연 줄이기), 필요하면 환경에 맞춰 붙여도 됨
            cmd.add("-hide_banner");
            cmd.add("-loglevel"); cmd.add("error");
            cmd.add("-nostats");
            cmd.add("-fflags"); cmd.add("+genpts");
            cmd.add("-i"); cmd.add("pipe:0");
            cmd.add("-ac"); cmd.add("1");
            cmd.add("-ar"); cmd.add("16000");
            cmd.add("-f"); cmd.add("s16le");
            cmd.add("pipe:1");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            process = pb.start();
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // 오류 로그 파이프(선택)
            new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(stderr))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.err.println("[ffmpeg] " + line);
                    }
                } catch (IOException ignored) {}
            }, "ffmpeg-stderr").start();

        } catch (IOException e) {
            throw new RuntimeException("Failed to start ffmpeg process", e);
        }
    }

    public OutputStream stdin()  { return stdin;  }
    public InputStream  stdout() { return stdout; }

    @Override public void close() {
        try { stdin.close(); } catch (Exception ignored) {}
        try { stdout.close(); } catch (Exception ignored) {}
        try { process.destroy(); } catch (Exception ignored) {}
    }

    private static String ffmpegCmd() {
        // 시스템에 깔린 ffmpeg 사용(컨테이너/Render면 /usr/bin/ffmpeg일 가능성 높음)
        // 필요 시 경로를 절대경로로 교체
        return "ffmpeg";
    }
}
