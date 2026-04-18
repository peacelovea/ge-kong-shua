#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS_DIR="$ROOT_DIR/app/src/main/assets"
MODEL_NAME="vosk-model-small-cn-0.22"
MODEL_ZIP="$MODEL_NAME.zip"
MODEL_URL="https://alphacephei.com/vosk/models/$MODEL_ZIP"

mkdir -p "$ASSETS_DIR"

if [[ -d "$ASSETS_DIR/$MODEL_NAME" ]]; then
  echo "Model already installed at $ASSETS_DIR/$MODEL_NAME"
  exit 0
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Downloading $MODEL_URL ..."
curl -L -o "$TMP_DIR/$MODEL_ZIP" "$MODEL_URL"

echo "Unzipping to $ASSETS_DIR ..."
unzip -q "$TMP_DIR/$MODEL_ZIP" -d "$ASSETS_DIR"

# Vosk's StorageService.unpack() requires a `uuid` file to do version tracking;
# the upstream model zip doesn't ship one, so we create a stable marker.
echo "shower-voice-ctrl-v1" > "$ASSETS_DIR/$MODEL_NAME/uuid"

echo "Done. Model installed at $ASSETS_DIR/$MODEL_NAME"
