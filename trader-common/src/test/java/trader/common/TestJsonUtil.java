package trader.common;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.google.gson.JsonElement;

import trader.common.util.JsonUtil;

public class TestJsonUtil {

    @Test
    public void test() {
        String[] list = {"a", "b", "c"};

        JsonElement json1 = JsonUtil.object2json(list);
        JsonElement json2 = JsonUtil.object2json(list);

        assertTrue(json1!=json2);
        assertTrue(json1.equals(json2));
    }
}
