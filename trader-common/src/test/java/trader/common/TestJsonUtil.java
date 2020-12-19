package trader.common;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

    @Test
    public void testMerge() throws Exception
    {
        JsonObject jsonRoot = JsonParser.parseString("{\n"
                + "    \"a\":[\n"
                + "        {\n"
                + "            \"c\":\"d\"\n"
                + "        },{\n"
                + "            \"c\":\"e\"\n"
                + "        }\n"
                + "    ]\n"
                + "}").getAsJsonObject();
        JsonElement val = JsonParser.parseString("[0,1]");
        JsonUtil.merge(jsonRoot, "/a/#0/c", val);

        assertTrue(jsonRoot.toString().indexOf("1")>0);
    }
}
