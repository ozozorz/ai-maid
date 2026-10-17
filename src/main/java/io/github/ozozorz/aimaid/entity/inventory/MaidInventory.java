package io.github.ozozorz.aimaid.entity.inventory;

import java.util.Comparator;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Predicate;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public class MaidInventory extends SimpleContainer {

    public static final int STORAGE_SIZE = 35;
    private long changeVersion;

    public MaidInventory() {
        super(STORAGE_SIZE);
    }

    /** 供等待补给的服务端行为观察变化；不持久化，也不替代 Slot 同步。 */
    public long getChangeVersion() {
        return changeVersion;
    }

    @Override
    public void setChanged() {
        changeVersion++;
        super.setChanged();
    }

    /** 所有位置都达到各自堆叠上限时，任何普通物品都无法继续存入。 */
    public boolean isCompletelyFull() {
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (stack.isEmpty() || stack.getCount() < Math.min(getMaxStackSize(), stack.getMaxStackSize())) return false;
        }
        return true;
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
