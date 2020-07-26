package trader.common.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;

/**
 * 使用类似XML DOM结构表达配置参数
 */
public class ConfigItem {
	public static final Pattern PATTERN_ARRAY_IDX = Pattern.compile("(.*)\\[(\\d+)\\]");
    public static final String PATTERN_KEY_SPLIT = "/|\\.";

	private final String name;
	private String value;
	private Map<String, String> attrs;
	private List<ConfigItem> children = null;

	public ConfigItem(String name, String value, Map<String, String> attrs) {
		this.name = name;
		this.value = value;
		if ( null!=attrs && attrs.size()>0 ) {
			this.attrs = new HashMap<>(attrs);
		}
	}

	public String getName() {
		return name;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String val) {
		this.value = val;
	}

	public Map<String, String> getAttrs(){
		Map<String, String> result = attrs;
		if ( null==result ) {
			result = Collections.emptyMap();
		}
		return result;
	}

	public void setAttr(String k, String v) {
		if ( null==attrs) {
			attrs = new HashMap<>();
		}
		if ( StringUtil.isEmpty(v)) {
			attrs.remove(k);
		} else {
			attrs.put(k, v);
		}
	}

	/**
	 * 子节点, 两种风格: a, a[2]
	 */
	public ConfigItem getItem(String name) {
		ConfigItem result = null;
		if ( null!=children ) {
			Matcher m = PATTERN_ARRAY_IDX.matcher(name);
            if ( m.matches() ) {
                String name0 = m.group(1);
                int idx = ConversionUtil.toInt(m.group(2), true);
                List<ConfigItem> items = getItems(name0);
                if (items.size()>idx) {
                    result = items.get(idx);
                }
            } else {
				for(ConfigItem item:children) {
					if ( item.getName().equals(name)) {
						result = item;
						break;
					}
				}
            }
		}
		return result;
	}

	public List<ConfigItem> getItems(String name){
		if ( name.endsWith("[]")) {
			name = name.substring(0, name.length()-2);
		}
		List<ConfigItem> result = null;
		if ( StringUtil.isEmpty(name) ) {
			result = this.children;
		} else {
			result = new ArrayList<>();
			if ( null!=children ) {
				for(ConfigItem item:children) {
					if ( item.getName().equals(name)) {
						result.add(item);
					}
				}
			}
		}
		if ( null==result ) {
			result = Collections.emptyList();
		}
		return result;
	}

	/**
	 * 是叶子节点, 没有子Item
	 */
	public boolean isLeaf() {
		return null==children || children.isEmpty();
	}

	public void addChild(ConfigItem item) {
		if (null==children) {
			children = new ArrayList<>();
		}
		children.add(item);
	}

	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(getName());
		if ( !StringUtil.isEmpty(value)) {
			builder.append("=").append(getValue());
		}
		if ( null!=attrs ) {
			builder.append(" ").append(attrs.toString());
		}
		return builder.toString();
	}

	public ConfigItem clone() {
		ConfigItem item = new ConfigItem(this.name, this.value, this.attrs);
		if ( null!=children ) {
			for(ConfigItem child:children) {
				item.addChild(child.clone());
			}
		}
		return item;
	}

	public static List<ConfigItem> clone(List<ConfigItem> items){
		List<ConfigItem> result = new ArrayList<>(items.size());
		for(ConfigItem item:items) {
			result.add(item.clone());
		}
		return result;
	}

	/**
	 * 从两份原始配置参数合并出第三份配置参数, 原始值不变
	 *
	 * @param items
	 * @param items2
	 * @return 合并后的值
	 */
	public static List<ConfigItem> merge(List<ConfigItem> items, List<ConfigItem> items2){
		//复制一份出来, 避免修改原始值
		List<ConfigItem> result = clone(items);

		for(ConfigItem item2:items2) {
			item2 = item2.clone();
			ConfigItem item = getItem(result, item2.getName());
			if ( null==item ) {
				result.add(item2);
			} else {
				merge(item, item2);
			}
		}
		return result;
	}

	public static ConfigItem getItem(List<ConfigItem> items, String name) {
		ConfigItem result = null;
		for(ConfigItem item:items) {
			if ( StringUtil.equals(name, item.getName())) {
				result = item;
				break;
			}
		}
		return result;
	}

    public static void buildItem(List<ConfigItem> items, String path, String val) {
    	String[] parts = StringUtil.split(path, PATTERN_KEY_SPLIT);
    	if ( parts.length==1) {
    		ConfigItem item = getOrCreateItem(items, parts[0]);
    		item.setValue(val);
    	} else {
	    	ConfigItem item = null;
	    	for(int i=0;i<parts.length;i++) {
	    		boolean last = i+1==parts.length;
	    		String part = parts[i];
	    		if ( !last ) {
	    			if ( null==item ) {
	    				item = getOrCreateItem(items, part);
	    			} else {
	    				if ( null==item.getItem(part) ) {
	    					ConfigItem item0 = new ConfigItem(part, null, null);
	    					item.addChild(item0);
	    					item = item0;
	    				} else {
		    				item = item.getItem(part);
	    				}
	    			}
	    		} else {
	    			item.setAttr(part, val);
	    		}
	    	}
    	}
    }

    private static ConfigItem getOrCreateItem(List<ConfigItem> items, String name) {
    	ConfigItem item = null;
		for(ConfigItem item0:items) {
			if ( item0.getName().equals(name)) {
				item = item0;
				break;
			}
		}
		if ( null==item) {
			item = new ConfigItem(name, null, null);
			items.add(item);
		}
		return item;
    }

	private static void merge(ConfigItem item, ConfigItem item2) {
		if ( !StringUtil.isEmpty(item2.value) ) {
			item.value = item2.value;
		}
		if ( null!=item2.attrs ) {
			Map<String, String> attrs = new HashMap<>();
			if ( null!=item.attrs) {
				attrs = item.attrs;
			}
			attrs.putAll(item2.attrs);
			item.attrs = attrs;
		}
		if ( null!=item2.children ) {
			if ( null==item.children) {
				item.children = new ArrayList<>();
			}
			for(ConfigItem child2:item2.children) {
				List<ConfigItem> children2 = item2.getItems(child2.getName());
				List<ConfigItem> children0 = item.getItems(child2.getName());
				if ( children2.size()==1 || children0.size()==1 ) {
					ConfigItem child0 = children0.get(0);
					merge(child0, child2);
				} else {
					//#1有多个同名Child #2 item没有同名child, 添加而不合并
					item.children.add(child2);
				}
			}
		}
	}

}
