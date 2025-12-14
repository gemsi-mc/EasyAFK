package com.gemsi.easyafk;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;

import java.util.HashSet;
import java.util.Set;

public class DamageManager {
    private static final Set<ResourceKey<DamageType>> combatDamageTypes = new HashSet<>();

    static {
        combatDamageTypes.add(DamageTypes.PLAYER_ATTACK);
        combatDamageTypes.add(DamageTypes.MOB_ATTACK);
    }

    public static boolean isCombatDamage(ResourceKey<DamageType> damageTypeKey) {
        return combatDamageTypes.contains(damageTypeKey);
    }

}