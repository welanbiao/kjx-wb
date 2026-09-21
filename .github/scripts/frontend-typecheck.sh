#!/usr/bin/env bash
set -euo pipefail

# Usage: frontend-typecheck.sh web|admin
target="${1:-}"
if [ "$target" != "web" ] && [ "$target" != "admin" ]; then
  echo "usage: $0 web|admin" >&2
  exit 2
fi

pkg_dir="apps/${target}"
workspace_pkg="@kjx/${target}"

if [ ! -f "${pkg_dir}/package.json" ]; then
  echo "::error::${pkg_dir}/package.json missing; this job should have been skipped"
  exit 1
fi

install_and_typecheck_dir() {
  local dir="$1"
  (
    cd "$dir"
    if [ -f pnpm-lock.yaml ]; then
      corepack enable
      pnpm install --frozen-lockfile
      if npm pkg get scripts.typecheck | grep -vq undefined; then
        pnpm run typecheck
      elif [ -f tsconfig.json ]; then
        pnpm exec tsc --noEmit
      else
        echo "::error::${dir} lockfile present but no typecheck script or tsconfig.json"
        exit 1
      fi
    elif [ -f package-lock.json ]; then
      npm ci
      if npm pkg get scripts.typecheck | grep -vq undefined; then
        npm run typecheck
      elif [ -f tsconfig.json ]; then
        npx tsc --noEmit
      else
        echo "::error::${dir} lockfile present but no typecheck script or tsconfig.json"
        exit 1
      fi
    elif [ -f yarn.lock ]; then
      corepack enable
      yarn install --frozen-lockfile
      if npm pkg get scripts.typecheck | grep -vq undefined; then
        yarn typecheck
      elif [ -f tsconfig.json ]; then
        yarn tsc --noEmit
      else
        echo "::error::${dir} lockfile present but no typecheck script or tsconfig.json"
        exit 1
      fi
    else
      echo "::error::${dir} has no lockfile"
      exit 1
    fi
  )
}

if [ -f "${pkg_dir}/pnpm-lock.yaml" ] || [ -f "${pkg_dir}/package-lock.json" ] || [ -f "${pkg_dir}/yarn.lock" ]; then
  install_and_typecheck_dir "$pkg_dir"
elif [ -f apps/package-lock.json ]; then
  cd apps
  npm ci
  npm run typecheck -w "$workspace_pkg"
elif [ -f apps/pnpm-lock.yaml ]; then
  cd apps
  corepack enable
  pnpm install --frozen-lockfile
  pnpm --filter "$workspace_pkg" run typecheck
elif [ -f apps/yarn.lock ]; then
  cd apps
  corepack enable
  yarn install --frozen-lockfile
  yarn workspace "$workspace_pkg" typecheck
else
  echo "::error::${target} job ran without a lockfile; detect job is wrong"
  exit 1
fi
