package de.epiceric.shopchest.utils;

import com.google.common.collect.Lists;
import de.epiceric.shopchest.nms.CustomBookMeta;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Map;

public class ItemUtils {

    public static Map<Enchantment, Integer> getEnchantments(ItemStack itemStack) {
        if (itemStack.getItemMeta() instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta esm = (EnchantmentStorageMeta) itemStack.getItemMeta();
            return esm.getStoredEnchants();
        } else {
            return itemStack.getEnchantments();
        }
    }

    public static PotionType getPotionEffect(ItemStack itemStack) {
        if (itemStack.getItemMeta() instanceof PotionMeta) {
            if (Utils.getMajorVersion() < 9) {
                return Potion.fromItemStack(itemStack).getType();
            } else {
                return ((PotionMeta) itemStack.getItemMeta()).getBasePotionData().getType();
            }
        }

        return null;
    }

    public static boolean isExtendedPotion(ItemStack itemStack) {
        if (itemStack.getItemMeta() instanceof PotionMeta) {
            if (Utils.getMajorVersion() >= 9) {
                PotionMeta potionMeta = (PotionMeta) itemStack.getItemMeta();
                return potionMeta.getBasePotionData().isExtended();
            } else {
                Potion potion = Potion.fromItemStack(itemStack);
                return potion.hasExtendedDuration();
            }
        }

        return false;
    }

    public static boolean isMusicDisc(ItemStack itemStack) {
        List<Material> musicDiscMaterials = Lists.newArrayList(
                Material.GOLD_RECORD, Material.GREEN_RECORD, Material.RECORD_3, Material.RECORD_4,
                Material.RECORD_5, Material.RECORD_6, Material.RECORD_7, Material.RECORD_8,
                Material.RECORD_9, Material.RECORD_10, Material.RECORD_11, Material.RECORD_12
        );

        return musicDiscMaterials.contains(itemStack.getType());
    }

    public static CustomBookMeta.Generation getBookGeneration(ItemStack itemStack) {
        if (itemStack.getType() == Material.WRITTEN_BOOK) {
            return CustomBookMeta.getGeneration(itemStack);
        }

        return null;
    }

}
