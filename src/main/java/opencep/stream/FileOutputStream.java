package opencep.stream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class FileOutputStream<T> extends OutputStream<T> {
  private final BufferedWriter writer;
  private boolean closed;

  public FileOutputStream(String basePath, String fileName) throws IOException {
    this(Path.of(basePath, fileName));
  }

  public FileOutputStream(Path path) throws IOException {
    if (path.toAbsolutePath().getParent() != null)
      Files.createDirectories(path.toAbsolutePath().getParent());
    writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
  }

  public synchronized void addItem(T item) {
    if (closed) throw new IllegalStateException("Stream is closed");
    try {
      writer.write(item.toString());
      writer.flush();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    // File output intentionally keeps no match history; large runs remain streaming.
  }

  public synchronized void close() {
    if (closed) return;
    closed = true;
    try {
      writer.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      super.close();
    }
  }
}
