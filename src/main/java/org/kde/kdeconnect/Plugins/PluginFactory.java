package org.kde.kdeconnect.Plugins;


import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Plugins.BatteryPlugin.BatteryPlugin;
import org.kde.kdeconnect.Plugins.MousePadPlugin.MousePadPlugin;
import org.kde.kdeconnect.Plugins.SftpPlugin.SftpPlugin;
import org.kde.kdeconnect.Plugins.ClibpoardPlugin.ClipboardPlugin;
import org.kde.kdeconnect.Plugins.MprisPlugin.MprisPlugin;
import org.kde.kdeconnect.Plugins.NotificationsPlugin.NotificationsPlugin;
import org.kde.kdeconnect.Plugins.PingPlugin.PingPlugin;
import org.kde.kdeconnect.Plugins.SharePlugin.SharePlugin;
import org.kde.kdeconnect.Plugins.TelephonyPlugin.TelephonyPlugin;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class PluginFactory {

    public static class PluginInfo {

        public PluginInfo(String pluginName, String displayName, String description, Drawable icon,
                          boolean enabledByDefault, boolean hasSettings) {
            this.pluginName = pluginName;
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
            this.enabledByDefault = enabledByDefault;
            this.hasSettings = hasSettings;
        }

        public String getPluginName() {
            return pluginName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public Drawable getIcon() {
            return icon;
        }

        public boolean hasSettings() { return hasSettings; }

        public boolean isEnabledByDefault() {
            return enabledByDefault;
        }

        private final String pluginName;
        private final String displayName;
        private final String description;
        private final Drawable icon;
        private final boolean enabledByDefault;
        private final boolean hasSettings;

    }

    private static final Map<String, Class> availablePlugins = new TreeMap<String, Class>();
    private static final Map<String, PluginInfo> availablePluginsInfo = new TreeMap<String, PluginInfo>();

    static {
        //TODO: Use reflection to find all subclasses of Plugin, instead of adding them manually
        PluginFactory.registerPlugin(TelephonyPlugin.class);
        PluginFactory.registerPlugin(PingPlugin.class);
        PluginFactory.registerPlugin(MprisPlugin.class);
        PluginFactory.registerPlugin(ClipboardPlugin.class);
        PluginFactory.registerPlugin(BatteryPlugin.class);
        PluginFactory.registerPlugin(SftpPlugin.class);
        PluginFactory.registerPlugin(NotificationsPlugin.class);
        PluginFactory.registerPlugin(MousePadPlugin.class);
        PluginFactory.registerPlugin(SharePlugin.class);
    }

    public static PluginInfo getPluginInfo(Context context, String pluginName) {
        PluginInfo info = availablePluginsInfo.get(pluginName); //Is it cached?
        if (info != null) return info;
        try {
            Plugin p = ((Plugin)availablePlugins.get(pluginName).newInstance());
            p.setContext(context, null);
            info = new PluginInfo(pluginName, p.getDisplayName(), p.getDescription(), p.getIcon(),
                    p.isEnabledByDefault(), p.hasSettings());
            availablePluginsInfo.put(pluginName, info); //Cache it
            return info;
        } catch(Exception e) {
            e.printStackTrace();
            Log.e("PluginFactory","getPluginInfo exception");
            return null;
        }
    }

    public static Set<String> getAvailablePlugins() {
        return availablePlugins.keySet();
    }

    public static Plugin instantiatePluginForDevice(Context context, String pluginName, Device device) {
        Class c = availablePlugins.get(pluginName);
        if (c == null) {
            Log.e("PluginFactory", "Plugin not found: "+pluginName);
            return null;
        }

        try {
            Plugin plugin = (Plugin)c.newInstance();
            plugin.setContext(context, device);
            return plugin;
        } catch(Exception e) {
            e.printStackTrace();
            Log.e("PluginFactory", "Could not instantiate plugin: "+pluginName);
            return null;
        }

    }

    public static void registerPlugin(Class<? extends Plugin> pluginClass) {
        try {
            //I hate this but I need to create an instance because abstract static functions can't be declared
            String pluginName = (pluginClass.newInstance()).getPluginName();
            availablePlugins.put(pluginName, pluginClass);
        } catch(Exception e) {
            Log.e("PluginFactory","addPlugin exception");
            e.printStackTrace();
        }
    }

}
