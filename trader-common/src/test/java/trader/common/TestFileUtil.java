package trader.common;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import trader.common.util.FileUtil;
import trader.common.util.FileWatchListener;

public class TestFileUtil {

    @Test
    public void testFileChangeListener() throws Exception
    {
        File file = File.createTempFile("test_file_change", "txt");
        file.deleteOnExit();
        FileUtil.save(file, "TEST1");

        final List<File> listenerFiles = new ArrayList<>();

        FileUtil.watchOn(file, new FileWatchListener() {
            public void onFileChanged(File file) {
                listenerFiles.add(file);
                System.out.println("File "+file+" was changed");
            }
        });
        Thread.sleep(2000);
        FileUtil.save(file, "TEST2");
        Thread.sleep(2000);
        assertTrue(listenerFiles.size()==1 && listenerFiles.get(0).equals(file));
    }

}
