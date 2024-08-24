package com.petrolpark.destroy;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.petrolpark.destroy.advancement.DestroyAdvancementTrigger;
import com.petrolpark.destroy.badge.Badge;
import com.petrolpark.destroy.badge.DestroyBadges;
import com.petrolpark.destroy.block.DestroyBlocks;
import com.petrolpark.destroy.block.entity.DestroyBlockEntityTypes;
import com.petrolpark.destroy.chemistry.api.Chemistry;
import com.petrolpark.destroy.chemistry.forge.event.ForgeChemistryEventFirer;
import com.petrolpark.destroy.chemistry.legacy.index.DestroyGenericReactions;
import com.petrolpark.destroy.chemistry.legacy.index.DestroyGroupFinder;
import com.petrolpark.destroy.chemistry.legacy.index.DestroyMolecules;
import com.petrolpark.destroy.chemistry.legacy.index.DestroyReactions;
import com.petrolpark.destroy.chemistry.legacy.index.DestroyTopologies;
import com.petrolpark.destroy.client.gui.menu.DestroyMenuTypes;
import com.petrolpark.destroy.client.particle.DestroyParticleTypes;
import com.petrolpark.destroy.compat.CompatMods;
import com.petrolpark.destroy.compat.createbigcannons.CreateBigCannons;
import com.petrolpark.destroy.compat.curios.Curios;
import com.petrolpark.destroy.compat.jei.DestroyJEI;
import com.petrolpark.destroy.config.DestroyAllConfigs;
import com.petrolpark.destroy.effect.DestroyMobEffects;
import com.petrolpark.destroy.effect.potion.DestroyPotions;
import com.petrolpark.destroy.entity.DestroyEntityTypes;
import com.petrolpark.destroy.entity.attribute.DestroyAttributes;
import com.petrolpark.destroy.fluid.DestroyFluids;
import com.petrolpark.destroy.fluid.pipeEffectHandler.DestroyOpenEndedPipeEffects;
import com.petrolpark.destroy.item.CoaxialGearBlockItem.GearOnShaftPlacementHelper;
import com.petrolpark.destroy.item.CoaxialGearBlockItem.ShaftOnGearPlacementHelper;
import com.petrolpark.destroy.item.DecayingItemHandler;
import com.petrolpark.destroy.item.DestroyItems;
import com.petrolpark.destroy.item.attributes.DestroyItemAttributes;
import com.petrolpark.destroy.item.compostable.DestroyCompostables;
import com.petrolpark.destroy.item.creativeModeTab.DestroyCreativeModeTabs;
import com.petrolpark.destroy.item.potatoCannonProjectileType.DestroyPotatoCannonProjectileTypes;
import com.petrolpark.destroy.item.tooltip.ContaminatedItemDescription;
import com.petrolpark.destroy.item.tooltip.IDynamicItemDescription;
import com.petrolpark.destroy.item.tooltip.TempramentalItemDescription;
import com.petrolpark.destroy.network.DestroyMessages;
import com.petrolpark.destroy.recipe.DestroyCropMutations;
import com.petrolpark.destroy.recipe.DestroyExtrusions;
import com.petrolpark.destroy.recipe.DestroyRecipeTypes;
import com.petrolpark.destroy.registrate.DestroyRegistrate;
import com.petrolpark.destroy.sound.DestroySoundEvents;
import com.petrolpark.destroy.stats.DestroyStats;
import com.petrolpark.destroy.util.DestroyTags;
import com.petrolpark.destroy.util.circuit.CircuitPatternHandler;
import com.petrolpark.destroy.util.circuit.CircuitPuncherHandler;
import com.petrolpark.destroy.util.vat.VatMaterial;
import com.petrolpark.destroy.world.damage.DestroyDamageTypes;
import com.petrolpark.destroy.world.loot.DestroyLoot;
import com.petrolpark.destroy.world.village.DestroyVillagers;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipHelper.Palette;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.simibubi.create.foundation.placement.PlacementHelpers;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryBuilder;

@Mod(Destroy.MOD_ID)
public class Destroy {
    public static final String MOD_ID = "destroy";
    public static final String NAME = "Destroy";

    // Utility
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean datagen = false;

    // Registrate(s)
    @MoveToPetrolparkLibrary
    public static final DestroyRegistrate PETROLPARK_REGISTRATE = new DestroyRegistrate("petrolpark");
    public static final DestroyRegistrate REGISTRATE = new DestroyRegistrate(MOD_ID);

    // Level-attached managers
    public static final CircuitPuncherHandler CIRCUIT_PUNCHER_HANDLER = new CircuitPuncherHandler();
    public static final CircuitPatternHandler CIRCUIT_PATTERN_HANDLER = new CircuitPatternHandler();
    public static final ThreadLocal<DecayingItemHandler> DECAYING_ITEM_HANDLER = ThreadLocal.withInitial(() -> DecayingItemHandler.DUMMY);

    public static ResourceLocation asResource(String path) {
        return new ResourceLocation(MOD_ID, path);
    };

    // Really early stuff
    static {
        // Tooltips
		REGISTRATE.setTooltipModifierFactory(item -> {
			return new ItemDescription.Modifier(item, Palette.STANDARD_CREATE)
				.andThen(TooltipModifier.mapNull(KineticStats.create(item)))
                .andThen(TooltipModifier.mapNull(IDynamicItemDescription.create(item)))
                .andThen(new TempramentalItemDescription())
                .andThen(new ContaminatedItemDescription());
		});
        // Placement Helpers which need to come before Create's
        PlacementHelpers.register(new GearOnShaftPlacementHelper());
        PlacementHelpers.register(new ShaftOnGearPlacementHelper());
	};

    // Registries
    public static ResourceKey<Registry<Badge>> BADGE_REGISTRY_KEY = PETROLPARK_REGISTRATE.makeRegistry("badge", RegistryBuilder::new);

    // Initiation
    public Destroy() {
        ModLoadingContext modLoadingContext = ModLoadingContext.get();
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;

        PETROLPARK_REGISTRATE.registerEventListeners(modEventBus);
        REGISTRATE.registerEventListeners(modEventBus);

        // Mod objects
        if (!datagen) DestroySoundEvents.prepare(); // Sound datagen is broken and I can't be bothered to fix it
        DestroyBadges.register();
        DestroyCreativeModeTabs.register(modEventBus);
        DestroyTags.register();
        DestroyBlockEntityTypes.register();
        DestroyBlocks.register();
        DestroyMobEffects.register(modEventBus);
        DestroyPotions.register(modEventBus);
        DestroyItems.register();
        DestroyMenuTypes.register();
        DestroyRecipeTypes.register(modEventBus);
        DestroyParticleTypes.register(modEventBus);
        DestroyFluids.register();
        DestroyCropMutations.register();
        DestroyEntityTypes.register();
        DestroyVillagers.register(modEventBus);
        DestroyLoot.register(modEventBus);
        DestroyDamageTypes.register();
        DestroyStats.register(modEventBus);
        DestroyItemAttributes.register();
        DestroyAttributes.register(modEventBus);

        // Events
        MinecraftForge.EVENT_BUS.register(this);

        // Config
        DestroyAllConfigs.register(modLoadingContext);

        // Initiation Events
        modEventBus.addListener(Destroy::init);
        if (!datagen) modEventBus.addListener(DestroySoundEvents::register);
        modEventBus.addListener(DestroyClient::clientInit);
        modEventBus.addListener(EventPriority.LOWEST, Destroy::gatherData);

        // JEI compat
        if (CompatMods.JEI.isLoading()) {
            forgeEventBus.register(DestroyJEI.ClientEvents.class);
        };

        // Client
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DestroyClient.clientCtor(modEventBus, forgeEventBus));

        // Optional compatibility mods. According to the Create main class doing the same thing, this isn't thread safe
        CompatMods.BIG_CANNONS.executeIfInstalled(() -> () -> CreateBigCannons.init(modEventBus, forgeEventBus));
        CompatMods.CURIOS.executeIfInstalled(() -> () -> Curios.init(modEventBus, forgeEventBus));
    };

    // Initiation Events

    public static void init(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Chemistry.initiate(new ForgeChemistryEventFirer(), Destroy.LOGGER::info);
            DestroyMessages.register();
            DestroyCompostables.register();
        });
        DestroyStats.register();
        VatMaterial.registerDestroyVatMaterials();
        DestroyOpenEndedPipeEffects.register();
        DestroyAdvancementTrigger.register();
        DestroyPotatoCannonProjectileTypes.register();
        DestroyExtrusions.register();

        // Chemistry
        DestroyGroupFinder.register();
        DestroyTopologies.register();
        DestroyMolecules.register();
        DestroyReactions.register();
        DestroyGenericReactions.register();

        // Config
        GogglesItem.addIsWearingPredicate(player -> player.isCreative() && DestroyAllConfigs.SERVER.automaticGoggles.get());
    };

    public static void gatherData(GatherDataEvent event) {
    };
}
