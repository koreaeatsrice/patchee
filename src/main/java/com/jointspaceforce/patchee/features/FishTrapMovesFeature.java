package com.jointspaceforce.patchee.features;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.jointspaceforce.patchee.core.Feature;
import com.jointspaceforce.patchee.core.Outcome;
import com.jointspaceforce.patchee.core.Step;

/**
 * Makes Pam's HarvestCraft Fish Trap work with InventoryBogoSorter's hotkeys.
 *
 * <p>
 * BogoSorter's move hotkeys (space+click move all, alt+click move same,
 * ctrl+click move one) are sent by the client and validated on the server, and
 * the server only accepts them from containers in its compat map. HarvestCraft's
 * {@code ContainerPamFishTrap} is not in that map, so every move hotkey is
 * rejected in the Fish Trap GUI. Patchee registers the missing compat on the
 * server. Middle-click sort is different: it runs in the client's own sorting
 * context, so sorting the trap's output (and the sorter's sort button in that
 * GUI) needs Patchee on the client too. The move hotkeys work from the server
 * jar alone; plain shift-click already works and is unchanged.
 *
 * <p>
 * The slot group is the <b>output grid only</b> (slots 0-17, 6 per row), never a
 * generic group. A generic group would include the bait slot (18), and
 * BogoSorter's sort writes stacks back into slot order — a middle-click sort
 * could then move the bait out of 18 and silently stop the trap. Restricting the
 * group to the fish outputs keeps sorting safe. The cost is that space/alt-click
 * on the bait slot does not pull bait out; ctrl-click and vanilla shift-click
 * still do.
 *
 * <p>
 * The bait slot is filled through a custom insertable instead, so a move-all
 * from the player inventory puts bait into slot 18 even though it is outside the
 * sortable group. Everything here reaches BogoSorter and HarvestCraft by
 * reflection: Patchee's jar must load on a server that has neither mod.
 */
public final class FishTrapMovesFeature implements Feature {

    public static final String ID = "FishTrapMoves";

    private static final String BOGO_API_CLASS = "com.cleanroommc.bogosorter.BogoSortAPI";
    private static final String BOGO_API_INTERFACE = "com.cleanroommc.bogosorter.api.IBogoSortAPI";
    private static final String BUILDER_INTERFACE = "com.cleanroommc.bogosorter.api.ISortingContextBuilder";
    private static final String INSERTABLE_INTERFACE = "com.cleanroommc.bogosorter.api.ICustomInsertable";
    private static final String TRAP_CONTAINER_CLASS = "com.pam.harvestcraft.ContainerPamFishTrap";

    /** The bait slot: first slot after the 18 outputs. */
    private static final int BAIT_SLOT = 18;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String configKey() {
        return "enableFishTrapMoves";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public String description() {
        return "Fish Trap: the inventory sorter's move hotkeys (space+click move all, alt+click move same, "
            + "ctrl+click move one) work from the server jar alone. The server also routes bait into the trap's "
            + "bait slot. Sorting the trap's output with middle-click (and the sorter's sort button in that GUI) "
            + "needs Patchee on the client too. Plain shift-click already works and is unchanged.";
    }

    @Override
    public String disabledMessage() {
        return "[FishTrapMoves] disabled in config - Fish Trap moves left as they were";
    }

    @Override
    public String failureMessage() {
        return "[FishTrapMoves] failed - Fish Trap is not sortable";
    }

    @Override
    public String targetClass() {
        return TRAP_CONTAINER_CLASS;
    }

    @Override
    public String missingTargetMessage() {
        return "[FishTrapMoves] Pam's HarvestCraft is not installed - Fish Trap moves skipped";
    }

    @Override
    public List<Step> steps() {
        return Collections.singletonList(this::registerSorterCompat);
    }

    /**
     * One step: find BogoSorter, validate the API shape, then register the sort
     * group and the bait insertable against the Fish Trap container class.
     */
    private Outcome registerSorterCompat(com.jointspaceforce.patchee.core.PatchContext ctx) throws Exception {
        Class<?> apiClass = ctx.reflect()
            .find(BOGO_API_CLASS);
        if (apiClass == null) {
            ctx.log()
                .info("[FishTrapMoves] InventoryBogoSorter is not installed - skipped");
            return Outcome.SKIPPED;
        }

        Class<?> apiInterface = ctx.reflect()
            .find(BOGO_API_INTERFACE);
        Class<?> builderInterface = ctx.reflect()
            .find(BUILDER_INTERFACE);
        Class<?> insertableInterface = ctx.reflect()
            .find(INSERTABLE_INTERFACE);
        Class<?> containerClass = ctx.reflect()
            .find(TRAP_CONTAINER_CLASS);
        if (apiInterface == null || builderInterface == null || insertableInterface == null || containerClass == null) {
            ctx.log()
                .error(
                    "[FishTrapMoves] BogoSorter API is incomplete (api={}, builder={}, insertable={})",
                    apiInterface,
                    builderInterface,
                    insertableInterface);
            return Outcome.FAILED;
        }

        // Validate the builder shape BEFORE registering anything: a changed API
        // must fail here, not inside another mod's sort loop.
        final Method addSlotGroup = builderInterface.getMethod("addSlotGroup", int.class, int.class, int.class);
        Method addCompat = apiInterface.getMethod("addCompat", Class.class, BiConsumer.class);
        Method addCustomInsertable = apiInterface.getMethod("addCustomInsertable", Class.class, insertableInterface);

        Field instanceField = apiClass.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Object api = instanceField.get(null);
        if (api == null) {
            ctx.log()
                .error("[FishTrapMoves] BogoSorter's BogoSortAPI.INSTANCE is null");
            return Outcome.FAILED;
        }

        final AtomicBoolean reported = new AtomicBoolean();
        BiConsumer<Object, Object> compat = (container, builder) -> {
            try {
                addSlotGroup.invoke(builder, 0, 18, 6);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                if (reported.compareAndSet(false, true)) {
                    ctx.log()
                        .error(
                            "[FishTrapMoves] could not add the output sort group - middle-click sort will be skipped",
                            failure);
                }
            }
        };

        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("insert".equals(name)) {
                // insert(Container, List<SlotAccessor>, ItemStack, boolean)
                return insertBait((Container) args[0], (ItemStack) args[2], (Boolean) args[3]);
            }
            if ("toString".equals(name)) {
                return "PatcheeFishTrapInsertable";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            return null;
        };
        Object insertable = Proxy
            .newProxyInstance(insertableInterface.getClassLoader(), new Class<?>[] { insertableInterface }, handler);

        addCompat.invoke(api, containerClass, compat);
        addCustomInsertable.invoke(api, containerClass, insertable);

        ctx.log()
            .info(
                "[FishTrapMoves] armed - Fish Trap is sortable (outputs 0-17, 6 per row); move hotkeys accept "
                    + "bait into slot 18");
        return Outcome.APPLIED;
    }

    /**
     * Puts {@code stack} into the bait slot, never taking more than the slot
     * allows. Called from the sorter's insert path by reflection, so it only
     * uses vanilla types.
     *
     * @param emptyOnly when true, only an empty bait slot may be used
     * @return the leftover stack, or {@code null} when everything fitted
     */
    static ItemStack insertBait(Container container, ItemStack stack, boolean emptyOnly) {
        if (container == null || stack == null || stack.stackSize <= 0) {
            return stack;
        }
        Slot slot;
        try {
            slot = (Slot) container.inventorySlots.get(BAIT_SLOT);
        } catch (IndexOutOfBoundsException | NullPointerException absent) {
            return stack;
        }
        if (slot == null || slot.getSlotIndex() != BAIT_SLOT || !slot.isItemValid(stack)) {
            return stack;
        }

        int limit = Math.min(slot.getSlotStackLimit(), stack.getMaxStackSize());
        if (limit <= 0) {
            return stack;
        }

        ItemStack existing = slot.getStack();
        if (existing == null) {
            ItemStack placed = stack.copy();
            placed.stackSize = Math.min(limit, stack.stackSize);
            slot.putStack(placed);
            slot.onSlotChanged();
            stack.stackSize -= placed.stackSize;
            return stack.stackSize <= 0 ? null : stack;
        }

        if (emptyOnly || !existing.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(existing, stack)) {
            return stack;
        }
        if (existing.stackSize >= limit) {
            return stack;
        }
        int moved = Math.min(limit - existing.stackSize, stack.stackSize);
        existing.stackSize += moved;
        slot.onSlotChanged();
        stack.stackSize -= moved;
        return stack.stackSize <= 0 ? null : stack;
    }
}
