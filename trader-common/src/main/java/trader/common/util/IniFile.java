package trader.common.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * A simple ini file parser
 */
public class IniFile {
    public static class Section {
        private String name;
        private String text;
        private Properties props;

        void setText(String text) {
            this.text = text;
        }

        public String getName() {
            return name;
        }

        public String getText() {
            return text;
        }

        public Properties getProperties() {
            if (props == null) {
            	props = StringUtil.text2properties(text);
            }
            return props;
        }

        public String get(String key) {
            return getProperties().getProperty(key);
        }

        @Override
        public String toString() {
            return "["+name+"]\n"+text;
        }
    }

    private List<Section> sections = new LinkedList<Section>();

    public IniFile(File file) throws IOException {
        this(new FileInputStream(file));
    }

    public IniFile(InputStream is) throws IOException
    {
        this(new InputStreamReader(is,StringUtil.UTF8));
    }

    public IniFile(Reader reader) throws IOException
    {
        try (BufferedReader br = new BufferedReader(reader);) {
            Section section = null;
            String line = null;
            StringBuilder sectionText = new StringBuilder();
            while ((line = br.readLine()) != null) {
                String tline = line.trim();
                if (section == null && tline.length() == 0) {
                    continue;
                }
                if (line.startsWith("#")) {
                    continue;
                }
                if (tline.startsWith("[") && tline.endsWith("]")) {
                    if (section != null) {
                        section.setText(sectionText.toString());
                    }
                    section = new Section();
                    section.name = tline.substring(1, tline.length() - 1);
                    sectionText = new StringBuilder();
                    sections.add(section);
                    continue;
                }
                if (section != null) {
                    if (sectionText.length() > 0) {
                        sectionText.append("\n");
                    }
                    sectionText.append(line);
                }
            }
            section.setText(sectionText.toString());
        }
    }

    public Collection<Section> getAllSections() {
        return Collections.unmodifiableList(sections);
    }

    public Section getSection(String name) {
        for (Section s : sections) {
            if (s.name.equals(name)) {
                return s;
            }
        }
        return null;
    }

}
