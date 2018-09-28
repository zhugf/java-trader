package trader.common.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Properties;

public class IniWriter extends Writer{
    private BufferedWriter out;

    public IniWriter(Writer writer){
        this.out = (writer instanceof BufferedWriter)?(BufferedWriter)writer
                : new BufferedWriter(writer);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        out.write(cbuf, off, len);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    public void writeSection(String section) throws IOException
    {
        out.write("\n["+section+"]\n");
    }

    public void writeProperty(String propName, Object propValue) throws IOException
    {
        out.write(propName+"="+propValue+"\n");
    }

    public void writeProperties(Properties props) throws IOException
    {
        props.store(out, null);
    }

    public void newLine()throws IOException
    {
        out.newLine();
    }

}
