#!/usr/bin/env bash
# Creates the annotated local tags v0.1.0 ... v0.14.0 on the reconstructed milestone commits
# (see docs/VERSIONING.md). Existing tags are never overwritten.
# Usage: tools/versioning/create-tags.sh [--push]
# Without --push nothing leaves the machine. With --push only these tags are pushed to origin.
set -euo pipefail

push=0
case "${1:-}" in
  "") ;;
  --push) push=1 ;;
  *) echo "usage: $0 [--push]" >&2; exit 2 ;;
esac

cd "$(git rev-parse --show-toplevel)"

# version|full SHA|theme
versions=(
  "0.1.0|3ea12fbda13cd8148ea7ed34bb5c27a9ccaf20c4|Fork baseline: Moonlight 12.2 merge, Android 10 minimum, dead-code removal, Delos branding"
  "0.2.0|d42c805e8ca326a7c8374f93ba0fc8e739b5a061|Native Delos UI, profiles, settings, stream menu, SteamGridDB artwork"
  "0.3.0|ad44873390a0a8ce45194e428d8ca3d6d9e1f266|Palettes, backgrounds, home row and on-screen controls polish"
  "0.4.0|2bc3fa1c6a28d00f3c930cf21aae4258ab7d5f77|PyroWave codec"
  "0.5.0|fa06daeaed0cf9b9d0eaadfb9e7174c037046c29|Per-game settings, SGSR upscaling, external display, help panels, Phosphor icons"
  "0.6.0|c44d9e1de8588f0cad34cc7a2ff1fb54f3bcd838|Game metadata, library filters, HDR badges, launcher icon"
  "0.7.0|744b5f18f20d3c2f210c86bcedd1661a8bda9b79|Illustrated avatars, quit and exit flow, launch transition"
  "0.8.0|547438bb5e57b6ed31b4cb72878c0402cfcfa366|Hero art, artwork page redesign, PyroWave 4:4:4, Sharpen only, settings motion"
  "0.9.0|a763ea00313643b3a81a7c3de18043b409996bce|Link guidance, latency work, SGSR verification, per-game settings redesign"
  "0.10.0|51626aa17d38c32fcfc02cf375d764b3e5e84df4|Gyro smoothing, context menu redesign, Game activity refactor begins"
  "0.11.0|a5c6b5b930b9d3599d2e11914f3400deda0d4f32|Stream defect fixes, bundled app art, diagnostics and problem reports"
  "0.12.0|40a3eadaaba8fa154ccec9866de6a03f61de5d9e|Localization in ten languages"
  "0.13.0|7f5882e6e994d74a9c70baa39a3183e25f30a465|Screen shapes, dual screen with companion pages"
  "0.14.0|54be0b6d8967921e54d89cc8a55494f35402f1d7|Variable refresh rate, new palettes and avatars, UI sounds, rumble fixes"
)

created=()
for entry in "${versions[@]}"; do
  IFS='|' read -r version sha theme <<<"$entry"
  tag="v$version"
  git cat-file -e "$sha^{commit}" 2>/dev/null || { echo "missing commit for $tag: $sha" >&2; exit 1; }
  if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    existing=$(git rev-parse "$tag^{commit}")
    if [ "$existing" = "$sha" ]; then
      echo "$tag exists on $sha, skipped"
    else
      echo "$tag exists on $existing, expected $sha; refusing to overwrite" >&2
      exit 1
    fi
  else
    git tag -a "$tag" "$sha" -m "Delos $version: $theme"
    echo "$tag -> $sha"
  fi
  created+=("refs/tags/$tag")
done

if [ "$push" = 1 ]; then
  git push origin "${created[@]}"
else
  echo "Local tags only. Push with: $0 --push"
fi
