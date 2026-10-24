package nexus.io.agent.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import nexus.io.agent.sdk.json.Json;
import nexus.io.agent.sdk.json.JsonException;
import nexus.io.agent.sdk.json.JsonMaps;

public class JsonTest {

  @Test
  public void roundTripsNestedValuesAndEscapes() {
    Map<String, Object> value = JsonMaps.of("text", "line\n\"quoted\" \\ tab\t \u2028 中文", "list",
        Arrays.asList(1L, 2.5, true, null), "nested", JsonMaps.of("empty", JsonMaps.of()));
    String text = Json.stringify(value);
    assertTrue(text, text.contains("\\u2028"));
    assertEquals(value, Json.parse(text));
  }

  @Test
  public void decodesNumbersByRange() {
    List<?> numbers = (List<?>) Json.parse("[1, -2, 3.0, 1e3, 123456789012345678901234567890]");
    assertEquals(Long.valueOf(1), numbers.get(0));
    assertEquals(Long.valueOf(-2), numbers.get(1));
    assertEquals(Double.valueOf(3.0), numbers.get(2));
    assertEquals(Double.valueOf(1000), numbers.get(3));
    assertEquals(new BigInteger("123456789012345678901234567890"), numbers.get(4));
    assertEquals("[3,2.5]", Json.stringify(Arrays.asList(3.0, 2.5)));
  }

  @Test
  public void decodesUnicodeEscapesAndByteOrderMark() {
    assertEquals("é😀", JsonMaps.string(Json.parseObject("\ufeff{\"a\":\"\\u00e9\\ud83d\\ude00\"}"), "a"));
    assertNull(Json.parse("null"));
  }

  @Test
  public void rejectsMalformedInput() {
    for (String bad : new String[] { "{", "{\"a\":}", "[1,]", "\"\\x\"", "tru", "{} extra", "\"a\nb\"" }) {
      try {
        Json.parse(bad);
        fail("Accepted " + bad);
      } catch (JsonException expected) {
        // expected
      }
    }
  }
}
