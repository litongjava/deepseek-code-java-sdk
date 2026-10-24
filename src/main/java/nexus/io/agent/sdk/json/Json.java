package nexus.io.agent.sdk.json;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON codec for the NDJSON protocol, so the SDK adds no dependency to
 * the host application.
 *
 * <p>Objects decode to {@link LinkedHashMap}, arrays to {@link ArrayList},
 * integral numbers to {@link Long} (or {@link BigInteger} when they overflow),
 * other numbers to {@link Double}.
 */
public final class Json {

  private Json() {
  }

  /** Serializes maps, collections, arrays, strings, numbers, booleans, null and {@link JsonValue}. */
  public static String stringify(Object value) {
    StringBuilder out = new StringBuilder();
    write(out, value);
    return out.toString();
  }

  public static Object parse(String text) {
    Parser parser = new Parser(text);
    parser.skipWhitespace();
    Object value = parser.readValue();
    parser.skipWhitespace();
    if (parser.position != text.length()) {
      throw parser.error("Unexpected trailing characters");
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> parseObject(String text) {
    Object value = parse(text);
    if (!(value instanceof Map)) {
      throw new JsonException("Expected a JSON object");
    }
    return (Map<String, Object>) value;
  }

  private static void write(StringBuilder out, Object value) {
    if (value == null) {
      out.append("null");
    } else if (value instanceof JsonValue) {
      write(out, ((JsonValue) value).toJson());
    } else if (value instanceof CharSequence || value instanceof Character || value instanceof Enum) {
      writeString(out, value.toString());
    } else if (value instanceof Boolean) {
      out.append(value.toString());
    } else if (value instanceof Double || value instanceof Float) {
      double number = ((Number) value).doubleValue();
      if (Double.isNaN(number) || Double.isInfinite(number)) {
        throw new JsonException("JSON cannot represent " + number);
      }
      if (number == Math.rint(number) && Math.abs(number) < 1e15) {
        out.append((long) number);
      } else {
        out.append(number);
      }
    } else if (value instanceof BigDecimal) {
      out.append(((BigDecimal) value).toPlainString());
    } else if (value instanceof Number) {
      out.append(value.toString());
    } else if (value instanceof Map) {
      out.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        if (!first) {
          out.append(',');
        }
        first = false;
        writeString(out, String.valueOf(entry.getKey()));
        out.append(':');
        write(out, entry.getValue());
      }
      out.append('}');
    } else if (value instanceof Collection) {
      out.append('[');
      boolean first = true;
      for (Object item : (Collection<?>) value) {
        if (!first) {
          out.append(',');
        }
        first = false;
        write(out, item);
      }
      out.append(']');
    } else if (value.getClass().isArray()) {
      out.append('[');
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        if (i > 0) {
          out.append(',');
        }
        write(out, Array.get(value, i));
      }
      out.append(']');
    } else {
      throw new JsonException("Cannot serialize " + value.getClass().getName() + "; implement JsonValue");
    }
  }

  private static void writeString(StringBuilder out, String text) {
    out.append('"');
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      switch (ch) {
      case '"':
        out.append("\\\"");
        break;
      case '\\':
        out.append("\\\\");
        break;
      case '\n':
        out.append("\\n");
        break;
      case '\r':
        out.append("\\r");
        break;
      case '\t':
        out.append("\\t");
        break;
      case '\b':
        out.append("\\b");
        break;
      case '\f':
        out.append("\\f");
        break;
      default:
        // U+2028/U+2029 are escaped so a line never splits inside JavaScript readers.
        if (ch < 0x20 || ch == ' ' || ch == ' ') {
          out.append(String.format("\\u%04x", (int) ch));
        } else {
          out.append(ch);
        }
      }
    }
    out.append('"');
  }

  private static final class Parser {
    private final String text;
    private int position;
    private int depth;

    Parser(String text) {
      this.text = text;
    }

    JsonException error(String message) {
      return new JsonException(message + " at offset " + position);
    }

    void skipWhitespace() {
      while (position < text.length()) {
        char ch = text.charAt(position);
        if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || ch == '﻿') {
          position++;
        } else {
          break;
        }
      }
    }

    Object readValue() {
      if (position >= text.length()) {
        throw error("Unexpected end of input");
      }
      char ch = text.charAt(position);
      switch (ch) {
      case '{':
        return readObject();
      case '[':
        return readArray();
      case '"':
        return readString();
      case 't':
        return readLiteral("true", Boolean.TRUE);
      case 'f':
        return readLiteral("false", Boolean.FALSE);
      case 'n':
        return readLiteral("null", null);
      default:
        if (ch == '-' || (ch >= '0' && ch <= '9')) {
          return readNumber();
        }
        throw error("Unexpected character '" + ch + "'");
      }
    }

    private Object readLiteral(String literal, Object value) {
      if (!text.startsWith(literal, position)) {
        throw error("Invalid literal");
      }
      position += literal.length();
      return value;
    }

    private void enter() {
      if (++depth > 512) {
        throw error("JSON nesting is too deep");
      }
    }

    private Map<String, Object> readObject() {
      enter();
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      position++;
      skipWhitespace();
      if (peek() == '}') {
        position++;
        depth--;
        return map;
      }
      while (true) {
        skipWhitespace();
        if (peek() != '"') {
          throw error("Expected object key");
        }
        String key = readString();
        skipWhitespace();
        expect(':');
        skipWhitespace();
        map.put(key, readValue());
        skipWhitespace();
        char next = next();
        if (next == '}') {
          depth--;
          return map;
        }
        if (next != ',') {
          throw error("Expected ',' or '}'");
        }
      }
    }

    private List<Object> readArray() {
      enter();
      List<Object> list = new ArrayList<Object>();
      position++;
      skipWhitespace();
      if (peek() == ']') {
        position++;
        depth--;
        return list;
      }
      while (true) {
        skipWhitespace();
        list.add(readValue());
        skipWhitespace();
        char next = next();
        if (next == ']') {
          depth--;
          return list;
        }
        if (next != ',') {
          throw error("Expected ',' or ']'");
        }
      }
    }

    private String readString() {
      expect('"');
      StringBuilder out = new StringBuilder();
      while (true) {
        char ch = next();
        if (ch == '"') {
          return out.toString();
        }
        if (ch == '\\') {
          char escaped = next();
          switch (escaped) {
          case '"':
          case '\\':
          case '/':
            out.append(escaped);
            break;
          case 'b':
            out.append('\b');
            break;
          case 'f':
            out.append('\f');
            break;
          case 'n':
            out.append('\n');
            break;
          case 'r':
            out.append('\r');
            break;
          case 't':
            out.append('\t');
            break;
          case 'u':
            if (position + 4 > text.length()) {
              throw error("Truncated unicode escape");
            }
            try {
              out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
            } catch (NumberFormatException e) {
              throw error("Invalid unicode escape");
            }
            position += 4;
            break;
          default:
            throw error("Invalid escape '\\" + escaped + "'");
          }
        } else if (ch < 0x20) {
          throw error("Unescaped control character in string");
        } else {
          out.append(ch);
        }
      }
    }

    private Object readNumber() {
      int start = position;
      if (peek() == '-') {
        position++;
      }
      boolean integral = true;
      while (position < text.length()) {
        char ch = text.charAt(position);
        if (ch >= '0' && ch <= '9') {
          position++;
        } else if (ch == '.' || ch == 'e' || ch == 'E' || ch == '+' || ch == '-') {
          integral = false;
          position++;
        } else {
          break;
        }
      }
      String number = text.substring(start, position);
      try {
        if (integral) {
          BigInteger value = new BigInteger(number);
          if (value.bitLength() < 64) {
            return Long.valueOf(value.longValue());
          }
          return value;
        }
        return Double.valueOf(number);
      } catch (NumberFormatException e) {
        throw error("Invalid number '" + number + "'");
      }
    }

    private char peek() {
      if (position >= text.length()) {
        throw error("Unexpected end of input");
      }
      return text.charAt(position);
    }

    private char next() {
      char ch = peek();
      position++;
      return ch;
    }

    private void expect(char expected) {
      if (next() != expected) {
        position--;
        throw error("Expected '" + expected + "'");
      }
    }
  }
}
