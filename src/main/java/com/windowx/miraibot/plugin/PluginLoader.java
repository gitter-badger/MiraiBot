package com.windowx.miraibot.plugin;

import com.windowx.miraibot.PluginMain;
import com.windowx.miraibot.event.EventHandler;
import com.windowx.miraibot.event.ListenerHost;
import com.windowx.miraibot.utils.ConfigUtil;
import com.windowx.miraibot.utils.LogUtil;
import net.mamoe.mirai.event.Event;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

import static com.windowx.miraibot.PluginMain.completes;
import static com.windowx.miraibot.PluginMain.language;

public class PluginLoader {
    public ArrayList<Plugin> plugins;
    public final HashMap<String, Class<?>> classes = new HashMap<>();
    public final HashMap<Plugin, PluginClassLoader> loaders = new LinkedHashMap<>();
    public final HashMap<Plugin, ArrayList<ListenerHost>> listeners = new HashMap<>();

    public void broadcastEvent(Event event) {
        for(Plugin plugin : listeners.keySet()) {
            if (!plugin.isEnabled()) continue;
            ArrayList<ListenerHost> listeners = this.listeners.get(plugin);
            for(ListenerHost listener : listeners) {
                Field[] fields = listener.getClass().getFields();
                for(Field field : fields) {
                    field.setAccessible(true);
                }
                Method[] methods = listener.getClass().getMethods();
                for(Method method : methods) {
                    if (!method.isAnnotationPresent(EventHandler.class)) {
                        continue;
                    }
                    Class<?>[] type = method.getParameterTypes();
                    if (type.length < 1) {
                        continue;
                    }
                    Class<?> ec = event.getClass();
                    if (type[0] != event.getClass() && !ec.isAssignableFrom(type[0]) && !type[0].isAssignableFrom(ec)) {
                        continue;
                    }
                    method.setAccessible(true);
                    try {
                        Object instance = listener.getClass().getDeclaredConstructor().newInstance();
                        method.invoke(instance, event);
                    } catch (Exception e) {
                        LogUtil.error(ConfigUtil.getLanguage("event.error"),
                                plugin.getName(),
                                className(event.getClass().getName()),
                                e.toString()
                        );
                        System.out.println();
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    /**
     * 注册事件监听器
     * @param plugin 插件
     * @param listener 监听器
     */
    public void registerListener(Plugin plugin, ListenerHost listener) {
        ArrayList<ListenerHost> list = listeners.get(plugin);
        if (list == null) {
            list = new ArrayList<>();
        }
        if (!list.contains(listener)) {
            list.add(listener);
        }
        listeners.put(plugin, list);
    }

    /**
     * 注册多个事件监听器
     * @param plugin 插件
     * @param listeners 监听器数组
     */
    public void registerListeners(Plugin plugin, ListenerHost[] listeners) {
        ArrayList<ListenerHost> list = this.listeners.get(plugin);
        if (list == null) {
            list = new ArrayList<>();
        }
        for(ListenerHost l : listeners) {
            if (list.contains(l)) {
                continue;
            }
            list.add(l);
        }
        this.listeners.put(plugin, list);
    }

    private String className(String name) {
        String[] split = name.split("\\.");
        return split[split.length - 1];
    }
    /**
     * 通过插件名获取插件对象
     *
     * @param name 插件名
     * @return 插件
     */
    @Nullable
    public Plugin getPlugin(String name) {
        for (Plugin p : plugins) {
            if (p.getName().equals(name)) return p;
        }
        return null;
    }

    /**
     * 通过插件名获取插件是否存在
     *
     * @param name 插件名
     * @return 是否存在
     */
    public boolean hasPlugin(String name) {
        for (Plugin p : plugins) {
            if (p.getName().equals(name)) return true;
        }
        return false;
    }

    /**
     * 卸载某个插件
     *
     * @param name 插件名
     */
    public void unloadPlugin(String name) {
        Plugin plugin = null;
        for (Plugin value : plugins) {
            if (value.getName().equals(name)) {
                plugin = value;
                break;
            }
        }
        if (plugin != null) {
            LogUtil.log(language("unloading.plugin"), plugin.getName());
            try {
                plugin.onDisable();
            } catch (Exception e) {
                e.printStackTrace();
            }
            plugin.setEnabled(false);
            removeClass(plugin.getName());
            loaders.remove(plugin);
            System.gc();
            LogUtil.log(language("unloaded.plugin"), plugin.getName());
        } else {
            LogUtil.error(language("plugin.not.exits"), name);
        }
    }

    private Plugin init(Properties plugin, PluginClassLoader u) throws Exception {
        Class<?> clazz = u.loadClass(plugin.getProperty("main"));
        Plugin p = (Plugin) clazz.getDeclaredConstructor().newInstance();
        p.setName(plugin.getProperty("name", "Untitled"));
        p.setOwner(plugin.getProperty("owner", "Unnamed"));
        p.setClassName(plugin.getProperty("main"));
        p.setVersion(plugin.getProperty("version", "1.0.0"));
        p.setDescription(plugin.getProperty("description", "A Plugin For MiraiBot."));
        p.setCommands(plugin.getProperty("commands", "").split(","));
        p.setPluginClassLoader(u);
        p.setPlugin(plugin);
        p.setPluginLoader(PluginMain.loader);
        Properties config = new Properties();
        File file = new File("plugins/" + plugin.getProperty("name") + "/config.ini");
        if (file.exists()) config.load(new FileReader(file));
        p.setConfig(config);
        loaders.put(p, u);
        return p;
    }

    Class<?> getClassByName(String name) {
        Class<?> cachedClass = classes.get(name);

        if (cachedClass != null) {
            return cachedClass;
        } else {
            for (Plugin current : loaders.keySet()) {
                PluginClassLoader loader = loaders.get(current);

                try {
                    cachedClass = loader.findClass(name, false);
                } catch (ClassNotFoundException ignored) {

                }
                if (cachedClass != null) {
                    return cachedClass;
                }
            }
        }
        return null;
    }

    void setClass(final String name, final Class<?> clazz) {
        if (!classes.containsKey(name)) {
            classes.put(name, clazz);
        }
    }

    void removeClass(final String name) {
        classes.remove(name);
    }

    /**
     * 加载某个插件
     *
     * @param file 文件
     * @param name 名称
     * @throws Exception 报错
     */
    public void loadPlugin(File file, String name) throws Exception {
        if (file.exists()) {
            Plugin plugin = null;
            PluginClassLoader u = new PluginClassLoader(new URL[]{file.toURI().toURL()}, getClass().getClassLoader(), this);
            InputStream is = u.getResourceAsStream("plugin.ini");
            if (is != null) {
                LogUtil.log(language("loading.plugin"), name);
                Properties prop = new Properties();
                prop.load(is);
                if (prop.containsKey("depend")) {
                    String[] split = prop.getProperty("depend").split(",");
                    for (String s : split) {
                        if (hasPlugin(s)) {
                            continue;
                        }
                        LogUtil.error(language("depend.not.exits"), s);
                        return;
                    }
                }
                plugin = init(prop, u);
            } else {
                LogUtil.error(language("failed.load.plugin"), file.getName(), "\"plugin.ini\" not found");
            }
            if (plugin != null) {
                plugin.setFile(file);
                Plugin p = getPlugin(plugin.getName());
                if (p != null) {
                    if (p.isEnabled()) {
                        LogUtil.warn(language("plugin.already.loaded"), plugin.getName());
                        plugins.remove(p);
                        return;
                    }
                }
                plugin.setEnabled(true);
                plugins.add(plugin);
                try {
                    plugin.onEnable();
                    completes.addAll(List.of(plugin.getCommands()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                LogUtil.log(language("loaded.plugin"), plugin.getName());
            } else {
                LogUtil.error(language("failed.load.plugin"), file.getName(), "unknown error");
            }
        } else {
            LogUtil.error(language("plugin.file.not.exits")
                    , name
            );
        }
    }

    /**
     * 初始化插件，将所有 plugins 文件夹内的 .jar 文件加载到插件列表
     */
    public void initPlugins() {
        File pluginsDir = new File("plugins");
        plugins = new ArrayList<>();
        try {
            File[] pluginsFile = pluginsDir.listFiles();
            if (pluginsFile == null) {
                return;
            }
            ArrayList<File> after = new ArrayList<>();
            for (File f : pluginsFile) {
                if (!f.getName().endsWith(".jar")) continue;
                Plugin plugin = null;
                PluginClassLoader u = new PluginClassLoader(new URL[]{f.toURI().toURL()}, getClass().getClassLoader(), this);
                InputStream is = u.getResourceAsStream("plugin.ini");
                if (is == null) {
                    LogUtil.error(language("failed.load.plugin"), f.getName(), "\"plugin.ini\" not found");
                    continue;
                }
                try {
                    Properties prop = new Properties();
                    prop.load(is);
                    if (prop.containsKey("depend")) {
                        String[] split = prop.getProperty("depend").split(",");
                        boolean con = false;
                        for (String s : split) {
                            if (getPlugin(s) == null) {
                                after.add(f);
                                con = true;
                                break;
                            }
                        }
                        if (con) {
                            continue;
                        }
                    }
                    plugin = init(prop, u);
                } catch (Exception e) {
                    System.out.println();
                    e.printStackTrace();
                }
                if (plugin != null) {
                    plugin.setFile(f);
                    plugin.setEnabled(true);
                    plugins.add(plugin);
                } else {
                    LogUtil.error(language("failed.load.plugin"), f.getName(), "unknown error");
                }
            }
            if (after.size() < 1) {
                return;
            }
            for (File f : after) {
                Plugin plugin = null;
                PluginClassLoader u = new PluginClassLoader(new URL[]{f.toURI().toURL()}, getClass().getClassLoader(), this);
                InputStream is = u.getResourceAsStream("plugin.ini");
                assert is != null;

                try {
                    Properties prop = new Properties();
                    prop.load(is);
                    if (prop.containsKey("depend")) {
                        String[] split = prop.getProperty("depend").split(",");
                        for (String s : split) {
                            if (hasPlugin(s)) {
                                continue;
                            }
                            LogUtil.error(language("depend.not.exits"), s);
                            return;
                        }
                    }
                    plugin = init(prop, u);
                } catch (Exception e) {
                    System.out.println();
                    e.printStackTrace();
                }

                if (plugin != null) {
                    plugin.setFile(f);
                    plugin.setEnabled(true);
                    plugins.add(plugin);
                } else {
                    LogUtil.error(language("failed.load.plugin"), f.getName(), "unknown error");
                }
            }
        } catch (Exception e) {
            LogUtil.error(language("unknown.error"));
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
