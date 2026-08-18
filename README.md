# Brick by Brick Tab

[Русская версия](README.ru.md)

By BRICK. Telegram: https://t.me/brickstu

Client-only Fabric mod for Minecraft 26.2. It watches blocks you place, guesses the next one, and draws it as a ghost.

A trained model is optional. Without weights the mod still suggests using simple "continue the line" rules. Natural terrain is never used as training data or as model input.

## Install

1. Minecraft 26.2, [Fabric Loader](https://fabricmc.net/) 0.19.3+, Fabric API.
2. Put `brickbybricktab-0.1.0.jar` in `mods/`.

Optional custom weights:

```
<game>/config/brickbybricktab/model/model.bbbt
<game>/config/brickbybricktab/model/palette.json
```

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

## Settings that stay in the file

The menu only shows things you actually change while playing. These still exist in `config/brickbybricktab/config.json` if you need them:

- `refreshDelayTicks`, `previewDepth`, `bulkChangeThreshold`
- `debugOverlay`
- LoRA knobs: `loraStrength`, `loraTrainEveryPlacements`, `loraScope`, rank, learning rate
- `captionCollectLocally`
- `telemetryEndpoint`, `captionEndpoint`, `modelOverridePath`

## Build

JDK 25. From the repo root:

```
./gradlew build
```

On Windows: `.\gradlew.bat build`. The jar lands in `build/libs/`.

## License

MIT. Author: BRICK. See [LICENSE](LICENSE).
