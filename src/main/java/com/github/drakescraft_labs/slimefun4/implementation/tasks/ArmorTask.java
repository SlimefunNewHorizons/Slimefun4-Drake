package com.github.drakescraft_labs.slimefun4.implementation.tasks;

import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.github.drakescraft_labs.slimefun4.api.items.HashedArmorpiece;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItemStack;
import com.github.drakescraft_labs.slimefun4.api.player.PlayerProfile;
import com.github.drakescraft_labs.slimefun4.core.attributes.ProtectionType;
import com.github.drakescraft_labs.slimefun4.core.attributes.Radioactive;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.implementation.items.armor.SlimefunArmorPiece;
import com.github.drakescraft_labs.slimefun4.implementation.items.electric.gadgets.SolarHelmet;
import com.github.drakescraft_labs.slimefun4.utils.compatibility.VersionedPotionEffectType;
import com.github.drakescraft_labs.slimefun4.utils.itemstack.ItemStackWrapper;

/**
 * Handles {@link SlimefunArmorPiece} potion effects and lethal Radiation contact.
 * Includes Geiger Counter audio feedback, immediate lethal damage, and custom death messages.
 */
public class ArmorTask implements Runnable, Listener {

    /** Margen antes de que expire un efecto de armadura para renovarlo, en ticks. */
    private static final int MARGEN_REFRESCO_TICKS = 60;

    private final Set<PotionEffect> radiationEffects;
    private final boolean radioactiveFire;
    private final Random random = new Random();

    public static final String RAD_META_KEY = "sf_rad_item_name";

    public ArmorTask(boolean radioactiveFire) {
        this.radioactiveFire = radioactiveFire;

        Set<PotionEffect> effects = new HashSet<>();
        effects.add(new PotionEffect(PotionEffectType.WITHER, 400, 3));
        effects.add(new PotionEffect(PotionEffectType.BLINDNESS, 400, 3));
        effects.add(new PotionEffect(VersionedPotionEffectType.NAUSEA, 400, 3));
        effects.add(new PotionEffect(PotionEffectType.WEAKNESS, 400, 2));
        effects.add(new PotionEffect(VersionedPotionEffectType.SLOWNESS, 400, 2));
        effects.add(new PotionEffect(VersionedPotionEffectType.MINING_FATIGUE, 400, 2));
        effects.add(new PotionEffect(PotionEffectType.HUNGER, 400, 2));
        radiationEffects = Collections.unmodifiableSet(effects);

        // Registrar listener de eventos de muerte por radiación
        Bukkit.getPluginManager().registerEvents(this, Slimefun.instance());
    }

    @Nonnull
    public Set<PotionEffect> getRadiationEffects() {
        return radiationEffects;
    }

    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isValid() || p.isDead()) {
                continue;
            }

            PlayerProfile.get(p, profile -> {
                ItemStack[] armor = p.getInventory().getArmorContents();
                HashedArmorpiece[] cachedArmor = profile.getArmor();

                handleSlimefunArmor(p, armor, cachedArmor);

                if (hasSunlight(p)) {
                    checkForSolarHelmet(p);
                }

                checkForRadiation(p, profile);
            });
        }
    }

    @ParametersAreNonnullByDefault
    private void handleSlimefunArmor(Player p, ItemStack[] armor, HashedArmorpiece[] cachedArmor) {
        for (int slot = 0; slot < 4; slot++) {
            ItemStack item = armor[slot];
            HashedArmorpiece armorpiece = cachedArmor[slot];

            if (armorpiece.hasDiverged(item)) {
                SlimefunItem sfItem = SlimefunItem.getByItem(item);

                if (!(sfItem instanceof SlimefunArmorPiece)) {
                    sfItem = null;
                }

                armorpiece.update(item, sfItem);
            }

            if (item != null && armorpiece.getItem().isPresent()) {
                Slimefun.runSync(() -> {
                    SlimefunArmorPiece slimefunArmor = armorpiece.getItem().get();

                    if (slimefunArmor.canUse(p, true)) {
                        for (PotionEffect effect : slimefunArmor.getPotionEffects()) {
                            aplicarSiHaceFalta(p, effect);
                        }
                    }
                });
            }
        }
    }

    /**
     * Refresca un efecto de armadura solo cuando hace falta.
     *
     * POR QUE NO SE QUITA Y SE VUELVE A PONER
     *
     * Antes se hacia `removePotionEffect` seguido de `addPotionEffect` en cada pasada. Con la
     * mayoria de efectos no se nota, pero con AUMENTO DE VIDA si: quitarlo baja la vida maxima al
     * instante, y si el jugador tenia llenos esos corazones de mas, la diferencia se la come como
     * daño. Con la tarea de armadura corriendo cada pocos segundos, eso son mordiscos constantes
     * a quien lleve unas botas que den vida extra. Lo reporto un jugador y era exactamente esto.
     *
     * Refrescar solo cuando el efecto falta o esta a punto de agotarse tiene el mismo resultado
     * visible -- el efecto no se corta mientras lleves la armadura -- sin el bajon de vida.
     *
     * El margen es de tres segundos: la tarea pasa mucho mas a menudo, asi que da tiempo de sobra
     * a renovarlo antes de que expire de verdad.
     */
    private void aplicarSiHaceFalta(@Nonnull Player p, @Nonnull PotionEffect effect) {
        PotionEffect actual = p.getPotionEffect(effect.getType());

        if (actual != null
                && actual.getAmplifier() >= effect.getAmplifier()
                && actual.getDuration() > MARGEN_REFRESCO_TICKS) {
            return;
        }

        p.addPotionEffect(effect);
    }

    private void checkForSolarHelmet(@Nonnull Player p) {
        ItemStack helmet = p.getInventory().getHelmet();
        SlimefunItem item = SlimefunItem.getByItem(helmet);

        if (item instanceof SolarHelmet solarHelmet && item.canUse(p, true)) {
            solarHelmet.rechargeItems(p);
        }
    }

    private boolean hasSunlight(@Nonnull Player p) {
        World world = p.getWorld();

        if (world.getEnvironment() != Environment.NORMAL) {
            return false;
        }

        return (world.getTime() < 12300 || world.getTime() > 23850) && p.getEyeLocation().getBlock().getLightFromSky() == 15;
    }

    private void checkForRadiation(@Nonnull Player p, @Nonnull PlayerProfile profile) {
        if (!profile.hasFullProtectionAgainst(ProtectionType.RADIATION)) {
            for (ItemStack item : p.getInventory()) {
                if (checkAndApplyRadiation(p, item)) {
                    break;
                }
            }
        }
    }

    private boolean checkAndApplyRadiation(@Nonnull Player p, @Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        Set<SlimefunItem> radioactiveItems = Slimefun.getRegistry().getRadioactiveItems();
        ItemStack itemStack = item;

        if (!(item instanceof SlimefunItemStack) && radioactiveItems.size() > 1) {
            itemStack = ItemStackWrapper.wrap(item);
        }

        for (SlimefunItem radioactiveItem : radioactiveItems) {
            if (radioactiveItem.isItem(itemStack) && !radioactiveItem.isDisabledIn(p.getWorld())) {
                String itemName = radioactiveItem.getItemName();
                p.setMetadata(RAD_META_KEY, new FixedMetadataValue(Slimefun.instance(), itemName));

                Slimefun.getLocalization().sendMessage(p, "messages.radiation");

                Slimefun.runSync(() -> {
                    // 1. Efectos sonoros de Contador Geiger (Ticks acelerados)
                    float randomPitch = 1.4f + (random.nextFloat() * 0.6f);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.2f, randomPitch);
                    if (random.nextBoolean()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.8f);
                    }

                    // 2. Partículas de chispa e indicador de daño por radiación
                    p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0.1);
                    p.getWorld().spawnParticle(Particle.ASH, p.getEyeLocation(), 15, 0.5, 0.5, 0.5, 0.05);

                    // 3. Efectos letales de poción
                    p.addPotionEffects(radiationEffects);

                    // 4. Fuego radioactivo directo
                    if (radioactiveFire) {
                        p.setFireTicks(400);
                    }

                    // 5. Daño letal directo (4.0 HP = 2 corazones por tick de chequeo)
                    p.damage(4.0);
                });

                return true;
            }
        }

        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player.hasMetadata(RAD_META_KEY)) {
            String radioactiveItemName = player.getMetadata(RAD_META_KEY).get(0).asString();
            player.removeMetadata(RAD_META_KEY, Slimefun.instance());

            // Mensaje de muerte personalizado por radiación letal
            String customDeathMsg = ChatColor.RED + "☢ " + ChatColor.YELLOW + player.getName()
                    + ChatColor.RED + " was disintegrated by lethal radiation while handling "
                    + ChatColor.GOLD + ChatColor.BOLD + radioactiveItemName;

            event.setDeathMessage(customDeathMsg);
        }
    }
}
