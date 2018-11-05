package trader.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class FileLocker implements AutoCloseable {
    private FileLock lock = null;
    private RandomAccessFile raf=null;

    public FileLocker(File file) throws IOException
    {
    	if ( file!=null ){
	    	raf = new RandomAccessFile(file,"rw");
	    	lock = raf.getChannel().lock();
    	}
    }


    public FileLocker(FileInputStream is) throws IOException
    {
    	if ( is!=null )
    		lock = is.getChannel().lock();
    }

    public FileLocker(FileOutputStream is) throws IOException
    {
    	if ( is!=null )
    		lock = is.getChannel().lock();
    }

    public FileLocker(RandomAccessFile raf) throws IOException
    {
    	if ( raf!=null )
    		lock = raf.getChannel().lock();
    }

    public FileLocker(FileChannel channel) throws IOException
    {
    	if ( channel!=null )
    		lock = channel.lock();
    }

    @Override
    public void close() throws IOException {
        if ( lock!=null ){
        	lock.close();
        	lock = null;
        }
        if ( raf!=null ){
        	raf.close();
        	raf = null;
        }
    }

}
