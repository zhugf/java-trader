package trader.common.util;

import java.io.*;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

public class IOUtil {

	public static BufferedWriter createBufferedWriter(File file, Charset charset, boolean append) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file,append), charset));
		return writer;
	}

	public static BufferedReader createBufferedReader(InputStream is) throws IOException {
		return new BufferedReader(new InputStreamReader(is, StringUtil.UTF8));
	}

    public static BufferedReader createBufferedReader(File file, Charset charset) throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), StringUtil.UTF8));
    }

    public static String readAsString(InputStream is) throws IOException {
        StringWriter writer = new StringWriter();
        char cbuf[] = new char[4096];
        int len=0;
        try(Reader reader =new InputStreamReader(is, StringUtil.UTF8);){
            while( (len=reader.read(cbuf))>0 ) {
                writer.write(cbuf, 0, len);
            }
        }
        return writer.toString();
    }

    public static List<String> readLines(InputStream is) throws IOException
    {
        LinkedList<String> lines = new LinkedList<>();
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(is, StringUtil.UTF8)); ){
            String line = null;
            while( (line=reader.readLine())!=null ){
                line = line.trim();
                if ( line.length()>0 )
                    lines.add(line);
            }
        }
        return lines;
    }

}
