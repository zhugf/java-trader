package trader.common.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import trader.common.util.ConversionUtil;
import trader.common.util.StringUtil;

/**
 * 使用类似XML DOM结构表达配置参数
 */
public class ConfigItem {
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

	public String getId() {
	    String result = getAttr("id");
	    if (StringUtil.isEmpty(result)) {
	        result = getAttr("name");
	    }
	    return result;
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

	public List<ConfigItem> getChildren(){
		return  children;
	}

	public Map<String,Object> getAllValue(){
		Map<String,Object> result = new HashMap<>();
		result.putAll(getAttrs());
		if (children != null){
			List<Map<String,Object>> childrenList = new ArrayList<>();
			for(ConfigItem configItem:children){
				childrenList.add(configItem.getAllValue());
			}
			result.put("children",childrenList);
		}
		return result;
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
		attrs.put(k, v);
	}

	public String getAttr(String k) {
		return getAttrs().get(k);
	}

	/**
	 * 子节点, 3种风格: a, a[2], a[idOrName], a[id=id1,name=name2]
	 */
	public ConfigItem getItem(String name) {
		return getOrCreateItem(this.children, name, false);
	}

	public List<ConfigItem> getItems(String name){
		if ( name.endsWith("[]")) {
			name = name.substring(0, name.length()-2);
		}
		List<ConfigItem> result = null;
		if ( StringUtil.isEmpty(name) ) {
			result = this.children;
		} else {
			result = getItemsByName(children, name);
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
        LinkedList<String> parts = new LinkedList<>( Arrays.asList(StringUtil.split(path, PATTERN_KEY_SPLIT)));
        String attr = null;
        if ( parts.size()>1 && !(path.endsWith("/")||path.endsWith(".")) ) {
            attr = parts.pollLast();
        }
        ConfigItem item = null;
        for(String part:parts) {
		    // part 可以是: abc, abc[0], abc[id=id1,name=name2]
			if ( null==item ) {
				item = getOrCreateItem(items, part, true);
			} else {
			    item = getOrCreateChild(item, part);
			}
        }
        if (null!=item) {
        	if (null!=attr ) {
        	    item.setAttr(attr, val);
        	} else {
        	    item.setValue(val);
        	}
        }
    }

    private static ConfigItem getOrCreateChild(ConfigItem item, String childNameWithAttrs) {
        ConfigItem result = item.getItem(childNameWithAttrs);
        if ( null==result ) {
            if ( null==item.children) {
                item.children = new ArrayList<>();
            }
            result = getOrCreateItem(item.children, childNameWithAttrs, true);
        }
        return result;
    }

    private static ConfigItem getOrCreateItem(List<ConfigItem> items, String nameWithAttrs, boolean create) {
    	ConfigItem result = null;
    	if ( null==items ) {
    	    return null;
    	}
    	String itemName = null;
    	String itemAttrs = null;

    	if ( nameWithAttrs.endsWith("]") && nameWithAttrs.indexOf('[')>0) {
            // abc[0]
            itemName = nameWithAttrs.substring(0, nameWithAttrs.indexOf('['));
            itemAttrs = nameWithAttrs.substring(nameWithAttrs.indexOf('[')+1, nameWithAttrs.length()-1);
    	} else if ( nameWithAttrs.indexOf('#')>0 ) {
    	    itemName = nameWithAttrs.substring(0, nameWithAttrs.indexOf('#'));
    	    itemAttrs = nameWithAttrs.substring(nameWithAttrs.indexOf('#')+1);
    	}

    	if ( !StringUtil.isEmpty(itemAttrs)) {
            List<ConfigItem> children = getItemsByName(items, itemName);
            int attrIdx = ConversionUtil.toInt(itemAttrs, -1);
            if ( attrIdx>=0 ) { //name[0]
                if ( attrIdx<children.size() ) {
                    result = children.get(attrIdx);
                } else {
                    if ( create ) {
                        result = new ConfigItem(itemName, null, null);
                        items.add(result);
                    }
                }
            } else if ( itemAttrs.indexOf('=')>0||itemAttrs.indexOf(':')>0){
                //abc[a1=v1,a2=v2]
                List<String[]> attrKvs = StringUtil.splitKVs(itemAttrs);
                for(ConfigItem child:children) {
                    boolean attrMatch = true;
                    for(String[] kv:attrKvs) {
                        if ( kv.length>=2 ) {
                            if ( !StringUtil.equals(child.getAttr(kv[0]), kv[1])) {
                                attrMatch=false;
                                break;
                            }
                        } else {
                            if ( !StringUtil.equals(child.getId(), kv[0]))  {
                                attrMatch=false;
                                break;
                            }
                        }
                    }
                    if ( attrMatch ) {
                        result = child;
                        break;
                    }
                }
                if ( null==result && create) {
                    Map<String, String> attrs = new TreeMap<>();
                    for(String[] kv:attrKvs) {
                        if ( kv.length>=2 ) {
                            attrs.put(kv[0], kv[1]);
                        } else {
                            attrs.put("id", kv[0]);
                        }
                    }
                    result = new ConfigItem(itemName, null, attrs);
                    items.add(result);
                }
            } else {
                // abc[WEB]
                String idAttr = itemAttrs;
                for(ConfigItem child:children) {
                    if ( !StringUtil.equals(child.getId(), idAttr) ) {
                        result = child;
                        break;
                    }
                }
                if ( null==result && create) {
                    Map<String, String> attrs = new TreeMap<>();
                    attrs.put("id", idAttr);
                    result = new ConfigItem(itemName, null, attrs);
                    items.add(result);
                }
            }
    	} else {
            //abc
            for(ConfigItem item:items) {
                if ( item.getName().equals(nameWithAttrs)) {
                    result = item;
                    break;
                }
            }
            if ( null==result && create) {
                result = new ConfigItem(nameWithAttrs, null, null);
                items.add(result);
            }
    	}
        return result;
    }

    private static List<ConfigItem> getItemsByName(List<ConfigItem> items, String name){
        if ( items==null || items.isEmpty() ) {
            return Collections.emptyList();
        }
        List<ConfigItem> result = new ArrayList<>();
        for(ConfigItem item:items) {
            if ( item.getName().equals(name)) {
                result.add(item);
            }
        }
        return result;
    }

	private static void merge(ConfigItem item, ConfigItem item2) {
		if ( !StringUtil.isEmpty(item2.value) ) {
			item.value = item2.value;
		}
		if ( null!=item2.attrs ) { //合并属性
			Map<String, String> attrs = new HashMap<>();
			if ( null!=item.attrs) {
				attrs = item.attrs;
			}
			for(String attrName:item2.attrs.keySet()) {
			    String attrVal2 = item2.attrs.get(attrName);
	            attrs.put(attrName, attrVal2);
			}
			item.attrs = attrs;
		}
		if ( null!=item2.children ) { //基于attr=id/name合并子节点
			List<ConfigItem> childrenToMerge = new ArrayList<>();
			List<ConfigItem> childrenAfterMerge = new ArrayList<>();
            if ( null!=item.children) {
                childrenToMerge.addAll(item.children);
            }
			for(ConfigItem child2:item2.children) {
			    String id2 = child2.getId();
			    ConfigItem child = null;
			    for(ConfigItem child0:childrenToMerge) {
			        if ( !child2.getName().equals(child0.getName()) ) {
			            continue;
			        }
			        String id0 = child0.getId();
			        if ( StringUtil.equals(id0, id2) ) {
			            child = child0;
			            childrenToMerge.remove(child0);
			            break;
			        }
			    }
			    if ( child!=null ) {
			        merge(child, child2);
				} else {
					child = child2;
				}
			    childrenAfterMerge.add(child);
			}
			childrenAfterMerge.addAll(childrenToMerge);
            item.children = childrenAfterMerge;
		}
	}


}
