#!/usr/bin/env bash
# QS-15 (step 12, increment 2): a breaking change in either approved spec fails the build; an additive
# one passes. The base is each spec as it is on <base-ref> (the pull request's base branch).
# Usage: tools/contract/spec-compatibility.sh <base-ref>     (oasdiff on PATH, or OASDIFF=<binary>)
#
# asyncapi-diff-overrides.json: the AsyncAPI diff's standard marks any edit of /info/version as
# breaking, so every version bump would fail; the version number is not part of the contract.
# Every other rule of the standard stays as it is.
#
# oasdiff-err-ignore.txt: breaking changes decided as exceptions (ADR0014 addition, step 14 of
# increment 2), one line per operation and property, each matching exactly one oasdiff error.
# SEC-20: SubstituteIngredient.value gets maximum 10,000 (REST answer and Grandma's message). Once the
# change is on main the base carries it and the line matches nothing; remove it with the next change.
set -euo pipefail

base_ref="$1"
oasdiff="${OASDIFF:-oasdiff}"
openapi="docs/cooking-assistance/api/cooking-assistance.openapi.yaml"
asyncapi="docs/cooking-assistance/api/cooking-assistance.asyncapi.yaml"
root="$(pwd)"
ignore="$root/tools/contract/oasdiff-err-ignore.txt"
base="$(mktemp -d)"
trap 'rm -rf "$base"' EXIT

git show "$base_ref:$openapi" > "$base/openapi.yaml"
git show "$base_ref:$asyncapi" > "$base/asyncapi.yaml"

echo "OpenAPI: $base_ref -> working tree"
"$oasdiff" breaking "$base/openapi.yaml" "$openapi" --fail-on ERR --err-ignore "$ignore"

echo "AsyncAPI document (channels, operations, messages): $base_ref -> working tree"
npx --yes @asyncapi/cli@6.2.2 diff "$base/asyncapi.yaml" "$root/$asyncapi" \
  --type breaking --overrides "$root/tools/contract/asyncapi-diff-overrides.json"

# the AsyncAPI diff classifies payload-schema changes as "unclassified"; oasdiff judges them,
# sent messages as responses and received ones as request bodies
echo "AsyncAPI payloads: $base_ref -> working tree"
python3 -I tools/contract/asyncapi_as_openapi.py "$base/asyncapi.yaml" "$base/messages-base.yaml"
python3 -I tools/contract/asyncapi_as_openapi.py "$asyncapi" "$base/messages-head.yaml"
"$oasdiff" breaking "$base/messages-base.yaml" "$base/messages-head.yaml" --fail-on ERR \
  --err-ignore "$ignore"
