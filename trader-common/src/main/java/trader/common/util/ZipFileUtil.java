package trader.common.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.enums.CompressionLevel;

public class ZipFileUtil {

    public static void archiveRemove(File zip, String pathInZip)
            throws IOException
    {
        LinkedList<String> pathInZips = new LinkedList<>();
        pathInZips.add(pathInZip);
        archiveRemoveAll(zip, pathInZips);
    }

    public static void archiveRemoveAll(File zip, final List<String> pathInZips)
            throws IOException
    {
        net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(zip);
        for(String pathInZip:pathInZips) {
            zipFile.removeFile(pathInZip);
        }
    }

    public static void archiveAdd(File zip, File toAdd, String pathInZip)
            throws IOException
    {
        if ( pathInZip==null ) {
            pathInZip = toAdd.getName();
        }

        net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(zip);

        List<FileHeader> items = zipFile.getFileHeaders();
        for(FileHeader zipItem : items) {
            if ( zipItem.getFileName().equalsIgnoreCase(pathInZip)) {
                zipFile.removeFile(zipItem);
            }
        }
        net.lingala.zip4j.model.ZipParameters zipParams = new net.lingala.zip4j.model.ZipParameters();
        zipParams.setCompressionLevel(CompressionLevel.MAXIMUM);
        zipParams.setFileNameInZip(pathInZip);
        zipFile.addFile(toAdd, zipParams);

    }

    public static void archiveAddAll(File zip, final List<String> pathInZips, final List<byte[]> datas)
            throws IOException
    {
        net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(zip);

        List<FileHeader> items = new ArrayList<>( zipFile.getFileHeaders() );

        for(FileHeader zipItem : items) {
            if ( pathInZips.contains(zipItem.getFileName()) ) {
                zipFile.removeFile(zipItem);
            }
        }

        for(int i=0;i<pathInZips.size();i++) {
            net.lingala.zip4j.model.ZipParameters zipParams = new net.lingala.zip4j.model.ZipParameters();
            byte[] data = datas.get(i);
            zipParams.setCompressionLevel(CompressionLevel.MAXIMUM);
            zipParams.setFileNameInZip(pathInZips.get(i));
            zipFile.addStream(new ByteArrayInputStream(data), zipParams);
        }
    }

    public static void archiveAdd(File zip, byte[] data, String pathInZip)
            throws IOException
    {
        archiveAddAll(zip, Arrays.asList(new String[]{pathInZip}) , Arrays.asList(new byte[][]{data}));
    }

    public static ZipEntry[] listEntries(File zip, String classification) throws IOException
    {
        if ( !zip.exists() ) {
            return new ZipEntry[0];
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

    @FunctionalInterface
    private static interface ZipEntryWriter{
        void write(ZipOutputStream append, int pathIndex) throws IOException;
    }

}
