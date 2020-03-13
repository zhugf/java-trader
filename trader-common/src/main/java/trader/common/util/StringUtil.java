package trader.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StringUtil
{
    private static final Logger logger = LoggerFactory.getLogger(StringUtil.class);

    /**
     * 文本格式
     */
    public static enum TextFormat {
        PlainText(true, false),
        Ini(true, false),
        Property(true, false),
        JsonArray(false, true),
        JsonObject(false, true);

        private boolean text;
        private boolean json;

        private TextFormat(boolean text, boolean json){
            this.text = text;
            this.json = json;
        }
        public boolean isText() {
            return text;
        }

        public boolean isJson() {
            return json;
        }
    }

    public static class KVPair{
        public final String k;
        public final String v;
        public final String str;
        public KVPair(String k, String v, String str) {
            this.k = k;
            this.v = v;
            this.str = str;
        }

        @Override
        public String toString() {
            return str;
        }
    }

    public static final Charset GBK = Charset.forName("GBK");
    public static final Charset UTF8 = Charset.forName("UTF-8");
    public static final Charset UTF16 = Charset.forName("UTF-16");
    public static final Charset UTF32 = Charset.forName("UTF-32");
    public static final Class<String> STRING_CLASS = String.class;

    public static String capitalize(final String str) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return str;
        }

        final char firstChar = str.charAt(0);
        final char newChar = Character.toTitleCase(firstChar);
        if (firstChar == newChar) {
            // already capitalized
            return str;
        }

        char[] newChars = new char[strLen];
        newChars[0] = newChar;
        str.getChars(1,strLen, newChars, 1);
        return String.valueOf(newChars);
    }

    public static String firstNotEmpty(String... strs) {
        for(String str:strs) {
            if (!StringUtil.isEmpty(str)) {
                return str;
            }
        }
        return null;
    }

    public static boolean isEmpty(String str)
    {
        return str==null || str.trim().length()==0;
    }

    public static boolean notEmpty(String str) {
    	return !isEmpty(str);
    }

    public static String trim(String str)
    {
        if ( str==null ){
            return null;
        }
        return str.trim();
    }

    public static boolean contains(String str, String str2)
    {
        if ( isEmpty(str2) ){
            return true;
        }
        if ( isEmpty(str) ){
            return false;
        }
        return str.contains(str2);
    }

    public static String append(String str, String toAppend, String delimiter)
    {
        boolean textIsEmpty = isEmpty(str);
        boolean toAppendIsEmpty = isEmpty(toAppend);

        if ( !textIsEmpty && !toAppendIsEmpty ){
            return str+delimiter+toAppend;
        }
        if ( textIsEmpty ){
            return toAppend;
        }else {
            return str;
        }
    }

    public static String removeFrom(String str, String toRemove, String delimiter){
        int index = str.indexOf(toRemove);
        if ( index<0 ) {
            return str;
        }
        if ( equals(str, toRemove) ) {
            return "";
        }
        String before = "";
        String after="";
        if ( index==0 ) {
            after = str.substring(toRemove.length());
        }else {
            before = str.substring(0, index);
            after = str.substring(index+toRemove.length());
        }
        if ( after.startsWith(delimiter)) {
            after = after.substring(delimiter.length());
        }
        return before+after;
    }

    public static int compareTo(String str1, String str2) {
        boolean str1e = isEmpty(str1);
        boolean str2e = isEmpty(str2);

        if ( str1e && str2e ) {
            return 0;
        } else if ( str1e && !str2e ) {
            return -1;
        } else if ( !str1e && str2e ) {
            return 1;
        }
        return str1.compareTo(str2);
    }

    /**
     * 大小写敏感比较
     */
    public static boolean equals(String str1, String str2){
        boolean str1e = isEmpty(str1);
        boolean str2e = isEmpty(str2);
        //both empty
        if ( str1e && str2e ){
            return true;
        }
        if ( str1e!=str2e ){
            return false;
        }
        //both not empty
        return str1.equals(str2);
    }

    /**
     * 大小写无关比较
     */
    public static boolean equalsIgnoreCase(String str1, String str2){
        boolean str1e = isEmpty(str1);
        boolean str2e = isEmpty(str2);
        //both empty
        if ( str1e && str2e ){
            return true;
        }
        if ( str1e!=str2e ){
            return false;
        }
        return str1.equalsIgnoreCase(str2);
    }

    public static String[] split(String str, String regex) {
        if ( StringUtil.isEmpty(str)) {
            return new String[] {};
        }
        List<String> interfaces = new ArrayList<>();
        for(String elem: str.split(regex)) {
            elem = elem.trim();
            if ( !StringUtil.isEmpty(elem)) {
                interfaces.add(elem.intern());
            }
        }
        return interfaces.toArray(new String[interfaces.size()]);
    }

    public static List<String[]> splitKVs(String str){
        List<String[]> result = new ArrayList<>();
        for(String p0:str.split("(,|;)\\s*")) {
            String kv[] = p0.split("\\s*(:|=)\\s*");
            result.add(kv);
        }
        return result;
    }

    /**
     * 将多行文本按行切分
     */
    public static List<String> text2lines(String text, boolean trim, boolean ignoreEmptyLines){
        List<String> result = new ArrayList<>();
        if ( ignoreEmptyLines ) {
            trim = true;
        }
        if ( !StringUtil.isEmpty(text) ) {
            try(BufferedReader br = new BufferedReader(new StringReader(text));) {
                String line = null;

                while( (line=br.readLine())!=null ) {
                    if ( trim) {
                        line = line.trim();
                    }
                    if ( ignoreEmptyLines && line.isEmpty() ) {
                        continue;
                    }
                    result.add(line);
                }
            } catch (IOException e) {}
        }
        return result;
    }

    public static String lines2text(List<String> lines) {
        return concatObject(lines.toArray(new String[lines.size()]), "\n", null, null, false);
    }

    public static String concat(String[] strs, String delimiter, String begin, String end) {
        return concatObject(strs, delimiter, begin, end, false);
    }

    public static String concatString(String[] strs, String delimiter, String begin, String end) {
        return concatObject(strs, delimiter, begin, end, true);
    }

    public static String concatObject(String[] strs, String delimiter, String begin, String end, boolean doubleMarks) {
        StringBuilder result = new StringBuilder(128);
        if( !StringUtil.isEmpty(begin) ) {
            result.append(begin);
        }
        for(int i=0;i<strs.length;i++) {
            if (i>0) {
                result.append(delimiter);
            }
            if( doubleMarks ) {
                result.append("\"").append(strs[i]).append("\"");
            } else {
                result.append(strs[i]);
            }
        }
        if ( !StringUtil.isEmpty(end) ) {
            result.append(end);
        }
        return result.toString();
    }

    public static Properties text2properties(String text)
    {
        Properties props = new Properties();
        if ( !StringUtil.isEmpty(text) ) {
            try(BufferedReader reader=new BufferedReader(new StringReader(text));){
                String preLine=null;
                String line = null;
                while( (line=reader.readLine())!=null ){
                    line = line.trim();
                    if ( preLine!=null ) {
                        line = preLine+line;
                        preLine = null;
                    }
                    if ( line.startsWith("#")){
                        continue;
                    }
                    if ( line.endsWith("\\")) {
                        preLine = line;
                        continue;
                    }
                    int equalIndex = line.indexOf("=");
                    if ( equalIndex<0 ){
                        continue;
                    }
                    String key = line.substring(0, equalIndex).trim().intern();
                    String val = StringUtil.unquotes(line.substring(equalIndex+1).trim()).intern();

                    props.setProperty(key, val);
                }
            }catch(IOException e){}
        }
        return props;
    }

    /**
     * 解析参数为Key-Value值对
     */
    public static List<KVPair> args2kvpairs(List<String> args){
        List<KVPair> result = new ArrayList<>();
        for(String arg:args) {
            if ( arg.startsWith("--")) {
                arg = arg.substring(2);
            }else if ( arg.startsWith("-")) {
                arg = arg.substring(1);
            }
            int idx=arg.indexOf('=');
            if ( idx>0 ) {
                result.add(new KVPair(arg.substring(0, idx), arg.substring(idx+1), arg));
            }else {
                result.add(new KVPair(arg, null, arg));
            }
        }
        return result;
    }

    /**
     * k1=v1;k2=v2转换为kv对
     */
    public static List<KVPair> line2kvpairs(String line){
        List<KVPair> result = new ArrayList<>();
        for(String[] kv:splitKVs(line) ) {
            if( kv.length>=2 ) {
                result.add(new KVPair(kv[0], kv[1], kv[0]+"="+kv[1]));
            } else {
                result.add(new KVPair(kv[0], null, kv[0]));
            }
        }
        return result;
    }

    public static String md5(String input) {
        byte[] source;
        try {
            //Get byte according by specified coding.
            source = input.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            source = input.getBytes();
        }
        String result = null;
        char hexDigits[] = {'0', '1', '2', '3', '4', '5', '6', '7',
                '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(source);
            //The result should be one 128 integer
            byte temp[] = md.digest();
            char str[] = new char[16 * 2];
            int k = 0;
            for (int i = 0; i < 16; i++) {
                byte byte0 = temp[i];
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            result = new String(str);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * 去引号
     */
    public static String unquotes(String str)
    {
        if ( StringUtil.isEmpty(str)){
            return str;
        }
        if ( str.charAt(0)=='"' && str.charAt(str.length()-1)=='"' ){
            return str.substring(1, str.length()-1);
        }
        if ( str.charAt(0)=='\'' && str.charAt(str.length()-1)=='\'' ){
            return str.substring(1, str.length()-1);
        }
        return str;
    }

    public static boolean booleanValue(String boolStr, boolean defaultValue){
        if( isEmpty(boolStr) ){
            return defaultValue;
        }
        boolStr = boolStr.trim().toLowerCase();
        return "true".equalsIgnoreCase(boolStr) || "yes".equalsIgnoreCase(boolStr);
    }

    public static String urlEscape(String url) {
        int i;
        char j;
        StringBuffer tmp = new StringBuffer();
        tmp.ensureCapacity(url.length() * 6);
        for (i = 0; i < url.length(); i++) {
            j = url.charAt(i);
            if (Character.isDigit(j) || Character.isLowerCase(j)
                    || Character.isUpperCase(j))
                tmp.append(j);
            else if (j < 256) {
                tmp.append("%");
                if (j < 16)
                    tmp.append("0");
                tmp.append(Integer.toString(j, 16));
            } else {
                tmp.append("%u");
                tmp.append(Integer.toString(j, 16));
            }
        }
        return tmp.toString();
    }

    /***
     * 转义特殊字符
     *
     * @param keyword
     * @return
     */
    public static String escapeExprSpecialWord(String keyword) {
        StringBuilder result = new StringBuilder(keyword.length() + 32);
        int len = keyword.length();
        for (int i = 0; i < len; i++) {
            char ch = keyword.charAt(i);
            switch (ch) {
            case '\t':
                result.append("\\t");
                break;
            case '\n':
                result.append("\\n");
                break;
            case '\r':
                result.append("\\r");
                break;
            case '"':
                result.append("'");
                break;
            case '\\':
                result.append("\\\\");
                break;
            default:
                result.append(ch);
            }
        }
        return result.toString();
    }

    /*
     * when the line is null ,continue read next line
     * return text lines with no-null-line
     */
    public static LinkedList<String> parseMultiLineText(String text)
    {
        LinkedList<String> lines = new LinkedList<String>();
        try{
            BufferedReader br=new BufferedReader(new StringReader(text));
            String line=null;
            while( (line=br.readLine())!=null ){
                if ( line==null || line.trim().length()==0 )
                    continue;
                lines.add(line);
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return lines;
    }

    public static String throwable2string(Throwable t) {
        StringWriter w = new StringWriter();
        t.printStackTrace(new PrintWriter(w, true));
        return w.toString();
    }

    public static String castLongOrIntegerToString(Object text) {
        if(text instanceof Integer || text instanceof Long) {
            return text.toString();
        }
        else if(text instanceof String){
            return (String)text;
        }else {
            return "0";
        }
    }

    public static String replaceBlank(String str) {
        String dest = "";
        if (str!=null) {
            Pattern p = Pattern.compile("\\s*|\t|\r|\n");
            Matcher m = p.matcher(str);
            dest = m.replaceAll("");
        }
        return dest;
    }

    public static String unicodeReplaceBlank(String str) {
        if (StringUtil.isEmpty(str)) {
            return null;
        }
        int length = str.length();
        char temp =' ';
        StringBuilder newStr = new StringBuilder(length);
        for(int i = 0; i < length; ++i) {
            temp = str.charAt(i);
            if(temp <= 255 ) {
                newStr.append(temp);
                continue;
            }

            Character.UnicodeBlock block = Character.UnicodeBlock.of(temp);
            if(Character.UnicodeBlock.GENERAL_PUNCTUATION!=block) {
                newStr.append(temp);
            }
        }
        return newStr.toString();
    }

    /**
     * 用来判断某个字符串是否能转成非String类型的其它类型
     */
    public static boolean isDouble(String str) {
        try {
            double num = Double.valueOf(trim(str));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 根据表达式抽离出 named group
     */
    public static Map<String, Integer> getPatternNamedGroups(Pattern pattern){
        try{
            Method namedGroupsMethod = Pattern.class.getDeclaredMethod("namedGroups");
            namedGroupsMethod.setAccessible(true);
            Map<String, Integer> namedGroupsValue = (Map<String, Integer>) namedGroupsMethod.invoke(pattern);
            return namedGroupsValue;
        }catch(Throwable t){}
        return Collections.emptyMap();
    }

    public static String wildcard2pattern(String wildcard) {
        StringBuilder pattern = new StringBuilder(wildcard.length()+10);
        for(int i=0;i<wildcard.length();i++) {
            char c = wildcard.charAt(i);
            switch(c) {
            case '?':
                pattern.append(".");
                break;
            case '*':
                pattern.append(".*");
                break;
            default:
                pattern.append(c);
            }
        }
        return pattern.toString();
    }

    /**
     * 支持*?的简单字符串匹配
     */
    public static boolean wildcardMatches(String str, String wildcard) {
        if (StringUtil.isEmpty(str)) {
            return false;
        }
        return str.matches(wildcard2pattern(wildcard));
    }

    /**
     * 验证是否为jsonObject
     */
    public static TextFormat detectTextFormat(String text){
        if( StringUtil.isEmpty(text) ) {
            return TextFormat.PlainText;
        }
        text = text.trim();
        if ( text.startsWith("[") && text.endsWith("]") ) {
            return TextFormat.JsonArray;
        }
        if ( text.startsWith("{") && text.endsWith("}") && text.indexOf(":")>0 ) {
            return TextFormat.JsonObject;
        }
        boolean hasHead = false;
        boolean hasKV = false;
        try(BufferedReader reader=new BufferedReader(new StringReader(text));){
            String line = null;
            while((line=reader.readLine())!=null) {
                line = line.trim();
                if ( line.startsWith("[") && line.endsWith("]")) {
                    hasHead = true;
                    continue;
                }
                if ( line.indexOf("=")>0 ) {
                    hasKV = true;
                }
                if ( hasHead && hasKV ) {
                    break;
                }
            }
        }catch(Throwable t) {}
        if ( hasHead && hasKV ) {
            return TextFormat.Ini;
        }
        if ( !hasHead && hasKV ) {
            return TextFormat.Property;
        }
        return TextFormat.PlainText;
    }

    public static String array2str(String[] array, String delimiter){
        if ( array==null||array.length==0 ) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for(String s:array){
            if ( builder.length()>0 ) {
                builder.append(delimiter);
            }
            builder.append(s);
        }
        return builder.toString();
    }

    public static String collection2str(Collection<String> array, String delimiter){
        StringBuilder builder = new StringBuilder();
        if ( array==null||array.size()==0 ) {
            return null;
        }
        for(String s:array){
            if ( builder.length()>0 ) {
                builder.append(delimiter);
            }
            builder.append(s);
        }
        return builder.toString();
    }

    public static boolean arrrayEquals(String[] arr1, String[] arr2, boolean ignoreCase){
        if ( arr1==null && arr2==null ) {
            return true;
        }
        if (arr1.length!=arr2.length) {
            return false;
        }
        for(int i=0;i<arr1.length;i++){
            if (ignoreCase){
                if (!arr1[i].equalsIgnoreCase(arr2[i])){
                    return false;
                }
            }else{
                if (arr1[i].equalsIgnoreCase(arr2[i])){
                    return false;
                }
            }
        }
        return true;
    }

}
