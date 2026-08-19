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

The first-pass Molten Fabricator behavior is:

- An 8-bucket internal lava input tank accepts lava from filled fluid containers in the GUI or from NeoForge/Mekanism fluid logistics.
- An 8-bucket internal molten-metal output tank exposes its contents to fluid logistics and uses Mekanism's configurable fluid side system. The default sides are left/back/top/bottom input and right output, with fluid auto-ejection enabled.
- One diorite is consumed per operation.
- A copper or iron ingot/raw item selects the output metal but is not consumed. Common `c:` tags, legacy `forge:` tags, and conventional item IDs are recognized.
- One operation currently converts 1,000 mB lava + 1 diorite into 1,000 mB of the selected molten copper or molten iron over five seconds.
- The output buffer cannot mix fluids, so changing the selector while another molten metal remains buffered simply pauses processing until the output tank is emptied.
- The crafting recipe keeps the Metallurgic Infuser shell pattern but replaces its center osmium with Mekanism's Basic Mechanical Pipe: `I#I / RPR / I#I`, where `I` is an iron ingot, `#` is a furnace, `R` is redstone dust, and `P` is `mekanism:basic_mechanical_pipe`.
- The placed block and inventory item inherit Mekanism's Chemical Infuser model, so the exterior appearance is the same as the Chemical Infuser while remaining a distinct `mcmoltenmetals:molten_fabricator` block/item.

Generated client assets are stored under `config/mcmoltenmetals/generated_resource_pack`. Generated lava fluid tags and optional Fabricator server data are stored under `config/mcmoltenmetals/generated_data_pack`.
