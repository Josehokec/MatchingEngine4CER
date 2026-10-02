package cersrt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** Lazy UTF-8 file input, with path and line-number diagnostics. Use try-with-resources. */
public final class EventSource implements Iterable<Event>, AutoCloseable {
  private final Path path;
  private final EventFormats.Domain domain;
  private final BufferedReader reader;
  private boolean iterated;
  private long lineNumber;

  public EventSource(Path path, EventFormats.Domain domain) throws IOException {
    this.path = path;
    this.domain = domain;
    reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
  }

  @Override
  public Iterator<Event> iterator() {
    if (iterated)
      throw new IllegalStateException("EventSource has a single iterator; reopen to replay");
    iterated = true;
    return new Iterator<>() {
      private Event next;
      private boolean done;

      @Override
      public boolean hasNext() {
        while (!done && next == null) {
          try {
            String line = reader.readLine();
            if (line == null) {
              done = true;
              close();
              break;
            }
            lineNumber++;
            if (line.isBlank() || line.stripLeading().startsWith("//")) continue;
            try {
              next = EventFormats.parse(line, domain);
            } catch (IllegalArgumentException exception) {
              throw new IllegalArgumentException(
                  path + ":" + lineNumber + ": " + exception.getMessage(), exception);
            }
          } catch (IOException exception) {
            throw new UncheckedIOException(exception);
          }
        }
        return next != null;
      }

      @Override
      public Event next() {
        if (!hasNext()) throw new NoSuchElementException();
        Event event = next;
        next = null;
        return event;
      }
    };
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }
}
