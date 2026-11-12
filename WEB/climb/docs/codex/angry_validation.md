# Angry jump sprites validation

Created using built-in image_gen. Generated sources are retained under the permitted CODEX_HOME/generated_images location. Final assets are in public/assets/characters/angry/.

## Checks

- All seven assets: 1024x1024 RGBA, four corner alpha values 0.
- Bottom and horizontal center measured from all nonzero-alpha pixels after cleanup. Bottom is y=900.
- Original front torso width: 802 px, measured in y=400..650, excluding limbs. Allowed 761.9..842.1 px.
- Torso widths use visually selected ROIs excluding hands/feet. Land, wall and splat have partly occluded contours, so their torso boundaries were manually estimated before raster measurement.
- Numeric width limit takes priority over exact area preservation during squash. Squash order: land > charge > splat in height.
- Alignment follows requested final check: full nontransparent silhouette including limbs, rather than torso-only center. Asymmetric wall pose therefore has a torso center offset.
- Only uniform resizing and integer translation were used for final geometric normalization. Alpha 1..7 background specks were cleared; no facial features were repainted in code.
- check_angry.png contains original + seven poses on gray, at equal canvas scale. Opened for visual review.
- All original PNG hashes matched. This task did not write any public/*.js. Concurrent changes were detected in public/main.js and public/physics.js and left untouched.

## Measurements

| File | Bottom y | Center x | Torso width px |
|---|---:|---:|---:|
| jump_charge.png | 900 | 512.5 | 832 |
| jump_rise.png | 900 | 511.5 | 764 |
| jump_apex.png | 900 | 512.0 | 800 |
| jump_fall.png | 900 | 512.0 | 775 |
| jump_land.png | 900 | 512.0 | 816 |
| jump_wall.png | 900 | 512.0 | 780 |
| jump_splat.png | 900 | 512.0 | 838 |

## Generation records

- Initial prompts: angry_prompts.json
- Revisions and generated paths: angry_revision_prompts.json
- Selected sources and measurement ROIs: angry_normalization.json
- Machine measurements: angry_measurements.json
