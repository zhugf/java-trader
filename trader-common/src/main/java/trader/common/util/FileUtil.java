package trader.common.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class FileUtil {

    public static String createTempDirectory(File parentDir, String prefix) throws IOException
    {
        return Files.createTempDirectory(parentDir.toPath(), prefix).toString();
    }

    /**
     * 删除一个目录和所有的子目录，文件
     */
    public static void deleteDirectory(File dirToDelete){
        if ( !dirToDelete.exists() )
            return;
        LinkedList<File> files = new LinkedList<>();
        searchFiles(files, dirToDelete);
        for(File file:files){
            file.delete();
        }
    }

    private static void searchFiles(List<File> files, File dir){
        File subs[] = dir.listFiles();
        if ( subs!=null&&subs.length>0){
            for(int i=0;i<subs.length;i++){
                if ( subs[i].isFile() )
                    files.add(subs[i]);
                else
                    searchFiles(files,subs[i]);
            }
        }
        files.add(dir);
    }

    public static byte[] loadAsBytes(File file) throws IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream((int)file.length());
        try(InputStream is = new FileInputStream(file)){
            byte buf[] = new byte[4096];
            int len;
            while ( (len=is.read(buf))>0 ){
                os.write(buf,0,len);
            }
        }
        return os.toByteArray();
    }

    public static String load(File file) throws IOException{
        StringWriter writer = new StringWriter();
        try(BufferedReader reader = IOUtil.createBufferedReader(file, StringUtil.UTF8)){
            char cbuf[] = new char[4096];
            int clen;
            while ( (clen=reader.read(cbuf))>0 ){
                writer.write(cbuf,0,clen);
            }
        }
        return writer.toString();
    }

    public static List<String> loadLines(File file) throws IOException{
        LinkedList<String> lines = new LinkedList<>();
        try(BufferedReader reader = IOUtil.createBufferedReader(file, StringUtil.UTF8)){
            String line = null;
            while( (line=reader.readLine())!=null ){
                line = line.trim();
                if ( line.length()>0 )
                    lines.add(line);
            }
        }
        return lines;
    }

    public static void copy(File src, File tgt) throws IOException {
        int read;
        byte [] buffer = new byte [4096];
        try( InputStream is = new FileInputStream(src);
             OutputStream os = new FileOutputStream (tgt); )
        {
            while ((read = is.read (buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        }
        tgt.setLastModified(src.lastModified());
        if ( tgt.length()!=src.length()){
            tgt.delete();
            throw new IOException("Copy "+src+" to "+tgt+" failed, file length is not matched.");
        }
    }

    public static void copy(InputStream srcIs, File tgt ) throws IOException {
        int read;
        byte [] buffer = new byte [4096];
        try( InputStream is = srcIs;
             OutputStream os = new FileOutputStream (tgt); )
        {
            while ((read = is.read (buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        }
    }

    public static String getFileMainName(File file) {
        String fname = file.getName();
        if ( fname.indexOf('.')<0) {
            return fname;
        }
        return fname.substring(0, fname.lastIndexOf('.'));
    }

    public static String read(File file) throws IOException
    {
        if ( !file.exists() || !file.canRead() ){
            throw new IOException("File "+file+" not exists or not readable");
        }
        StringWriter writer = new StringWriter();
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));){
            char cbuf[] = new char[4096];
            int clen;
            while ( (clen=reader.read(cbuf))>0 ){
                writer.write(cbuf,0,clen);
            }
        }
        return writer.toString();
    }

    public static List<String> readLines(File file) throws IOException
    {
        return IOUtil.readLines(new FileInputStream(file));
    }

    public static Properties readProps(File file) throws IOException
    {
        return readProps(new FileInputStream(file));
    }

    public static Properties readProps(InputStream is) throws IOException
    {
        try(InputStreamReader reader = new InputStreamReader(is, StringUtil.UTF8);){
            Properties props = new Properties();
            props.load(reader);
            return props;
        }
    }

    public static String read(InputStream is, String encoding) throws IOException
    {
        if ( encoding==null ){
            encoding = "UTF-8";
        }
        char[] cbuf = new char[4096];
        StringBuilder text= new StringBuilder(4096);
        try( InputStreamReader reader = new InputStreamReader(is, encoding); ){
            int len=0;
            while( (len=reader.read(cbuf))>0 ){
                text.append(cbuf, 0, len);
            }
        }
        return text.toString();
    }

    public static BufferedReader bufferedRead(File file, String encoding) throws IOException {
        if ( encoding==null ){
            return new BufferedReader(new FileReader(file));
        }else{
            return new BufferedReader(new InputStreamReader(new FileInputStream(file), StringUtil.UTF8));
        }
    }

    public static BufferedWriter bufferedWrite(File file) throws IOException{
        return new BufferedWriter( new OutputStreamWriter(new FileOutputStream(file), StringUtil.UTF8) );
    }

    public static void save(File file, List<String> confText) throws IOException
    {
        try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StringUtil.UTF8)); ){
            boolean needsNewLine = false;
            for(String line:confText){
                if ( needsNewLine ){
                    writer.write("\n");
                }
                writer.write(line);
                needsNewLine = true;
            }
        }
    }

    public static void save(File file, String text) throws IOException
    {
    	save(file, text, false);
    }

    public static void save(File file, String content, boolean ifAppend) throws IOException
    {
    	try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, ifAppend), StringUtil.UTF8));){
    		writer.write(content);
    	}
    }

    public static void save(File file, InputStream is) throws IOException
    {
        try(FileOutputStream fos = new FileOutputStream(file);
                InputStream fis = is;)
        {
            byte[] data=new byte[4096];
            int len=0;
            while( (len=fis.read(data))>0 ){
                fos.write(data, 0, len);
            }
            fos.flush();
        }
    }

    public static void setLastModifiedTime(File file, Instant lastModified) throws IOException
    {
        Files.setLastModifiedTime(file.toPath(), FileTime.from(lastModified));
    }

    public static boolean delete(File file){
        if ( !file.exists() ){
            return false;
        }
        if ( file.isDirectory() ){
            File[] files = file.listFiles();
            if ( files!=null ){
                for(File f:files){
                    delete(f);
                }
            }
            return file.delete();
        }else{
            return file.delete();
        }
    }

    /**
     * 修改文件的后缀
     * @return 新的文件名
     */
    public static File changeSuffix(File file, String suffix)
    {
        String apath = file.getAbsolutePath();
        String npath = null;
        if ( apath.lastIndexOf(".")>0 ){
            int idx = apath.lastIndexOf(".");
            npath = apath.substring(0, idx)+"."+suffix;
        }else{
            npath = apath+"."+suffix;
        }
        return new File(npath);
    }

    /**
     * 递归遍历所有文件
     */
    public static List<File> listAllFiles(File dir, FileFilter filter) {
        List<File> result = new ArrayList<>();
        LinkedList<File> dirs = new LinkedList<>();
        dirs.add( dir );
        while(!dirs.isEmpty()) {
            File file = dirs.poll();
            if ( file.isDirectory() ) {
                File[] files = file.listFiles();
                if (files!=null) {
                    dirs.addAll(Arrays.asList(files));
                }
                continue;
            }
            if ( filter==null || filter.accept(file) ) {
                result.add(file);
            }
        }
        return result;
    }

    public static List<File> listSubDirs(File dir) {
        List<File> dirs = new ArrayList<>();
        File[] files = dir.listFiles();
        if ( files==null || files.length==0 ) {
            return Collections.emptyList();
        }
        for(File file:files) {
            if ( file.isDirectory() ) {
                dirs.add(file);
            }
        }
        Collections.sort(dirs);
        return dirs;
    }

    public static String md5(File file) throws Exception
    {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = new FileInputStream(file);)
        {
            byte[] buffer = new byte[4096];
            int len=0;
            while( (len=is.read(buffer))>0 ){
                md.update(buffer, 0, len);
            }
        }
        byte[] digest = md.digest();
        StringBuilder result = new StringBuilder();
        for (int i=0; i < digest.length; i++) {
            result.append( Integer.toString( ( digest[i] & 0xff ) + 0x100, 16).substring(1) );
        }
        return result.toString();
    }

    public static String md5(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(data);

        byte[] digest = md.digest();
        StringBuilder result = new StringBuilder();
        for (int i=0; i < digest.length; i++) {
            result.append( Integer.toString( ( digest[i] & 0xff ) + 0x100, 16).substring(1) );
        }
        return result.toString();
    }

    private static class WatchInfo{
        File file;
        long timestamp;
        WatchKey key;
        WeakReference<FileWatchListener> listenerRef;
    }

    private static Thread watchThread;
    private static WatchService watchService;
    private static List<WatchInfo> watchInfos = new LinkedList<>();
    private static List<WatchInfo> toWatchInfos = Collections.synchronizedList(new LinkedList<>());
    private static final long FULL_CHECK_INTERVAL = 15*1000;
    private static long lastCheckTime;

    private static void startWatchThread() throws IOException
    {
        if ( watchThread==null ) {
            watchService = FileSystems.getDefault().newWatchService();
            watchThread = new Thread(()->{
                watchThreadFunc();
            }, "File watch thread");
            watchThread.setDaemon(true);
            watchThread.start();
        }
    }

    private static void watchThreadFunc() {
        while(true) {
            WatchKey key = null;
            try {
                key = watchService.poll(1, TimeUnit.SECONDS);
            }catch(Throwable t) {}

            if ( key!=null ) {
                try{
                    List<WatchEvent<?>> events = key.pollEvents();
                    for(WatchEvent event:events) {
                        for(Iterator<WatchInfo> it=watchInfos.iterator(); it.hasNext();) {
                            WatchInfo info=it.next();
                            if ( info.key==key && info.file.getName().equals(event.context().toString())) {
                                FileWatchListener listener = info.listenerRef.get();
                                if ( listener==null ) {
                                    it.remove();
                                } else {
                                    info.timestamp = info.file.lastModified();
                                    listener.onFileChanged(info.file);
                                }
                                break;
                            }
                        }
                    }
                }catch(Throwable t) {}
                lastCheckTime = System.currentTimeMillis();
            } else if ( (System.currentTimeMillis()-lastCheckTime)>FULL_CHECK_INTERVAL ) {
                for(Iterator<WatchInfo> it=watchInfos.iterator(); it.hasNext();) {
                    WatchInfo info=it.next();
                    FileWatchListener listener = info.listenerRef.get();
                    if ( listener==null ) {
                        it.remove();
                        continue;
                    }
                    if ( info.file.lastModified()!=info.timestamp ) {
                        info.timestamp = info.file.lastModified();
                        listener.onFileChanged(info.file);
                    }
                }
                lastCheckTime = System.currentTimeMillis();
            }
            while(!toWatchInfos.isEmpty()) {
                WatchInfo watchInfo = toWatchInfos.remove(0);
                try{
                    watchInfo.key = watchInfo.file.getParentFile().toPath().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                    watchInfos.add(watchInfo);
                }catch(Throwable t) {
                    t.printStackTrace(System.out);
                }
            }
        }
    }

    public static void watchOn(File file, FileWatchListener listener) throws IOException
    {
        startWatchThread();
        WatchInfo info = new WatchInfo();
        info.file = file;
        info.listenerRef = new WeakReference<FileWatchListener>(listener);
        info.timestamp = file.lastModified();
        toWatchInfos.add(info);
    }

}
