# MC Molten Metals

NeoForge 1.21.1 mod that discovers metal materials at startup and registers palette-derived molten versions of them.

## Current scope

- No fixed metal list. An ingot convention establishes a metal identity: common `c:` / legacy `forge:` `ingots/<metal>` tags and conventional `*_ingot` items are discovered dynamically.
- Raw-material, scrap, and ore conventions are supporting inputs only. They do not create separate molten materials by themselves. For example, bauxite does not become `Molten Bauxite` merely because it is a raw/ore material.
- The registered name/identity and preferred palette artwork are both ingot-led. Matching ingot artwork is used for the molten palette whenever possible; raw material, scrap, and ore artwork remain fallback sources only if usable ingot artwork cannot be resolved.
- Discovery metadata is cached under `config/mcmoltenmetals/metal-discovery-cache.json`. The loaded mod set and discovery algorithm version are fingerprinted every startup; the cache is reused only while that fingerprint still matches.
- Registry entries are recreated every game startup from the discovered material list. Minecraft registries do not persist across launches, so the cache accelerates discovery rather than replacing registration.
- Each discovered metal gets its own `Molten <Metal>` source fluid, flowing fluid, liquid block, and `Molten <Metal> Bucket` item.
- Fluid mechanics inherit from Minecraft's `LavaFluid`. NeoForge-side movement, item movement, and source-conversion behavior delegate to the registered vanilla lava `FluidType` rather than using approximations.
- The generated data pack adds the dynamic source/flowing fluids to `minecraft:lava`. Entity contact also falls back to Minecraft's own `Entity.lavaHurt()` when a molten-metal `FluidType` is detected but the vanilla lava tag has not been rebound yet, preserving normal lava fire/damage and fall-distance semantics.
- Vanilla lava is an artwork/animation template, not the identity of the registered fluid. The current lava still/flow animation is palette-remapped from the selected metal artwork, normally the ingot.
- The bucket is also generated: the current vanilla lava-bucket artwork is used as a shape/mask template while the contained lava pixels are palette-remapped to the metal.
- Generated fluid sprites are placed in the block texture atlas; generated bucket artwork is placed in the item texture atlas.
- Generated texture signatures include the source metal artwork and lava/bucket templates. Unchanged generated assets are reused on startup/resource reload.
- JEI can display the registered bucket items/fluids. No data-pack melting or casting recipes are included yet.
- `/mcmoltenmetals <metal>` gives the corresponding `Molten <Metal> Bucket` and requires no operator permission.
- `/mcmoltenmetals fabricator` gives the optional Molten Fabricator when Mekanism is installed, also without operator permission.

## Optional Mekanism integration

When Mekanism is installed, MC Molten Metals additionally registers a **Molten Fabricator**. Mekanism is an optional dependency: the integration classes are not loaded and the machine is not registered when Mekanism is absent.

The current Molten Fabricator behavior is:

- For offline-simulation testing, both the lava input tank and molten output tank are temporarily **1,000,000 buckets / 1,000,000,000 mB** each.
- Lava can enter through filled fluid containers in the GUI or through normal NeoForge/Mekanism fluid logistics while the chunk is loaded.
- Molten output is exposed through normal fluid logistics and uses Mekanism's configurable fluid side system. The default sides are left/back/top/bottom input and right output, with fluid auto-ejection enabled.
- The machine draws **100 FE/RF per tick** while actively processing. Its test energy buffer is temporarily **1,000,000,000 FE/RF**; one five-second (100-tick) operation still consumes exactly 10,000 FE/RF.
- Energy can enter through normal Mekanism/NeoForge energy logistics on configured input sides or through the GUI energy-item slot while loaded.
- One diorite is consumed per operation. A copper or iron ingot/raw item selects the output metal but is not consumed.
- One operation converts 1,000 mB lava + 1 diorite into 1,000 mB of the selected molten copper or molten iron over five seconds.
- The output buffer cannot mix fluids. Insufficient power/input or a blocked output pauses progress; changing the selected metal resets incompatible partial progress.
- The Fabricator has chunk-independent logical state and one processing implementation. A persistent registry records its dimension + block position for its lifetime.
- Every server game tick, a global offline tick checks registered machine positions. If the containing chunk is loaded, Minecraft/Mekanism performs the normal tile tick. If the chunk is not loaded, the registry directly ticks the same logical Fabricator state. There is no reload-time catch-up simulation.
- The logical machine has a same-game-tick guard so a chunk load/unload boundary cannot cause both the loaded and offline paths to process it twice.
- Loaded Fabricators remain standard Mekanism/NeoForge machines: Universal Cables, Mechanical Pipes, GUI container slots, side configuration, and other normal capability-based interactions continue to work.
- Unloaded Fabricators currently use the resources already present in their persistent logical state. Ordinary Mekanism pipes/cables/generators are not simulated while unloaded. The logical state exposes chunk-independent resource insertion/extraction methods for the offline power/fluid/item network planned next.
- Server downtime does not create progress because the offline simulation only advances from actual server game ticks.
- The crafting recipe keeps the Metallurgic Infuser shell pattern but replaces its center osmium with Mekanism's Basic Mechanical Pipe: `I#I / RPR / I#I`, where `I` is an iron ingot, `#` is a furnace, `R` is redstone dust, and `P` is `mekanism:basic_mechanical_pipe`.
- The placed block and inventory item inherit Mekanism's Chemical Infuser model and custom shape, so the exterior appearance and occlusion behavior match the Chemical Infuser while remaining a distinct `mcmoltenmetals:molten_fabricator` block/item.

Generated client assets are stored under `config/mcmoltenmetals/generated_resource_pack`. Generated lava fluid tags and optional Fabricator server data are stored under `config/mcmoltenmetals/generated_data_pack`.
