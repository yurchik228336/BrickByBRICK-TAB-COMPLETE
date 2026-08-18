# Brick by Brick Tab

[Русская версия](README.ru.md)

By BRICK. Telegram: https://t.me/brickstu

Client-only Fabric mod for Minecraft 26.2. It watches blocks you place, guesses the next one, and draws it as a ghost.

A trained model is optional. Without weights the mod still suggests using simple "continue the line" rules. Natural terrain is never used as training data or as model input.

## Install

1. Minecraft 26.2, [Fabric Loader](https://fabricmc.net/) 0.19.3+, Fabric API.
2. Put `brickbybricktab-0.1.0.jar` in `mods/`.

Optional custom weights, or let the mod pull the latest published pair from the catalog
on the same host as `telemetryEndpoint` (default `https://bbb.ruscreat.dev`):

```
<game>/config/brickbybricktab/model/model.bbbt
<game>/config/brickbybricktab/model/palette.json
```

The in-game settings show the catalog version when the files came from the server.
Reload model checks for a newer catalog entry. `autoUpdateModel` (on by default) also
checks on startup and every few hours. Set `autoUpdateModel` to false in
`config.json` if you want to keep a local file.

Then press Reload model in the in-game settings, or restart the game.

## Keys

| Key | Action |
| --- | --- |
| B | Toggle suggestions |
| V | Next suggestion |

Settings, accept, describe this build, and focus-region keys have no default bind. Set them in Controls if you want them. Accept can place a block, so it stays unbound on purpose.

## In game

- Ghost at the empty cell under the crosshair (green if you have the block, red if not).
- HUD list of ranked suggestions.
- Fast place from inventory: right-click the ghost, vanilla swap onto the hotbar, then a normal place. Nothing is spawned.
- On-device learning from your own placements. Nothing leaves the machine for that.
- Two separate consent questions if you ever want to share data: building data vs build descriptions.

## Model

Small transformer that predicts the next player-placed block.

| | |
| --- | --- |
| Parameters | about 3.2 million |
| Size | dim 192, 6 heads, FFN 512 |
| Encoder | 4 layers, self-attention |
| Decoder | 2 layers, cross-attention only (one query token) |
| Context | last 64 placed blocks |
| Position head | 16x16x16 relative grid (4096 cells, offsets -8 to +7) plus stop |
| Block vocab | 816 (814 building blocks + none/unk) |
| Orientation | 24 |

Two-step query: first where the next block goes, then which block and rotation, given that cell.

Only player-placed blocks go in. No natural terrain in the input.

## Training data

- [3D-Craft](https://github.com/facebookresearch/craftassist) houses (Facebook AI / VoxelCNN, ICCV 2019): about 2500 Creative builds with real click order.
- [Minecraft Fable](https://huggingface.co/datasets/TheAIdude303/Minecraft-Fable-Schem-final): about 28k player schematics. Placement order is reconstructed by adjacency growth, not recorded clicks.
- Building-block palette only (wood, stone, glass, and so on). Ores, terrain, plants and fluids are out.

The Java runtime in this repo is what actually runs at inference. Weights are a `.bbbt` file plus `palette.json`.

## Train your own model

The Java code under `src/main/java/dev/bbbt` is the source of truth. A trainer has to
match that spec or the mod will refuse the file. This repo does not ship training
code; the notes below are enough to write one, or to feed a dump into a private trainer.

### Task

Given the last 64 player-placed blocks, predict the next one in two steps:

1. Where it goes, as a cell on a 16x16x16 grid of relative offsets (-8 to +7) plus a stop bit.
2. Which block and which of 24 orientations, given that cell.

Never put natural terrain in the input. Only blocks a player placed.

### Data

Public sets that already work for this:

- [3D-Craft](https://github.com/facebookresearch/craftassist) houses: real click order.
- [Minecraft Fable](https://huggingface.co/datasets/TheAIdude303/Minecraft-Fable-Schem-final): player schematics. Reconstruct a placement order by growing from a seed along adjacency; that is not recorded clicks.

Opt-in telemetry from the mod is JSONL, one sequence per line, already relative, no player ids:

```
{"session": 3, "steps": [
  {"dx": 0, "dy": 0, "dz": 0, "block": "minecraft:stone", "orient": 0},
  {"dx": 1, "dy": 0, "dz": 0, "block": "minecraft:oak_planks", "orient": 0}
]}
```

If you run a collector, export the placement shards (top-level `*.jsonl`, not `_index/`)
and point the trainer at that folder.

### Architecture to match

Keep these numbers unless you also change `dev.bbbt.model.ModelSpec` and `WeightStore`:

| | |
| --- | --- |
| dim | 192 |
| heads | 6 |
| FFN | 512 |
| encoder | 4 self-attention layers |
| decoder | 2 layers, cross-attention only, one query token |
| context | 64 |
| grid | 16^3 = 4096 cells |
| vocab | 816 with the default palette (814 blocks + none + unk) |
| orientations | 24 |
| parameters | about 3.2 million |

GELU is the tanh approximation. LayerNorm uses population variance and `ln_eps = 1e-5`.
The encoder memory has no final LayerNorm. 2-D weights stay in PyTorch `[out, in]` layout;
the Java side computes `y = x @ W^T`.

A reasonable training recipe: AdamW, cosine decay with warmup, label smoothing around 0.05,
loss = CE(position) + CE(block) + CE(orientation) + BCE(stop). Augment with D4 rotations
and mirrors of the build so the same house is not always facing the same way.

### Files the mod reads

`palette.json`:

```
{"format": 1, "blocks": ["minecraft:stone", "..."], "aliases": {}}
```

Token 0 is none, token 1 is unk, `blocks[i]` is token `i + 2`. The vocab size in the
weight header must match `len(blocks) + 2`.

`model.bbbt`, big-endian header, little-endian payload:

```
bytes 0..3   ASCII BBBT
bytes 4..7   int32 BE  container version = 1
bytes 8..11  int32 BE  header length
then         UTF-8 JSON {arch, tensors:[{name, shape, dtype, offset}]}
then         raw tensors in declared offset order
```

`arch` must include `dim`, `heads`, `ff_dim`, `enc_layers`, `dec_layers`, `vocab`,
`orient_count`, `grid_volume`, `ln_eps`. Weights are usually fp16; biases and LayerNorm
gains/biases stay fp32.

Copy both files into `config/brickbybricktab/model/`, or publish them on a catalog
the mod already talks to:

- `GET /v1/model/latest` returns `{version, weightsSha256, paletteSha256, weightsBytes, paletteBytes}`
- `GET /v1/model/{version}/weights` and `.../palette` are the two files

The `version` string is yours (`v4`, `2026-08-18`, ...). The mod shows it in settings
after a successful download.

## Settings that stay in the file

The menu only shows things you actually change while playing. These still exist in `config/brickbybricktab/config.json` if you need them:

- `refreshDelayTicks`, `previewDepth`, `bulkChangeThreshold`
- `debugOverlay`
- LoRA knobs: `loraStrength`, `loraTrainEveryPlacements`, `loraScope`, rank, learning rate
- `captionCollectLocally`
- `telemetryEndpoint`, `captionEndpoint`, `modelOverridePath`
- `autoUpdateModel`, `modelEndpoint` (blank `modelEndpoint` uses `telemetryEndpoint`)

## Build

JDK 25. From the repo root:

```
./gradlew build
```

On Windows: `.\gradlew.bat build`. The jar lands in `build/libs/`.

## License

MIT. Author: BRICK. See [LICENSE](LICENSE).
