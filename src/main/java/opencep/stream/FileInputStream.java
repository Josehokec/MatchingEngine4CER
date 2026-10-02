package opencep.stream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Lazy line reader; does not load the input file into memory. */
public final class FileInputStream implements Iterable<String>, AutoCloseable {
  private final BufferedReader reader;
  private boolean claimed;

  public FileInputStream(String path) throws IOException {
    this(Path.of(path));
  }

  public FileInputStream(Path path) throws IOException {
    reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
  }

  public Iterator<String> iterator() {
    if (claimed) throw new IllegalStateException("File stream has already been consumed");
    claimed = true;
    return new Iterator<>() {
      private String next;
      private boolean fetched;

      public boolean hasNext() {
        if (!fetched) {
          try {
            next = reader.readLine();
            if (next == null) close();
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          fetched = true;
        }
        return next != null;
      }

      public String next() {
        if (!hasNext()) throw new NoSuchElementException();
        String s = next;
        fetched = false;
        return s;
      }
    };
  }

  public void close() throws IOException {
    reader.close();
  }
}
