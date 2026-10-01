package com.jointspaceforce.patchee.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.jointspaceforce.patchee.Config;
import com.jointspaceforce.patchee.core.Toggles;
import com.jointspaceforce.patchee.features.LightOverlayFeature;
import com.jointspaceforce.patchee.features.LightOverlayThreshold;

/**
 * Overrides the F7 light/mob-spawn overlay threshold in
 * {@code codechicken.nei.WorldOverlayRenderer}.
 *
 * <p>
 * NEI's {@code private static byte getSpawnMode(Chunk, int, int, int)} decides
 * the marker with a hardcoded int literal {@code 8}, used twice: block light
 * {@code >= 8} means no marker, and sky light {@code >= 8} picks the colour.
 * There is no config key, no server→client packet and no hook, so the only way
 * to change it is to modify the constant in place.
 *
 * <p>
 * {@link ModifyConstant} with {@code intValue = 8} rewrites <b>both</b> sites to
 * the value returned here, which is exactly the intended shape (both
 * comparisons must move together). The classic Mixin injector is used rather
 * than a MixinExtras one because the UniMixins build this project compiles
 * against ships {@code org.spongepowered.asm.mixin.injection.ModifyConstant}
 * but not MixinExtras' {@code ModifyConstant}.
 *
 * <p>
 * The replacement is computed at runtime from the client's own world dimension
 * plus the live config snapshot. The overlay only renders the local player's
 * world, so the client's current dimension is the correct one. When the master
 * switch or the feature switch is off,
 * {@link LightOverlayThreshold#constant} returns NEI's own {@code 8}, so the
 * behaviour is byte-identical to stock.
 *
 * <p>
 * The mixin config lists this class under {@code client} and sets
 * {@code required: false}: it is never applied on a dedicated server, and a
 * client without NEI logs a skip instead of crashing.
 */
@Mixin(targets = "codechicken.nei.WorldOverlayRenderer", remap = false)
public class WorldOverlayRendererMixin {

    /** The literal NEI hardcodes, at both comparison sites. */
    private static final int NEI_LIGHT_THRESHOLD = 8;

    @ModifyConstant(
        method = "getSpawnMode(Lnet/minecraft/world/chunk/Chunk;III)B",
        constant = @Constant(intValue = NEI_LIGHT_THRESHOLD),
        remap = false)
    private static int patchee$lightOverlayThreshold(int original) {
        Toggles toggles = Config.toggles();
        boolean active = toggles.masterEnabled() && toggles.featureEnabled(LightOverlayFeature.ID);
        return LightOverlayThreshold
            .constant(active, currentDimension(), toggles.lightOverlayMaxNormal(), toggles.lightOverlayMaxNether());
    }

    /** The dimension the client is currently in, or the Overworld when there is no world yet. */
    private static int currentDimension() {
        Minecraft minecraft = Minecraft.getMinecraft();
        World world = minecraft == null ? null : minecraft.theWorld;
        return world == null ? 0 : world.provider.dimensionId;
    }
}
