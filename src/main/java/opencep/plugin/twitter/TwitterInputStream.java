package opencep.plugin.twitter;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import opencep.stream.InputStream;

/** Reads newline-delimited tweet JSON from an explicitly supplied HTTP stream endpoint. */
public final class TwitterInputStream extends InputStream<String> {
  private final java.io.InputStream response;
  private final Thread reader;
  private volatile RuntimeException failure;

  public TwitterInputStream(URI endpoint, TwitterCredentials credentials, Duration timeLimit)
      throws IOException, InterruptedException {
    if (timeLimit != null && timeLimit.isNegative())
      throw new IllegalArgumentException("Negative stream duration");
    var request =
        HttpRequest.newBuilder(endpoint)
            .header("Authorization", "Bearer " + credentials.bearerToken())
            .GET()
            .build();
    var result =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
    if (result.statusCode() != 200) {
      result.body().close();
      throw new IOException("Tweet endpoint returned HTTP " + result.statusCode());
    }
    response = result.body();
    reader =
        new Thread(
            () -> {
              try (var lines =
                  new BufferedReader(new InputStreamReader(response, StandardCharsets.UTF_8))) {
                String line;
                while (!isClosed() && (line = lines.readLine()) != null)
                  if (!line.isBlank()) addItem(line);
              } catch (IOException e) {
                if (!isClosed()) failure = new UncheckedIOException(e);
              } finally {
                close();
              }
            },
            "opencep-tweets");
    reader.setDaemon(true);
    reader.start();
    if (timeLimit != null) {
      if (timeLimit.isNegative()) throw new IllegalArgumentException("Negative stream duration");
      Thread timer =
          new Thread(
              () -> {
                try {
                  Thread.sleep(timeLimit.toMillis());
                  close();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              },
              "opencep-tweet-timeout");
      timer.setDaemon(true);
      timer.start();
    }
  }

  public String getItem() {
    var item = super.getItem();
    if (item == null && failure != null) throw failure;
    return item;
  }

  public void close() {
    super.close();
    try {
      response.close();
    } catch (IOException ignored) {
    }
  }
}
