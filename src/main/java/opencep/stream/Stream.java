package opencep.stream;

import java.util.*;
import java.util.concurrent.*;

/** Single-consumer blocking stream. close() is idempotent and wakes the reader. */
public class Stream<T> implements Iterable<T>, AutoCloseable {
  private static final Object END = new Object();
  private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();
  private final List<T> history = new ArrayList<>();
  private volatile boolean closed;
  private boolean exhausted;

  public synchronized void addItem(T item) {
    if (closed) throw new IllegalStateException("Stream is closed");
    Objects.requireNonNull(item);
    history.add(item);
    queue.add(item);
  }

  public synchronized void close() {
    if (!closed) {
      closed = true;
      queue.add(END);
    }
  }

  @SuppressWarnings("unchecked")
  public T getItem() {
    if (exhausted) return null;
    try {
      Object item = queue.take();
      if (item == END) {
        exhausted = true;
        return null;
      }
      return (T) item;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Stream interrupted", e);
    }
  }

  public boolean isClosed() {
    return closed;
  }

  public synchronized int count() {
    return history.size();
  }

  public synchronized T first() {
    return history.isEmpty() ? null : history.get(0);
  }

  public synchronized T last() {
    return history.isEmpty() ? null : history.get(history.size() - 1);
  }

  public synchronized List<T> snapshot() {
    return List.copyOf(history);
  }

  public synchronized Stream<T> duplicate() {
    var out = new Stream<T>();
    history.forEach(out::addItem);
    if (closed) out.close();
    return out;
  }

  public Iterator<T> iterator() {
    return new Iterator<>() {
      private T next;
      private boolean fetched;

      public boolean hasNext() {
        if (!fetched) {
          next = getItem();
          fetched = true;
        }
        return next != null;
      }

      public T next() {
        if (!hasNext()) throw new NoSuchElementException();
        T value = next;
        fetched = false;
        return value;
      }
    };
  }
}
