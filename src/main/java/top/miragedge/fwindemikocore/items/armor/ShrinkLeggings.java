package top.miragedge.fwindemikocore.items.armor;

import top.miragedge.fwindemikocore.api.ItemModule;
import top.miragedge.fwindemikocore.modules.effects.EntityEffects;
import top.miragedge.fwindemikocore.modules.scale.PlayerScaleModule;
import top.miragedge.fwindemikocore.util.ConfigHelper;
import top.miragedge.fwindemikocore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 灵影护腿 - 盔甲类物品模块。
 * <p>
 * 功能：
 * <ul>
 *   <li><b>体型压缩</b>：穿戴时玩家体型缩小至 50%（影响碰撞箱与客户端显示）</li>
 *   <li><b>灵影步</b>：穿戴时玩家移动速度提升 100%</li>
 * </ul>
 * <p>
 * 效果在穿上时即时生效，脱下时即时移除。
 * <p>
 * <b>配置文件路径：</b> items/armor/shrink-leggings.yml
 */
public class ShrinkLeggings extends ItemModule {

    /** 缩放比例（0.5 = 一半大小） */
    private float scaleMultiplier;
    /** 移速增益比例（1.0 = 增速 100%） */
    private double speedBoost;
    /** 检查周期（tick） */
    private int checkInterval;

    /** 属性修饰符的命名空间键 */
    private final NamespacedKey scaleKey;
    private final NamespacedKey speedKey;

    /** 当前已生效的玩家集合 */
    private final Set<UUID> activePlayers = new HashSet<>();

    /** 定时检查任务 */
    private BukkitTask checkTask;

    /** 模块独立配置 */
    private YamlConfiguration itemConfig;

    /**
     * 构造灵影护腿模块。
     *
     * @param plugin 插件主实例
     */
    public ShrinkLeggings(JavaPlugin plugin) {
        super(plugin, "灵影护腿", "items/armor/shrink-leggings.yml", "miragedge_items:shrink_leggings");
        this.scaleKey = new NamespacedKey(plugin, "shrink_leggings_scale");
        this.speedKey = new NamespacedKey(plugin, "shrink_leggings_speed");
    }

    @Override
    public void loadConfig() {
        this.itemConfig = ConfigHelper.loadItemConfig(plugin, configFilePath);
        ConfigurationSection config = itemConfig;

        this.customItemId = ConfigHelper.getItemId(config, "item-id", defaultItemId, logger);
        this.scaleMultiplier = (float) ConfigHelper.getClampedDouble(config, "scale-multiplier", 0.5, 0.1, 10.0);
        this.speedBoost = ConfigHelper.getClampedDouble(config, "speed-boost", 1.0, 0.0, 10.0);
        this.checkInterval = ConfigHelper.getPositiveInt(config, "check-interval-ticks", 10);
    }

    /**
     * 注册事件监听器并启动装备状态定时检查任务。
     */
    @Override
    public void register() {
        super.register();
        if (enabled) {
            startCheckTask();
        }
    }

    /**
     * 注销所有监听器并停止定时检查任务。
     */
    @Override
    public void unregister() {
        stopCheckTask();
        for (UUID uuid : new HashSet<>(activePlayers)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                removeEffects(player);
            }
        }
        activePlayers.clear();
        super.unregister();
    }

    /** 启动定时检查任务，周期性扫描在线玩家的装备状态 */
    private void startCheckTask() {
        if (checkTask != null && !checkTask.isCancelled()) {
            return;
        }
        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                scanAllPlayers();
            }
        }.runTaskTimer(plugin, 20L, checkInterval);
    }

    /** 停止定时检查任务 */
    private void stopCheckTask() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    /** 扫描所有在线玩家，检查是否穿戴了本护腿 */
    private void scanAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean isWearing = isWearingArmor(player);
            boolean isActive = activePlayers.contains(player.getUniqueId());

            if (isWearing && !isActive) {
                applyEffects(player);
            } else if (!isWearing && isActive) {
                removeEffects(player);
            }
        }
    }

    /** 检查玩家是否穿戴了灵影护腿 */
    private boolean isWearingArmor(Player player) {
        ItemStack leggings = player.getInventory().getLeggings();
        if (leggings == null || leggings.getType().isAir()) {
            return false;
        }
        return isHoldingValidTool(leggings);
    }

    /** 对玩家应用灵影护腿效果（缩小 + 增速） */
    private void applyEffects(Player player) {
        activePlayers.add(player.getUniqueId());

        PlayerScaleModule scaleModule = ((top.miragedge.fwindemikocore.FwindEmikoCore) plugin).getScaleModule();
        if (scaleModule != null) {
            scaleModule.applyPersistentScale(player, scaleMultiplier, scaleKey);
        } else {
            EntityEffects.applyPersistentAttributeModifier(
                player, Attribute.SCALE, scaleKey,
                scaleMultiplier - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1
            );
        }

        EntityEffects.applyPersistentAttributeModifier(
            player, Attribute.MOVEMENT_SPEED, speedKey,
            speedBoost, AttributeModifier.Operation.MULTIPLY_SCALAR_1
        );

        EntityEffects.playSound(player, "entity.vex.charge", 0.5F, 1.4F);
        Msg.actionBar(player, "<aqua>灵影步激活：体型压缩 <yellow>" + (int)(scaleMultiplier * 100) +
            "% <aqua>· 移速提升 <yellow>" + (int)(speedBoost * 100) + "%");
    }

    /** 移除玩家的灵影护腿效果 */
    private void removeEffects(Player player) {
        activePlayers.remove(player.getUniqueId());

        PlayerScaleModule scaleModule = ((top.miragedge.fwindemikocore.FwindEmikoCore) plugin).getScaleModule();
        if (scaleModule != null) {
            scaleModule.removePersistentScale(player, scaleKey);
        } else {
            EntityEffects.removePersistentAttributeModifier(player, Attribute.SCALE, scaleKey);
        }

        EntityEffects.removePersistentAttributeModifier(player, Attribute.MOVEMENT_SPEED, speedKey);

        EntityEffects.playSound(player, "entity.vex.death", 0.4F, 1.0F);
        Msg.actionBar(player, "<green>灵影步消散，体型恢复正常");
    }

    /** 新玩家加入时，如果已穿戴则应用效果 */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (isWearingArmor(player)) {
            applyEffects(player);
        }
    }

    /** 玩家退出时清理效果 */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (activePlayers.contains(player.getUniqueId())) {
            removeEffects(player);
        }
    }
}
