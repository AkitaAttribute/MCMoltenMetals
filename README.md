# MC Molten Metals

NeoForge 1.21.1 mod that discovers metal materials at startup and registers palette-derived molten versions of them.

## Current scope

- No fixed metal list. An ingot convention establishes a metal identity: common `c:` / legacy `forge:` `ingots/<metal>` tags and conventional `*_ingot` items are discovered dynamically.
- Raw-material and ore conventions are supporting inputs only. They may provide additional source artwork for an already-discovered metal, but they do not create separate molten materials by themselves. For example, bauxite does not become `Molten Bauxite` merely because it is a raw/ore material.
- Discovery metadata is cached under `config/mcmoltenmetals/metal-discovery-cache.json`. The loaded mod set and discovery algorithm version are fingerprinted every startup; the cache is reused only while that fingerprint still matches.
- Registry entries are recreated every game startup from the discovered material list. Minecraft registries do not persist across launches, so the cache accelerates discovery rather than replacing registration.
- Each discovered metal gets its own `Molten <Metal>` source fluid, flowing fluid, liquid block, and `Molten <Metal> Bucket` item.
- Fluid mechanics inherit from Minecraft's `LavaFluid`. The generated data pack also adds the dynamic fluids to `minecraft:lava` so lava-tag behavior applies normally.
- Vanilla lava is an artwork/animation template, not the identity of the registered fluid. The current lava still/flow animation is palette-remapped from the current metal item artwork.
- The bucket is also generated: the current vanilla lava-bucket artwork is used as a shape/mask template while the contained lava pixels are palette-remapped to the metal.
- Generated fluid sprites are placed in the block texture atlas; generated bucket artwork is placed in the item texture atlas.
- Generated texture signatures include the source metal artwork and lava/bucket templates. Unchanged generated assets are reused on startup/resource reload.
- JEI can display the registered bucket items/fluids. No recipes are included yet.
- `/mcmoltenmetals <metal>` gives the corresponding `Molten <Metal> Bucket` and requires no operator permission.

Generated client assets are stored under `config/mcmoltenmetals/generated_resource_pack`. Generated lava fluid tags are stored under `config/mcmoltenmetals/generated_data_pack`.

No melting, casting, or other recipes are included in this pass.
