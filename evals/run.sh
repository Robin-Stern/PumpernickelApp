#!/usr/bin/env bash
# Runs the full eval pipeline: prepare prompts → workout suite → recipe suite → open viewer.
set -euo pipefail

cd "$(dirname "$0")"

if [[ -z "${TOGETHER_API_KEY:-}" ]]; then
  echo "❌ TOGETHER_API_KEY not set. export it first:" >&2
  echo "   export TOGETHER_API_KEY=<your-key>" >&2
  exit 1
fi

if [[ ! -d node_modules ]]; then
  echo "📦 Installing promptfoo (first run only)…"
  npm install
fi

echo "🛠  Preparing prompts…"
npm run prepare --silent

echo "🏋️  Workout eval…"
npx --yes promptfoo eval -c promptfoo.workout.yaml

echo "🥗 Recipe eval…"
npx --yes promptfoo eval -c promptfoo.recipe.yaml

echo "📊 Opening viewer (Ctrl+C to stop)…"
npx --yes promptfoo view
