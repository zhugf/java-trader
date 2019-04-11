package trader.common.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipFileUtil {

    public static void archiveRemove(File zip, String pathInZip)
            throws IOException
    {
        LinkedList<String> pathInZips = new LinkedList<>();
        pathInZips.add(pathInZip);
        archiveAdd(zip, pathInZips, null);
    }

    public static void archiveRemoveAll(File zip, final List<String> pathInZips)
            throws IOException
    {
        archiveAdd(zip, pathInZips, null);
    }

    public static void archiveAdd(File zip, File toAdd, String pathInZip)
            throws IOException
    {
        if ( pathInZip==null ) {
            pathInZip = toAdd.getName();
        }
        ZipEntryWriter writer = (ZipOutputStream append, int dataIndex)->{
            FileInputStream fis = new FileInputStream(toAdd);
            byte[] buffer = new byte[128000];
            int bytesRead;
            while ((bytesRead = fis.read(buffer))!= -1) {
                append.write(buffer, 0, bytesRead);
            }
            fis.close();
        };

        archiveAdd(zip, Arrays.asList(new String[]{pathInZip}), writer);
    }

    public static void archiveAddAll(File zip, final List<String> pathInZips, final List<byte[]> datas)
            throws IOException
    {
        ZipEntryWriter writer = (ZipOutputStream append, int pathIndex)->{
            append.write(datas.get(pathIndex));
        };
        archiveAdd(zip, pathInZips, writer);
    }

    public static void archiveAdd(File zip, byte[] data, String pathInZip)
            throws IOException
    {
        archiveAddAll(zip, Arrays.asList(new String[]{pathInZip}) , Arrays.asList(new byte[][]{data}));
    }

    public static ZipEntry[] listEntries(File zip, String classification) throws IOException
    {
        if ( !zip.exists() ) {
            return null;
        }
        LinkedList<ZipEntry> result = new LinkedList<>();
        ZipFile originalZip = new ZipFile(zip);
        Enumeration<? extends ZipEntry> entries = originalZip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if ( e.isDirectory() ) {
                continue;
            }
            if ( classification==null ){
                result.add(e);
                continue;
            }
            String entryName = e.getName();
            if ( entryName.indexOf(classification)>0 ) {
                result.add(e);
            }
        }
        originalZip.close();
        return result.toArray(new ZipEntry[result.size()]);
    }

    public static boolean arhiveExists(File zip, String pathInZip)
            throws IOException
    {
        if ( !zip.exists() ) {
            return false;
        }
        boolean found = false;
        ZipFile originalZip = new ZipFile(zip);
        Enumeration<? extends ZipEntry> entries = originalZip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if ( e.getName().equals(pathInZip) && e.getSize()>2 ){
                found = true;
                break;
            }
        }
        originalZip.close();
        return found;
    }

    public static String archiveRead(File zip, String pathInZip)
            throws IOException
    {
        try(ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))){
            ZipEntry e=null;
            while( (e=zis.getNextEntry())!=null ){
                if ( pathInZip.equals(e.getName())){
                    break;
                }
            }
            if ( e==null ){
                throw new IOException("Entry "+pathInZip+" not exists in "+zip.getCanonicalPath());
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len=0;
            while( (len=zis.read(buffer))>0){
                baos.write(buffer, 0, len);
            }
            return new String(baos.toByteArray(),"UTF-8");
        }
    }

    /**
     * returns a list of file name and content(in UTF-8) format.
     */
    public static List<String> archiveReadAll(File zip)
            throws IOException
    {
        List<String> result = new LinkedList<>();
        try(ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))){
            ZipEntry e=null;
            while( (e=zis.getNextEntry())!=null ){
                if ( e.isDirectory() ) {
                    continue;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len=0;
                while( (len=zis.read(buffer))>0){
                    baos.write(buffer, 0, len);
                }
                String text = new String(baos.toByteArray(),"UTF-8");
                result.add(e.getName());
                result.add(text);
            }
        }
        return result;
    }

    static void archiveAdd(File zip, List<String> pathInZips, ZipEntryWriter writer )
            throws IOException
    {
        File zipTemp = new File(zip.getAbsolutePath()+"-"+System.currentTimeMillis()+".tmp");
        ZipOutputStream append = new ZipOutputStream(new FileOutputStream(zipTemp));
        append.setLevel(Deflater.BEST_COMPRESSION);
        //copy contents from existing zip file
        if ( zip.exists() ){
            ZipFile originalZip = new ZipFile(zip);
            Enumeration<? extends ZipEntry> entries = originalZip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if ( pathInZips.contains(e.getName()) ) {
                    continue;
                }
                append.putNextEntry(new ZipEntry(e.getName()));
                if (!e.isDirectory()) {
                    copy(originalZip.getInputStream(e), append);
                }
                append.closeEntry();
            }
            originalZip.close();
        }
        // now append some extra content
        if ( writer!=null ){
            for(int i=0;i<pathInZips.size();i++){
                String pathInZip = pathInZips.get(i);
                ZipEntry e = new ZipEntry(pathInZip);
                append.putNextEntry(e);
                writer.write(append, i);
                append.closeEntry();
            }
        }
        append.close();
        if ( !zip.exists() || zip.delete() ){
            zipTemp.renameTo(zip);
        }else{
            zipTemp.delete();
            throw new IOException("Unable to delete old zip archive "+zip);
        }
    }


    /**
     * copy input to output stream - available in several StreamUtils or Streams classes
     */
    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[128000];
        int bytesRead;
        while ((bytesRead = input.read(buffer))!= -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    @FunctionalInterface
    private static interface ZipEntryWriter{
        void write(ZipOutputStream append, int pathIndex) throws IOException;
    }

}
