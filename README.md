# MC Molten Metals

First-pass NeoForge 1.21.1 implementation of palette-derived molten metal fluids.

## Current scope

- Molten Iron
- Molten Copper
- Molten Gold
- Molten Netherite
- Source + flowing fluid registration
- Lava behavior inherited from Minecraft's `LavaFluid`
- Normal bucket items and liquid blocks
- JEI-visible registered fluids/buckets; no recipes yet
- `/mcmoltenmetals <metal>` gives the matching bucket and requires no operator permission
- Runtime-generated fluid textures: the currently loaded vanilla lava animation is remapped onto the currently loaded ingot texture palette

The generated resource pack is written under `config/mcmoltenmetals/generated_resource_pack`. On the first client load, the mod generates the textures and requests one additional resource reload so the generated sprites are stitched into the block atlas.

Examples:

```text
/mcmoltenmetals iron
/mcmoltenmetals copper
/mcmoltenmetals gold
/mcmoltenmetals netherite
```

No melting, casting, or other recipes are included in this pass.
