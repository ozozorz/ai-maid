package io.github.ozozorz.aimaid.entity.inventory;

import java.util.Comparator;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Predicate;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public class MaidInventory extends SimpleContainer {

    public static final int STORAGE_SIZE = 35;

    public MaidInventory() {
        super(STORAGE_SIZE);
    }

    // 第一份符合条件的东西在哪里？
    public OptionalInt findFirstMatchingSlot(Predicate<ItemStack> predicate) {
        Objects.requireNonNull(predicate);
        for (int slot = 0; slot < this.getContainerSize(); slot++) {
            ItemStack stack = this.getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return OptionalInt.of(slot);
            }
        }
        return OptionalInt.empty();
    }

    // 符合条件的东西中，按调用者定义的比较规则，最合适的在哪里？
    public OptionalInt findBestMatchingSlot(Predicate<ItemStack> predicate, Comparator<ItemStack> comparator) {
        Objects.requireNonNull(predicate);
        Objects.requireNonNull(comparator);

        int bestSlot = -1;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int slot = 0; slot < this.getContainerSize(); slot++) {
            ItemStack stack = this.getItem(slot);
            if (stack.isEmpty() || !predicate.test(stack)) {
                continue;
            }
            if (bestSlot < 0 || comparator.compare(stack, bestStack) > 0) {
                bestSlot = slot;
                bestStack = stack;
            }
        }
        if (bestSlot < 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(bestSlot);
    }

    // 符合条件的物品一共有多少个？
    public int countMatching(Predicate<ItemStack> predicate) {
        Objects.requireNonNull(predicate);
        int count = 0;
        for (int slot = 0; slot < this.getContainerSize(); slot++) {
            ItemStack stack = this.getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
