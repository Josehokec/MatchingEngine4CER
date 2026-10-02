package opencep.misc;

import java.util.*;

/** Small strict JSON codec for tweet records and portable differential fixtures. */
public final class Json {
  private final String text;
  private int index;

  private Json(String text) {
    this.text = text;
  }

  public static Object parse(String text) {
    var p = new Json(text);
    Object value = p.value();
    p.space();
    if (p.index != text.length()) throw p.error();
    return value;
  }

  private IllegalArgumentException error() {
    return new IllegalArgumentException("Invalid JSON at offset " + index);
  }

  private void space() {
    while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
  }

  private boolean take(char c) {
    space();
    if (index < text.length() && text.charAt(index) == c) {
      index++;
      return true;
    }
    return false;
  }

  private Object value() {
    space();
    if (index >= text.length()) throw error();
    char c = text.charAt(index);
    if (c == '"') return string();
    if (c == '{') {
      index++;
      var out = new LinkedHashMap<String, Object>();
      if (take('}')) return out;
      do {
        space();
        if (index >= text.length() || text.charAt(index) != '"') throw error();
        var key = string();
        if (!take(':')) throw error();
        out.put(key, value());
      } while (take(','));
      if (!take('}')) throw error();
      return out;
    }
    if (c == '[') {
      index++;
      var out = new ArrayList<Object>();
      if (take(']')) return out;
      do {
        out.add(value());
      } while (take(','));
      if (!take(']')) throw error();
      return out;
    }
    for (var literal : List.of("true", "false", "null"))
      if (text.startsWith(literal, index)) {
        index += literal.length();
        return literal.equals("null") ? null : literal.equals("true");
      }
    int start = index;
    if (c == '-') index++;
    if (index >= text.length()) throw error();
    if (text.charAt(index) == '0') index++;
    else {
      if (text.charAt(index) < '1' || text.charAt(index) > '9') throw error();
      while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
    }
    boolean floating = false;
    if (index < text.length() && text.charAt(index) == '.') {
      floating = true;
      index++;
      int digits = index;
      while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
      if (digits == index) throw error();
    }
    if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
      floating = true;
      index++;
      if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-'))
        index++;
      int digits = index;
      while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
      if (digits == index) throw error();
    }
    try {
      String n = text.substring(start, index);
      if (floating) return Double.valueOf(n);
      return Long.valueOf(n);
    } catch (NumberFormatException e) {
      throw error();
    }
  }

  private String string() {
    if (text.charAt(index++) != '"') throw error();
    var out = new StringBuilder();
    while (index < text.length()) {
      char c = text.charAt(index++);
      if (c == '"') return out.toString();
      if (c < 32) throw error();
      if (c != '\\') {
        out.append(c);
        continue;
      }
      if (index >= text.length()) throw error();
      char e = text.charAt(index++);
      switch (e) {
        case '"', '\\', '/' -> out.append(e);
        case 'b' -> out.append('\b');
        case 'f' -> out.append('\f');
        case 'n' -> out.append('\n');
        case 'r' -> out.append('\r');
        case 't' -> out.append('\t');
        case 'u' -> {
          if (index + 4 > text.length()) throw error();
          try {
            out.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
          } catch (NumberFormatException ex) {
            throw error();
          }
          index += 4;
        }
        default -> throw error();
      }
    }
    throw error();
  }
}
