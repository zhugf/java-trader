package trader.common;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

import trader.common.util.ResourceUtil;

public class TestResourceUtil {

    @Test
    public void test() throws Exception
    {
        File testDir = ResourceUtil.detectJarFile(TestResourceUtil.class);
        assertTrue(testDir.isDirectory());
        File jarFile = ResourceUtil.detectJarFile(Test.class);
        assertTrue(jarFile.isFile() && jarFile.exists() && jarFile.length()>0 && jarFile.getName().indexOf("junit")>=0);
    }

}
