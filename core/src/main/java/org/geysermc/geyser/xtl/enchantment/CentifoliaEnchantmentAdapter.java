/*
 * Copyright (c) 2026 Xintinglei
 */
package org.geysermc.geyser.xtl.enchantment;

import net.kyori.adventure.key.Key;
import org.geysermc.geyser.translator.item.BedrockItemBuilder;
import org.geysermc.geyser.text.ChatColor;
import org.geysermc.geyser.text.MinecraftLocale;

import java.util.Map;

/**
 * Bedrock has no custom-enchantment registry. Centifolia books are still vanilla enchanted books
 * on the Java wire, so present their server-authoritative enchantment as stable, readable lore.
 */
public final class CentifoliaEnchantmentAdapter {
    private static final Map<String, Definition> ENCHANTMENTS = Map.ofEntries(
        Map.entry("centifolia:ember", new Definition("余烬", "烈焰虽熄，余火未尽。")),
        Map.entry("centifolia:gillification", new Definition("鳃化", "适应深水，也必须重新适应陆地。")),
        Map.entry("centifolia:magnet", new Definition("磁铁", "将掉落物拾取范围扩大至原版的三倍。")),
        Map.entry("centifolia:focus", new Definition("专注", "延长拉弓时间，以弓的寿命换取极高的单发伤害。")),
        Map.entry("centifolia:dawn", new Definition("§d破晓", "二段蓄力十秒，释放贯穿天际的金色箭矢。")),
        Map.entry("centifolia:critical", new Definition("会心", "横剑伺机，招架并回击近身之敌。")),
        Map.entry("centifolia:echo", new Definition("回响", "令古城沉寂的音波，再度沿剑锋回响。")),
        Map.entry("centifolia:rage", new Definition("狂暴", "连续跳劈，使战斧愈战愈快。")),
        Map.entry("centifolia:rapid_fire", new Definition("连射", "一次装填，持续供弹。")),
        Map.entry("centifolia:heavy_armor", new Definition("重装", "四件齐备时，以机动性换取额外减伤。")),
        Map.entry("centifolia:lava_walker", new Definition("熔岩行者", "在熔岩上留下短暂的岩浆块。")),
        Map.entry("centifolia:honesty", new Definition("诚实", "河神会认可诚实的铁斧。")),
        Map.entry("centifolia:propulsion", new Definition("动力推进", "跳跃后再次疾跑，向指定方向喷气冲刺。")),
        Map.entry("centifolia:sloth", new Definition("§c怠惰诅咒", "使武器攻击和工具挖掘都变得迟缓。")),
        Map.entry("centifolia:greed", new Definition("§c贪婪", "以耐久为代价，随机改变挖掘或击杀掉落。")),
        Map.entry("centifolia:pride", new Definition("§c傲慢诅咒", "集齐带纹饰的四件护甲且满血时获得体面。")),
        Map.entry("centifolia:lust", new Definition("§c色孽诅咒", "携带此诅咒的护甲无法被装备。")),
        Map.entry("centifolia:envy", new Definition("§c嫉妒诅咒", "死亡后，从杀死你的存在身上带回一件东西。")),
        Map.entry("centifolia:counterfeit", new Definition("赝品", "这是永远无法修复或修改的赝品。"))
    );

    private CentifoliaEnchantmentAdapter() {
    }

    public static boolean translate(Key id, int level, BedrockItemBuilder builder, String locale) {
        Definition definition = ENCHANTMENTS.get(id.asString());
        if (definition == null) {
            return false;
        }
        String levelText = MinecraftLocale.getLocaleString("enchantment.level." + level, locale);
        // addFirst makes the final ordering name, then description.
        builder.getOrCreateLore().addFirst(ChatColor.RESET + ChatColor.GRAY + definition.description());
        builder.getOrCreateLore().addFirst(ChatColor.RESET + ChatColor.LIGHT_PURPLE + definition.name() + " " + levelText);
        builder.addEnchantmentGlint();
        return true;
    }

    private record Definition(String name, String description) {
    }
}
