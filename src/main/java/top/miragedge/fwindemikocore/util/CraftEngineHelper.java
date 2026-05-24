package top.miragedge.fwindemikocore.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * CraftEngine 插件的静态工具类。
 * <p>
 * 提供统一的自定义物品查询、ID格式验证、物品存在性检查等功能。
 * 所有方法均为静态，使用前必须先调用 {@link #init(JavaPlugin)} 初始化。
 * <p>
 * 通过反射调用 CraftEngine API，兼容不同版本（0.0.60 返回 CustomItem，新版返回 BukkitItemDefinition）。
 */
public final class CraftEngineHelper {

    private CraftEngineHelper() {
    }

    private static JavaPlugin plugin;
    private static boolean craftEngineAvailable = false;

    private static Class<?> craftEngineItemsClass;
    private static Method byIdKeyMethod;
    private static Method byIdStringMethod;
    private static Method byItemStackMethod;
    private static Method getCustomItemIdMethod;
    private static Method idMethod;
    private static Method keyOfMethod;
    private static Class<?> keyClass;
    private static boolean apiInitialized = false;

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        checkAvailability();
    }

    public static void checkAvailability() {
        craftEngineAvailable = plugin.getServer().getPluginManager().getPlugin("CraftEngine") != null;
        if (craftEngineAvailable && !apiInitialized) {
            initReflection();
        }
    }

    public static boolean isAvailable() {
        return craftEngineAvailable;
    }

    private static void initReflection() {
        try {
            craftEngineItemsClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            keyClass = Class.forName("net.momirealms.craftengine.core.util.Key");

            try {
                byIdStringMethod = craftEngineItemsClass.getMethod("byId", String.class);
            } catch (NoSuchMethodException ignored) {
            }

            try {
                byIdKeyMethod = craftEngineItemsClass.getMethod("byId", keyClass);
            } catch (NoSuchMethodException ignored) {
            }

            try {
                byItemStackMethod = craftEngineItemsClass.getMethod("byItemStack", ItemStack.class);
            } catch (NoSuchMethodException ignored) {
            }

            try {
                getCustomItemIdMethod = craftEngineItemsClass.getMethod("getCustomItemId", ItemStack.class);
            } catch (NoSuchMethodException ignored) {
            }

            try {
                keyOfMethod = keyClass.getMethod("of", String.class);
            } catch (NoSuchMethodException ignored) {
            }

            try {
                idMethod = craftEngineItemsClass.getMethod("id");
            } catch (NoSuchMethodException ignored) {
            }

            apiInitialized = true;
            plugin.getLogger().info("[CraftEngine] API 反射初始化成功");
        } catch (Throwable e) {
            plugin.getLogger().warning("[CraftEngine] API 反射初始化失败，物品检测将不可用: " + e.getMessage());
            apiInitialized = false;
        }
    }

    /**
     * 通过物品ID查询 CraftEngine 中是否存在该自定义物品。
     *
     * @param itemId 物品命名空间ID（格式: namespace:id）
     * @return true 如果物品在 CraftEngine 中已注册；false 如果未注册或 CraftEngine 不可用；null 如果无法验证
     */
    public static @Nullable Boolean itemExists(@NotNull String itemId) {
        if (!craftEngineAvailable || !apiInitialized) {
            return null;
        }
        try {
            Object result = null;

            if (byIdStringMethod != null) {
                result = byIdStringMethod.invoke(null, itemId);
            } else if (byIdKeyMethod != null && keyOfMethod != null) {
                Object key = keyOfMethod.invoke(null, itemId);
                result = byIdKeyMethod.invoke(null, key);
            }

            return result != null;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 通过物品堆栈获取 CraftEngine 自定义物品的ID字符串。
     *
     * @param itemStack Bukkit 物品堆栈
     * @return 物品ID字符串（如 "miragedge_items:spicy_blade"），如果不是自定义物品则返回 null
     */
    public static @Nullable String getCustomItemId(@NotNull ItemStack itemStack) {
        if (!craftEngineAvailable || !apiInitialized || itemStack == null) {
            return null;
        }
        try {
            if (getCustomItemIdMethod != null) {
                Object key = getCustomItemIdMethod.invoke(null, itemStack);
                if (key != null) {
                    return key.toString();
                }
                return null;
            }

            if (byItemStackMethod != null) {
                Object definition = byItemStackMethod.invoke(null, itemStack);
                if (definition != null && idMethod != null) {
                    Object key = idMethod.invoke(definition);
                    if (key != null) {
                        return key.toString();
                    }
                }
            }

            return null;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 判断物品堆栈是否是指定的 CraftEngine 自定义物品。
     *
     * @param itemStack  物品堆栈
     * @param expectedId 期望的物品命名空间ID
     * @return true 如果匹配
     */
    public static boolean isCustomItem(@NotNull ItemStack itemStack, @NotNull String expectedId) {
        String itemId = getCustomItemId(itemStack);
        if (itemId == null) {
            return false;
        }
        return expectedId.equals(itemId);
    }

    public static boolean validateItemIdFormat(@NotNull String itemId) {
        return itemId.matches("^[a-z0-9_]+:[a-z0-9_]+$");
    }

    public static @NotNull String sanitizeItemId(@NotNull String itemId, @NotNull String defaultId) {
        if (validateItemIdFormat(itemId)) {
            return itemId;
        }
        plugin.getLogger().severe("[CraftEngine] 物品ID格式错误: " + itemId + "，使用默认值: " + defaultId);
        return defaultId;
    }
}
