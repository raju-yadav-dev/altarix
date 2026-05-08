package com.example.altarix.update;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Downloads files with simple progress tracking.
 */
public final class DownloadManager {
    private final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public CompletableFuture<Path> downloadAsync(URI uri, Path target, BiConsumer<Long, Long> progress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return download(uri, target, progress);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    public Path download(URI uri, Path target, BiConsumer<Long, Long> progress) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Download failed with HTTP " + response.statusCode());
        }

        OptionalLong lengthHeader = response.headers().firstValueAsLong("Content-Length");
        long totalBytes = lengthHeader.orElse(-1);
        long downloaded = 0;

        try (InputStream input = response.body();
             OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                downloaded += read;
                if (progress != null) {
                    progress.accept(downloaded, totalBytes);
                }
            }
        }

        return target;
    }
}
