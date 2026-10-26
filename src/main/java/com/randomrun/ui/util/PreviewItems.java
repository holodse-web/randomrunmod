package com.randomrun.ui.util;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.EmptyTagLookupWrapper;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.UseRemainder;

public final class PreviewItems {
    private PreviewItems() {}

    public static ItemStack stack(Item item) {
        return stack(item, 1);
    }

    public static ItemStack stack(Item item, int count) {
        Holder.Reference<Item> holder = item.builtInRegistryHolder();
        if (holder.areComponentsBound()) {
            return new ItemStack(holder, count);
        }
        return new ItemStack(Defaults.ITEMS.getOrThrow(holder.key()), count);
    }

    private static final class Defaults {
        private static final Registry<Item> ITEMS = loadItems();

        private static Registry<Item> loadItems() {
            MappedRegistry<Item> items = new MappedRegistry<>(Registries.ITEM, Lifecycle.stable());
            BuiltInRegistries.ITEM.listElements().forEach(holder ->
                items.register(holder.key(), holder.value(), RegistrationInfo.BUILT_IN));
            items.freeze();

            HolderLookup.Provider vanilla = VanillaRegistries.createWorldLookup();
            HolderLookup.RegistryLookup<Item> itemLookup = EmptyTagLookupWrapper.wrap(items);
            HolderLookup.Provider lookup = HolderLookup.Provider.create(vanilla.listRegistries().map(registry ->
                registry.key().equals(Registries.ITEM) ? itemLookup : registry));

            for (DataComponentInitializers.PendingComponents<?> pending :
                    BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)) {
                if (pending.key().equals(Registries.ITEM)) {
                    pending.forEach((holder, values) -> holder.bindComponents(prepareComponents(values, items)));
                }
            }
            return items;
        }

        private static DataComponentMap prepareComponents(DataComponentMap components, Registry<Item> items) {
            UseRemainder remainder = components.get(DataComponents.USE_REMAINDER);
            if (remainder == null) {
                return components;
            }
            ItemStackTemplate template = remainder.convertInto();
            Holder.Reference<Item> holder = items.getOrThrow(template.item().value().builtInRegistryHolder().key());
            return DataComponentMap.builder().addAll(components)
                .set(DataComponents.USE_REMAINDER,
                    new UseRemainder(new ItemStackTemplate(holder, template.count(), template.components())))
                .build();
        }
    }
}
