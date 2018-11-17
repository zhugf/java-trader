package trader.service;

import java.io.File;

import trader.common.util.TraderHomeUtil;
import trader.service.ta.TimeSeriesLoaderTest;

public class TraderHomeTestUtil {

    public static void initRepoistoryDir() {
        File file = new File( TimeSeriesLoaderTest.class.getClassLoader().getResource("data/shfe/ru1901/2018.tick-ctp.zip").getFile())
        .getParentFile().getParentFile().getParentFile();
        System.setProperty(TraderHomeUtil.PROP_REPOSITORY_DIR, file.getAbsolutePath());
    }
}
