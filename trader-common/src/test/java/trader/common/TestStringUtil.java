package trader.common;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import trader.common.util.StringUtil;

public class TestStringUtil {

    @Test
    public void testWildcard() {
        assertTrue(StringUtil.wildcardMatches("abc", "a?c"));
        assertTrue(!StringUtil.wildcardMatches("abc", "a?b"));
        assertTrue(StringUtil.wildcardMatches("abc", "a*"));
        assertTrue(StringUtil.wildcardMatches("abc", "a?*"));
    }
}
