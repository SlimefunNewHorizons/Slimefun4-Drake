package io.github.thebusybiscuit.slimefun4.implementation.guide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;

/** A bounded traversal of the registered primary recipe for each Slimefun ingredient. */
record GuideRecipePath(List<Step> steps, boolean truncated) {

    record Step(SlimefunItem item, int depth) {}

    static GuideRecipePath collect(SlimefunItem target, int limit, Predicate<SlimefunItem> canExpand) {
        List<Step> steps = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        steps.add(new Step(target, 0));
        visited.add(target.getId());
        boolean truncated = false;

        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (!canExpand.test(step.item())) {
                continue;
            }

            for (ItemStack ingredient : step.item().getRecipe()) {
                SlimefunItem item = SlimefunItem.getByItem(ingredient);
                if (item != null && visited.add(item.getId())) {
                    if (steps.size() == limit) {
                        truncated = true;
                    } else {
                        steps.add(new Step(item, step.depth() + 1));
                    }
                }
            }
        }

        return new GuideRecipePath(List.copyOf(steps), truncated);
    }
}
