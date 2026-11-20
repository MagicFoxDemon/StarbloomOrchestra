package dev.starbloom.orchestra.music;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 复制纸带：一卷打孔纸带 + 若干空白纸带 → 同样多的打孔纸带，原纸带留在合成格里（和原版复制成书一样）。
 * 复制出的纸带指向同一份内容；内容不可修改，所以互不影响。
 */
public class RollCopyRecipe extends CustomRecipe {
    public static final RollCopyRecipe INSTANCE = new RollCopyRecipe(dev.starbloom.orchestra.StarbloomOrchestra.id("music_roll_copy"), CraftingBookCategory.MISC);
    public static final RecipeSerializer<RollCopyRecipe> SERIALIZER = new SimpleCraftingRecipeSerializer<>(RollCopyRecipe::new);

    public RollCopyRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        return !assemble(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        return assemble(input);
    }

    /** 合成结果；不需要注册表（世界测试直接调用）。 */
    public ItemStack assemble(CraftingContainer input) {
        ItemStack source = ItemStack.EMPTY;
        int blanks = 0;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(Instruments.MUSIC_ROLL_ITEM.get()) && RollInfo.has(s)) {
                if (!source.isEmpty()) return ItemStack.EMPTY;
                source = s;
            } else if (s.is(Instruments.BLANK_ROLL.get())) {
                blanks++;
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (source.isEmpty() || blanks == 0) return ItemStack.EMPTY;
        return source.copyWithCount(blanks);
    }

    /** 至少两格（一卷打孔纸带加一卷空白纸带）才放得下。 */
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        NonNullList<ItemStack> result = NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack s = input.getItem(i);
            if (s.is(Instruments.MUSIC_ROLL_ITEM.get()) && RollInfo.has(s)) {
                result.set(i, s.copyWithCount(1));
                break;
            }
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
