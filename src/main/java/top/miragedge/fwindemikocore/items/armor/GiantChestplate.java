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
 * 巨像胸甲 - 盔甲类物品模块。
 * <p>
 * 功能：
 * <ul>
 *   <li><b>体型膨胀</b>：穿戴时玩家体型放大至 2 倍（影响碰撞箱与客户端显示）</li>
 *   <li><b>重力压迫</b>：穿戴时玩家移动速度降低 50%</li>
 * </ul>
 * <p>
 * 效果在穿上时即时生效，脱下时即时移除。
 * <p>
 * <b>配置文件路径：</b> items/armor/giant-chestplate.yml
 */
public class GiantChestplate extends ItemModule {

    /** 缩放比例（2.0 = 两倍大） */
    private float scaleMultiplier;
    /** 移速减益比例（-0.5 = 减速 50%） */
    private double speedReduction;
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
     * 构造巨像胸甲模块。
     *
     * @param plugin 插件主实例
     */
    public GiantChestplate(JavaPlugin plugin) {
        super(plugin, "巨像胸甲", "items/armor/giant-chestplate.yml", "miragedge_items:giant_chestplate");
        this.scaleKey = new NamespacedKey(plugin, "giant_chestplate_scale");
        this.speedKey = new NamespacedKey(plugin, "giant_chestplate_speed");
    }

    @Override
    public void loadConfig() {
        this.itemConfig = ConfigHelper.loadItemConfig(plugin, configFilePath);
        ConfigurationSection config = itemConfig;

        this.customItemId = ConfigHelper.getItemId(config, "item-id", defaultItemId, logger);
        this.scaleMultiplier = (float) ConfigHelper.getClampedDouble(config, "scale-multiplier", 2.0, 0.1, 10.0);
        this.speedReduction = ConfigHelper.getClampedDouble(config, "speed-reduction", -0.5, -1.0, 0.0);
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

    /** 扫描所有在线玩家，检查是否穿戴了本胸甲 */
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

    /** 检查玩家是否穿戴了巨像胸甲 */
    private boolean isWearingArmor(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate == null || chestplate.getType().isAir()) {
            return false;
        }
        return isHoldingValidTool(chestplate);
    }

    /** 对玩家应用巨像胸甲效果（放大 + 减速） */
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
            speedReduction, AttributeModifier.Operation.MULTIPLY_SCALAR_1
        );

        EntityEffects.playSound(player, "entity.iron_golem.repair", 0.6F, 0.8F);
        Msg.actionBar(player, "<gold>巨像之力降临：体型膨胀 <yellow>" + (int)(scaleMultiplier * 100) +
            "% <gold>· 移速降低 <yellow>" + (int)(Math.abs(speedReduction) * 100) + "%");
    }

    /** 移除玩家的巨像胸甲效果 */
    private void removeEffects(Player player) {
        activePlayers.remove(player.getUniqueId());

        PlayerScaleModule scaleModule = ((top.miragedge.fwindemikocore.FwindEmikoCore) plugin).getScaleModule();
        if (scaleModule != null) {
            scaleModule.removePersistentScale(player, scaleKey);
        } else {
            EntityEffects.removePersistentAttributeModifier(player, Attribute.SCALE, scaleKey);
        }

        EntityEffects.removePersistentAttributeModifier(player, Attribute.MOVEMENT_SPEED, speedKey);

        EntityEffects.playSound(player, "entity.iron_golem.damage", 0.4F, 1.2F);
        Msg.actionBar(player, "<green>巨像之力消散，体型恢复正常");
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
