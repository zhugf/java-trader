package trader.common;

import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Test;

import com.google.gson.JsonObject;

import trader.common.util.JsonUtil;
import trader.common.util.StringUtil;

public class TestStringUtil {

    @Test
    public void testWildcard() {
        assertTrue(StringUtil.wildcardMatches("abc", "a?c"));
        assertTrue(!StringUtil.wildcardMatches("abc", "a?b"));
        assertTrue(StringUtil.wildcardMatches("abc", "a*"));
        assertTrue(StringUtil.wildcardMatches("abc", "a?*"));
    }

    @Test
    public void testProperties() {
        String text="a=b\nc=d";
        Properties props = StringUtil.text2properties(text);
        assertTrue(props.containsKey("a"));
        assertTrue(props.containsKey("c"));
        JsonObject json = (JsonObject)JsonUtil.object2json(props);
        assertTrue(json.has("a"));
        assertTrue(json.has("c"));
    }
}
