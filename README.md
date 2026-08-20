# MC Molten Metals

NeoForge 1.21.1 mod that discovers metal materials at startup and registers palette-derived molten versions of them.

## Current scope

- No fixed metal list. An ingot convention establishes a metal identity: common `c:` / legacy `forge:` `ingots/<metal>` tags and conventional `*_ingot` items are discovered dynamically.
- Raw-material, scrap, and ore conventions are supporting inputs only. They do not create separate molten materials by themselves. For example, bauxite does not become `Molten Bauxite` merely because it is a raw/ore material.
- Source items can be excluded from molten discovery through `config/mcmoltenmetals/fabricator-recipes.json` under `molten_discovery.excluded_items`. The generated default currently contains `pixelmon:crystal`; there is no code-level Pixelmon blacklist. Changing this list changes the discovery-cache signature so the next startup rediscoveries molten candidates using the configured exclusions.
- The registered name/identity and preferred palette artwork are both ingot-led. Matching ingot artwork is used for the molten palette whenever possible; raw material, scrap, and ore artwork remain fallback sources only if usable ingot artwork cannot be resolved.
- Discovery metadata is cached under `config/mcmoltenmetals/metal-discovery-cache.json`. The loaded mod set, discovery algorithm version, and configured molten-source exclusions are fingerprinted; the cache is reused only while that fingerprint still matches.
- Registry entries are recreated every game startup from the discovered material list. Minecraft registries do not persist across launches, so the cache accelerates discovery rather than replacing registration.
- Each discovered metal gets its own `Molten <Metal>` source fluid, flowing fluid, liquid block, and `Molten <Metal> Bucket` item.
- Fluid mechanics inherit from Minecraft's `LavaFluid`. NeoForge-side movement, item movement, and source-conversion behavior delegate to the registered vanilla lava `FluidType` rather than using approximations.
- The generated data pack adds the dynamic source/flowing fluids to `minecraft:lava`. Entity contact also falls back to Minecraft's own `Entity.lavaHurt()` when a molten-metal `FluidType` is detected but the vanilla lava tag has not been rebound yet, preserving normal lava fire/damage and fall-distance semantics.
- Vanilla lava is an artwork/animation template, not the identity of the registered fluid. The current lava still/flow animation is palette-remapped from the selected metal artwork, normally the ingot.
- The bucket is also generated: the current vanilla lava-bucket artwork is used as a shape/mask template while the contained lava pixels are palette-remapped to the metal.
- Generated fluid sprites are placed in the block texture atlas; generated bucket artwork is placed in the item texture atlas.
- Generated texture signatures include the source metal artwork and lava/bucket templates. Unchanged generated assets are reused on startup/resource reload.
- JEI is optional and displays Molten Fabricator operations when both JEI and the optional Mekanism Fabricator are present.
- `/mcmoltenmetals <metal>` gives the corresponding `Molten <Metal> Bucket` and requires no operator permission.
- `/mcmoltenmetals fabricator` gives the optional Molten Fabricator when Mekanism is installed, also without operator permission.

## Optional Mekanism integration

When Mekanism is installed, MC Molten Metals additionally registers a **Molten Fabricator**. Mekanism is an optional dependency: the integration classes are not loaded and the machine is not registered when Mekanism is absent.

The current Molten Fabricator behavior is:

- For offline-simulation testing, both fluid tanks are temporarily **1,000,000 buckets / 1,000,000,000 mB** each and the energy buffer is **1,000,000,000 FE/RF**.
- The machine draws **100 FE/RF per tick** while actively processing. Current operations take five seconds / 100 ticks unless changed later.
- The left fluid gauge is a generalized input tank. It accepts lava for the original Fabricator operation and registered molten metals for casting.
- **Lava fabrication:** 1,000 mB lava + 1 diorite + a non-consumed copper/iron ingot or raw selector -> 1,000 mB matching molten metal.
- **Raw melting:** 50 matching raw-material items + 1 diorite -> 7,500 mB matching molten metal. Only actual `c:raw_materials/<metal>`, legacy `forge:raw_materials/<metal>`, or conventional `raw_<metal>` / `<metal>_raw` items qualify; dusts and scraps do not.
- **Casting:** 100 mB molten metal -> 1 associated ingot. Associated ingot resolution accepts common ingot tags and both `<metal>_ingot` and `ingot_<metal>` item naming.
- Molten iron has two casting modes in the GUI: **Ingot** produces the associated iron ingot, while **Steel** produces a discovered steel ingot. The mode is synchronized, saved, and part of the chunk-independent logical machine state.
- A dedicated item output slot receives cast ingots. Loaded item inputs/output use Mekanism's item side configuration and the output can auto-eject; loaded fluid and energy logistics remain normal Mekanism/NeoForge capabilities.
- Fluid side defaults remain left/back/top/bottom input and right output. Item side defaults likewise use left/back/top/bottom input and right output. All are editable with Mekanism's side configuration UI.
- The output fluid tank cannot mix fluids, and the item output cannot mix item types. Missing resources, insufficient power, a blocked output, or a recipe/mode change pauses or resets progress as appropriate.
- Recipe exclusions are stored in `config/mcmoltenmetals/fabricator-recipes.json` and are read at startup. Each recipe family has its own `excluded_metals` list: `lava_to_molten`, `raw_to_molten`, `molten_to_ingot`, and `steelmaking`; the same file also contains the separate full-item-ID `molten_discovery.excluded_items` list.
- `raw_to_molten` excludes `netherite`, `steel`, `refined_obsidian`, `refined_glowstone`, and `uranium` by default. The config migrates older generated files to add the newer safety exclusions once; they can still be removed manually afterward if a pack intentionally wants those recipes.
- Raw melting always requires a real raw-material item, so dust-only materials cannot enter this recipe family even if they remain valid molten candidates for other purposes.
- The Fabricator has chunk-independent logical state and one processing implementation. A persistent registry records its dimension + block position for its lifetime.
- Every server game tick, a global offline tick checks registered machine positions. If the containing chunk is loaded, Minecraft/Mekanism performs the normal tile tick. If the chunk is not loaded, the registry directly ticks the same logical Fabricator state. There is no reload-time catch-up simulation.
- The logical machine has a same-game-tick guard so a chunk load/unload boundary cannot cause both the loaded and offline paths to process it twice.
- Loaded Fabricators remain standard Mekanism/NeoForge machines for capabilities, GUI interaction, side configuration and logistics. Unloaded Fabricators use the same persistent logical inputs, outputs, energy, casting mode and progress.
- Ordinary Mekanism pipes/cables/generators are not simulated while unloaded. Chunk-independent insertion/extraction methods exist for the offline power/fluid/item network planned next.
- Server downtime does not create progress because the offline simulation only advances from actual server game ticks.
- The crafting recipe keeps the Metallurgic Infuser shell pattern but replaces its center osmium with Mekanism's Basic Mechanical Pipe: `I#I / RPR / I#I`, where `I` is an iron ingot, `#` is a furnace, `R` is redstone dust, and `P` is `mekanism:basic_mechanical_pipe`.
- The placed block and inventory item inherit Mekanism's Chemical Infuser model and custom shape, so the exterior appearance and occlusion behavior match the Chemical Infuser while remaining a distinct `mcmoltenmetals:molten_fabricator` block/item.

JEI presents the lava, raw-melting, normal casting and iron-to-steel operations in the Molten Fabricator category. The Fabricator item is registered as the recipe catalyst. Recipe-family exclusions are honored when JEI builds its recipe list.

Generated client assets are stored under `config/mcmoltenmetals/generated_resource_pack`. Generated lava fluid tags and optional Fabricator server data are stored under `config/mcmoltenmetals/generated_data_pack`.
