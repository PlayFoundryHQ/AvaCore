#!/bin/bash
set -e

# ==============================================================================
# AvaCore Professional Asset Provisioner - HIGH QUALITY EDITION (Float32)
# Bundles Persian (the heart of it) into the APK. English and Swedish are NOT
# bundled — AvaTtsService's ModelDownloader fetches them on-device the first
# time they're actually requested, so the install stays small. See PLAN.md
# Phase 2 item 5.
# ==============================================================================

PROJECT_ROOT="."
LIBS_DIR="$PROJECT_ROOT/app/libs"
ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets/tts"
RELEASE="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

# 1. Sherpa-ONNX Core Engine (AAR) - Stable v1.10.41
AAR_URL="https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-1.10.41.aar"

# 2. Bundled voice models (Full Quality Float32). Format per entry:
#   "<lang code>|<bundle slug>|<onnx basename inside the bundle>"
# Keep this in sync with VoiceRegistry.kt's bundledInApk=true entries — only
# Persian belongs here. English/Swedish use the same slug/basename convention
# in VoiceRegistry (bundleSlug/onnxBasename) but are fetched at runtime by
# ModelDownloader instead of by this script.
VOICES=(
  "fa|vits-piper-fa_IR-gyro-medium|fa_IR-gyro-medium"
)

# 3. Linguistic Phonemizer Data (eSpeak-NG) — shared across every language.
ESPEAK_DATA_URL="$RELEASE/espeak-ng-data.tar.bz2"

echo "🚀 Starting Professional HIGH-QUALITY Asset Provisioning..."

mkdir -p "$LIBS_DIR" "$ASSETS_DIR"

# --- PART 1: Core Engine ---
echo "📥 Syncing Sherpa-ONNX Engine..."
curl -L "$AAR_URL" -o "$LIBS_DIR/sherpa-onnx.aar" --progress-bar

# --- PART 2: Bundled voice models ---
for entry in "${VOICES[@]}"; do
    IFS='|' read -r LANG SLUG BASENAME <<< "$entry"
    MODEL_URL="$RELEASE/$SLUG.tar.bz2"

    echo "📥 Syncing $LANG neural model ($SLUG)..."
    if ! curl -L -f "$MODEL_URL" -o "$ASSETS_DIR/bundle_$LANG.tar.bz2" --progress-bar; then
        echo "⚠️  Could not fetch $SLUG — skipping '$LANG'. AvaCore will run without it until you re-run this script."
        rm -f "$ASSETS_DIR/bundle_$LANG.tar.bz2"
        continue
    fi

    mkdir -p "$ASSETS_DIR/tmp_$LANG"
    tar -xjf "$ASSETS_DIR/bundle_$LANG.tar.bz2" -C "$ASSETS_DIR/tmp_$LANG"

    EXTRACTED_DIR=$(find "$ASSETS_DIR/tmp_$LANG" -maxdepth 1 -type d -name "$SLUG" | head -n 1)
    if [ -z "$EXTRACTED_DIR" ]; then
        # A few older bundles extract without the wrapper directory.
        EXTRACTED_DIR="$ASSETS_DIR/tmp_$LANG"
    fi

    if [ -f "$EXTRACTED_DIR/$BASENAME.onnx" ] && [ -f "$EXTRACTED_DIR/tokens.txt" ]; then
        # Use the raw .onnx (Float32) for full quality — same convention as the
        # original Persian-only script.
        mv "$EXTRACTED_DIR/$BASENAME.onnx" "$ASSETS_DIR/model_$LANG.onnx"
        mv "$EXTRACTED_DIR/tokens.txt" "$ASSETS_DIR/tokens_$LANG.txt"
        echo "💎 '$LANG' provisioned ($(ls -lh "$ASSETS_DIR/model_$LANG.onnx" | awk '{print $5}'))."
    else
        echo "❌ ERROR: expected files not found for '$LANG' — skipping. Contents were:"
        find "$EXTRACTED_DIR" -maxdepth 1
    fi

    rm -rf "$ASSETS_DIR/tmp_$LANG" "$ASSETS_DIR/bundle_$LANG.tar.bz2"
done

# --- PART 3: eSpeak-NG Data ---
if [ ! -d "$ASSETS_DIR/espeak-ng-data" ]; then
    echo "📥 Syncing eSpeak-NG Data..."
    curl -L -f "$ESPEAK_DATA_URL" -o "$ASSETS_DIR/espeak-ng-data.tar.bz2" --progress-bar
    tar -xjf "$ASSETS_DIR/espeak-ng-data.tar.bz2" -C "$ASSETS_DIR"
    rm "$ASSETS_DIR/espeak-ng-data.tar.bz2"
fi

echo ""
echo "✅ HIGH-QUALITY Provisioning Complete."
echo "--------------------------------------------------------"
echo "Engine: $(ls -lh "$LIBS_DIR/sherpa-onnx.aar" 2>/dev/null || echo 'MISSING')"
for entry in "${VOICES[@]}"; do
    IFS='|' read -r LANG _ _ <<< "$entry"
    if [ -f "$ASSETS_DIR/model_$LANG.onnx" ]; then
        echo "Model [$LANG]: $(ls -lh "$ASSETS_DIR/model_$LANG.onnx")"
    else
        echo "Model [$LANG]: not provisioned"
    fi
done
echo "English/Swedish: not bundled — fetched on-device on first use (ModelDownloader)."
echo "--------------------------------------------------------"
