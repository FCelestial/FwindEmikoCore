package top.miragedge.fwindemikocore.modules.scale;

import top.miragedge.fwindemikocore.modules.effects.EntityEffects;
import top.miragedge.fwindemikocore.modules.packet.PlayerScalePacket;
import top.miragedge.fwindemikocore.util.Msg;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家碰撞箱缩放功能模块。
 * <p>
 * 提供改变玩家尺寸（scale）的能力，同时影响：
 * <ul>
 *   <li><b>服务端碰撞箱</b>：通过 {@link Attribute#SCALE} 属性修饰符真实改变</li>
 *   <li><b>客户端显示</b>：通过 ProtocolLib 广播 ENTITY_METADATA 数据包同步</li>
 * </ul>
 * <p>
 * 此模块是纯功能模块，不绑定任何物品，供其他物品模块调用。
 * <p>
 * <b>使用示例：</b>
 * <pre>
 * PlayerScaleModule scaleModule = plugin.getScaleModule();
 * scaleModule.applyScale(player, 0.5f, 10); // 临时：玩家缩小到50%，持续10秒
 * scaleModule.applyPersistentScale(player, 2.0f, key); // 持续：玩家放大到200%，需手动移除
 * </pre>
 */
public class PlayerScaleModule implements Listener {

    private final JavaPlugin plugin;

    /**
     * 记录每个玩家原始的 scale 基值，用于恢复。
     * Key: 玩家UUID, Value: 原始scale值
     */
    private final Map<UUID, Double> originalScales = new HashMap<>();

    /** 临时缩放使用的修饰符键 */
    private static final String TEMP_SCALE_KEY = "fec_player_scale";

    /**
     * 构造玩家缩放模块。
     *
     * @param plugin 插件主实例
     */
    public PlayerScaleModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 注册事件监听器。
     */
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 注销事件监听器。
     */
    public void unregister() {
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    /**
     * 对指定玩家应用临时缩放效果。
     * <p>
     * 此方法会同时修改服务端属性（影响碰撞箱）和客户端显示。
     * 效果在指定时间后自动恢复。
     *
     * @param player   目标玩家
     * @param scale    缩放比例（1.0 = 正常，0.5 = 一半，2.0 = 两倍）
     * @param duration 持续时间（秒）
     */
    public void applyScale(@org.jetbrains.annotations.NotNull Player player, float scale, int duration) {
        originalScales.putIfAbsent(player.getUniqueId(), player.getAttribute(Attribute.SCALE).getBaseValue());

        double modifierValue = scale - 1.0;
        NamespacedKey key = new NamespacedKey(plugin, TEMP_SCALE_KEY);

        EntityEffects.applyAttributeModifier(player, Attribute.SCALE, key, modifierValue, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        PlayerScalePacket.broadcastScaleUpdate(player, scale);

        if (scale < 1.0f) {
            EntityEffects.playSound(player, "entity.illusioner.mirror_move", 0.6F, 1.2F);
        } else {
            EntityEffects.playSound(player, "entity.illusioner.cast_spell", 0.6F, 0.8F);
        }

        int percent = (int) (scale * 100);
        Msg.actionBar(player, "<aqua>体型变化: <yellow>" + percent + "% <aqua>持续 <yellow>" + duration + "<aqua> 秒");

        new BukkitRunnable() {
            @Override
            public void run() {
                restoreScale(player);
            }
        }.runTaskLater(plugin, duration * 20L);
    }

    /**
     * 恢复指定玩家的原始尺寸（临时缩放专用）。
     *
     * @param player 目标玩家
     */
    public void restoreScale(@org.jetbrains.annotations.NotNull Player player) {
        NamespacedKey key = new NamespacedKey(plugin, TEMP_SCALE_KEY);
        EntityEffects.removeAttributeModifier(player, Attribute.SCALE, key);

        double originalScale = originalScales.getOrDefault(player.getUniqueId(), 1.0);
        PlayerScalePacket.broadcastScaleUpdate(player, (float) originalScale);

        originalScales.remove(player.getUniqueId());

        EntityEffects.playSound(player, "entity.enderman.teleport", 0.4F, 1.0F);
        Msg.actionBar(player, "<green>体型已恢复");
    }

    /**
     * 判断玩家是否正在被缩放效果影响。
     *
     * @param player 目标玩家
     * @return true 如果玩家当前有活跃的缩放修饰符
     */
    public boolean isScaled(@org.jetbrains.annotations.NotNull Player player) {
        return originalScales.containsKey(player.getUniqueId());
    }

    /**
     * 对指定玩家应用<b>持续性</b>缩放效果（用于装备驱动，不会自动恢复）。
     * <p>
     * 此方法会同时修改服务端属性（影响碰撞箱）和客户端显示。
     * 效果需要通过 {@link #removePersistentScale(Player, NamespacedKey)} 手动移除。
     *
     * @param player 目标玩家
     * @param scale  缩放比例（1.0 = 正常，0.5 = 一半，2.0 = 两倍）
     * @param key    修饰符的命名空间键（用于唯一标识，装备模块应各自使用不同key）
     */
    public void applyPersistentScale(@org.jetbrains.annotations.NotNull Player player, float scale, NamespacedKey key) {
        originalScales.putIfAbsent(player.getUniqueId(), player.getAttribute(Attribute.SCALE).getBaseValue());

        double modifierValue = scale - 1.0;
        EntityEffects.applyAttributeModifier(player, Attribute.SCALE, key, modifierValue, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        PlayerScalePacket.broadcastScaleUpdate(player, scale);
    }

    /**
     * 移除指定玩家的持续性缩放效果。
     *
     * @param player 目标玩家
     * @param key    修饰符的命名空间键（必须与 apply 时一致）
     */
    public void removePersistentScale(@org.jetbrains.annotations.NotNull Player player, NamespacedKey key) {
        EntityEffects.removeAttributeModifier(player, Attribute.SCALE, key);

        double originalScale = originalScales.getOrDefault(player.getUniqueId(), 1.0);
        PlayerScalePacket.broadcastScaleUpdate(player, (float) originalScale);

        originalScales.remove(player.getUniqueId());
    }

    /** 新玩家加入时，同步当前所有被缩放玩家的状态给新玩家 */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player newPlayer = event.getPlayer();
        for (Map.Entry<UUID, Double> entry : originalScales.entrySet()) {
            Player scaledPlayer = plugin.getServer().getPlayer(entry.getKey());
            if (scaledPlayer != null && scaledPlayer.isOnline()) {
                double currentScale = scaledPlayer.getAttribute(Attribute.SCALE).getValue();
                PlayerScalePacket.sendScaleUpdate(scaledPlayer, newPlayer, (float) currentScale);
            }
        }
    }

    /** 玩家退出时清理记录 */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (originalScales.containsKey(player.getUniqueId())) {
            NamespacedKey key = new NamespacedKey(plugin, TEMP_SCALE_KEY);
            EntityEffects.removeAttributeModifier(player, Attribute.SCALE, key);
            originalScales.remove(player.getUniqueId());
        }
    }
}
