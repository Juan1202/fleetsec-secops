#!/usr/bin/env bash
# setup-github.sh · FleetSec SecOps
#
# Crea el repo en GitHub vía gh CLI, hace el primer push del scaffolding,
# y aplica la branch protection sobre `main` inmediatamente después.
#
# Ver ADR-001 (docs/ADRs/ADR-001-branch-protection-from-day-zero.md) para el rationale.
#
# Pre-requisitos:
#   - gh CLI instalado y autenticado: `gh auth login`
#   - Estar dentro del directorio del repo local (con scaffolding ya construido)
#   - Variables abajo configuradas
#
# Uso:
#   ./scripts/setup-github.sh
#
# Idempotente: re-correr es seguro (re-aplica protección sin recrear el repo).

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# Configuración (editar antes de correr)
# ──────────────────────────────────────────────────────────────────────────────
GH_OWNER="${GH_OWNER:-Juan1202}"               # tu username o org
REPO_NAME="${REPO_NAME:-fleetsec-secops}"
VISIBILITY="${VISIBILITY:-private}"                  # private | public
DESCRIPTION="FleetSec SecOps · Prueba Técnica Ingeniero Ciberseguridad — Juan Andrés Moya"
DEFAULT_BRANCH="main"

# Branch protection settings
REQUIRED_REVIEWS=2
REQUIRE_CODE_OWNER_REVIEWS=true
DISMISS_STALE_REVIEWS=true
REQUIRE_LINEAR_HISTORY=true
ALLOW_FORCE_PUSHES=false
ALLOW_DELETIONS=false
# Status checks que deben pasar antes de merge (se agregan al final del Sprint 1
# cuando el workflow security.yml exista; vacío por ahora)
REQUIRED_STATUS_CHECKS=()

# ──────────────────────────────────────────────────────────────────────────────
# Validaciones
# ──────────────────────────────────────────────────────────────────────────────
echo "🔍 Verificando pre-requisitos..."

if ! command -v gh >/dev/null 2>&1; then
  echo "❌ gh CLI no instalado. https://cli.github.com/"
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "❌ gh CLI no autenticado. Corre: gh auth login"
  exit 1
fi

if [ ! -f "README.md" ] || [ ! -d ".github" ]; then
  echo "❌ No parece estar en el directorio raíz del repo (falta README.md o .github/)."
  echo "   cd al directorio que contiene el scaffolding antes de correr este script."
  exit 1
fi

if ! git rev-parse --git-dir >/dev/null 2>&1; then
  echo "ℹ️  git no inicializado. Inicializando..."
  git init -b "$DEFAULT_BRANCH"
fi

echo "✅ Pre-requisitos OK"
echo ""

# ──────────────────────────────────────────────────────────────────────────────
# Paso 1: Crear repo (si no existe)
# ──────────────────────────────────────────────────────────────────────────────
echo "📦 Paso 1: Crear/verificar repo $GH_OWNER/$REPO_NAME..."

if gh repo view "$GH_OWNER/$REPO_NAME" >/dev/null 2>&1; then
  echo "ℹ️  El repo ya existe. Saltando creación."
else
  gh repo create "$GH_OWNER/$REPO_NAME" \
    --"$VISIBILITY" \
    --description "$DESCRIPTION" \
    --disable-issues=false \
    --disable-wiki=true
  echo "✅ Repo $GH_OWNER/$REPO_NAME creado"
fi

# Set remote si no existe
if ! git remote get-url origin >/dev/null 2>&1; then
  git remote add origin "git@github.com:$GH_OWNER/$REPO_NAME.git"
  echo "✅ Remote origin agregado"
else
  echo "ℹ️  Remote origin ya configurado"
fi

echo ""

# ──────────────────────────────────────────────────────────────────────────────
# Paso 2: Primer commit + push (si no hay commits aún)
# ──────────────────────────────────────────────────────────────────────────────
echo "📤 Paso 2: Primer commit + push..."

if [ -z "$(git log --oneline 2>/dev/null)" ]; then
  echo "ℹ️  Repo local sin commits. Creando commit inicial..."

  # Instalar deps de Node (Husky + commitlint) si hay internet
  if command -v npm >/dev/null 2>&1; then
    npm install --no-audit --no-fund || echo "⚠️  npm install falló, continúa sin Husky local"
  fi

  git add -A
  GIT_AUTHOR_NAME="${GIT_AUTHOR_NAME:-Juan Andrés Moya}" \
  GIT_AUTHOR_EMAIL="${GIT_AUTHOR_EMAIL:-juan.andres@fleetsec.co}" \
  GIT_COMMITTER_NAME="${GIT_COMMITTER_NAME:-Juan Andrés Moya}" \
  GIT_COMMITTER_EMAIL="${GIT_COMMITTER_EMAIL:-juan.andres@fleetsec.co}" \
  git commit -m "chore(scaffold): initial repository scaffolding

- README, LICENSE, .gitignore, .editorconfig
- .github/CODEOWNERS + PULL_REQUEST_TEMPLATE.md + workflows placeholder
- Husky pre-commit (gitleaks) + commit-msg (commitlint)
- commitlint.config.js con preset Conventional Commits + scopes proyecto
- .gitleaks.toml con extend default + allowlist documentado
- Estructura: app/ pipeline/ vapt/ terraform/ ir/ docs/{ADRs,architecture}/
- docs/ai-report.md (template inicial, FSEC-11)
- docs/break-glass.md (placeholder Sprint 1)
- docs/suppression-policy.md (formato canónico)
- docs/ADRs/ADR-001 (branch protection from day zero)

Refs: FSEC-10, FSEC-11
"

  git push -u origin "$DEFAULT_BRANCH"
  echo "✅ Primer commit pusheado a origin/$DEFAULT_BRANCH"
else
  echo "ℹ️  El repo local ya tiene commits. Empujando solo cambios pendientes (si hay)..."
  git push origin "$DEFAULT_BRANCH" || true
fi

echo ""

# ──────────────────────────────────────────────────────────────────────────────
# Paso 3: Aplicar branch protection sobre main
# ──────────────────────────────────────────────────────────────────────────────
echo "🔒 Paso 3: Aplicando branch protection sobre $DEFAULT_BRANCH..."

# Construir JSON de status_checks
if [ ${#REQUIRED_STATUS_CHECKS[@]} -eq 0 ]; then
  STATUS_CHECKS_JSON="null"
else
  CHECKS_QUOTED=$(printf '"%s",' "${REQUIRED_STATUS_CHECKS[@]}")
  CHECKS_QUOTED="${CHECKS_QUOTED%,}"
  STATUS_CHECKS_JSON="{\"strict\":true,\"contexts\":[$CHECKS_QUOTED]}"
fi

# Aplicar branch protection vía gh api
gh api -X PUT "repos/$GH_OWNER/$REPO_NAME/branches/$DEFAULT_BRANCH/protection" \
  -H "Accept: application/vnd.github+json" \
  --input - <<EOF
{
  "required_status_checks": $STATUS_CHECKS_JSON,
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": $REQUIRED_REVIEWS,
    "require_code_owner_reviews": $REQUIRE_CODE_OWNER_REVIEWS,
    "dismiss_stale_reviews": $DISMISS_STALE_REVIEWS,
    "require_last_push_approval": true
  },
  "restrictions": null,
  "required_linear_history": $REQUIRE_LINEAR_HISTORY,
  "allow_force_pushes": $ALLOW_FORCE_PUSHES,
  "allow_deletions": $ALLOW_DELETIONS,
  "required_conversation_resolution": true,
  "lock_branch": false,
  "allow_fork_syncing": false
}
EOF

echo "✅ Branch protection aplicada"
echo ""

# ──────────────────────────────────────────────────────────────────────────────
# Paso 4: Verificación
# ──────────────────────────────────────────────────────────────────────────────
echo "🔍 Paso 4: Verificación..."

PROTECTION=$(gh api "repos/$GH_OWNER/$REPO_NAME/branches/$DEFAULT_BRANCH/protection" 2>/dev/null || echo "{}")

if echo "$PROTECTION" | grep -q '"required_approving_review_count"'; then
  REVIEWS=$(echo "$PROTECTION" | python3 -c "import sys,json; print(json.load(sys.stdin)['required_pull_request_reviews']['required_approving_review_count'])" 2>/dev/null || echo "?")
  echo "✅ Required PR reviews: $REVIEWS"
else
  echo "⚠️  No se pudo confirmar branch protection (verificar manualmente en GitHub UI)"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 Setup completado"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📦 Repo: https://github.com/$GH_OWNER/$REPO_NAME"
echo "🔒 Branch protection: aplicada sobre $DEFAULT_BRANCH"
echo "   - Require PR review: $REQUIRED_REVIEWS approvers"
echo "   - Require CODEOWNERS: $REQUIRE_CODE_OWNER_REVIEWS"
echo "   - Allow force push: $ALLOW_FORCE_PUSHES"
echo "   - Allow deletions: $ALLOW_DELETIONS"
echo ""
echo "📋 Próximos pasos:"
echo "   1. Verificar en GitHub UI: Settings > Branches > main"
echo "   2. Probar el bloqueo: crear PR de prueba e intentar mergear sin review"
echo "   3. Avanzar a Sprint 1 (FSEC-12 + FSEC-13)"

