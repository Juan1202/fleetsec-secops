#!/usr/bin/env bash
# audit-suppressions.sh · FleetSec SecOps
#
# Audita las supresiones en .semgrepignore, .trivyignore, .gitleaks.toml, checkov.yml
# y reporta:
#   - Entradas que NO cumplen el formato canónico (5 campos obligatorios)
#   - Supresiones próximas a expirar (< 14 días desde Review-by)
#   - Supresiones expiradas (Review-by ya pasó)
#
# Exit code:
#   0 — todo OK
#   1 — entradas inválidas o expiradas detectadas (CI debe fallar)
#
# Uso:
#   ./scripts/audit-suppressions.sh
#   ./scripts/audit-suppressions.sh --strict   (también falla por warnings)

set -euo pipefail

STRICT=0
if [ "${1:-}" = "--strict" ]; then
  STRICT=1
fi

TODAY=$(date -u +%Y-%m-%d)
WARN_DAYS=14
EXIT_CODE=0
TOTAL=0
INVALID=0
EXPIRING=0
EXPIRED=0

# ANSI colors (degradan a vacío si no es tty)
if [ -t 1 ]; then
  RED='\033[0;31m'
  YELLOW='\033[0;33m'
  GREEN='\033[0;32m'
  BLUE='\033[0;34m'
  NC='\033[0m'
else
  RED=''; YELLOW=''; GREEN=''; BLUE=''; NC=''
fi

echo "🔍 Auditando supresiones de seguridad (fecha referencia: $TODAY)"
echo ""

# ──────────────────────────────────────────────────────────────────────────────
# Función: validar bloque de supresión
# Recibe: archivo + número de línea inicio
# Espera encontrar (en orden, dentro de los 5 comentarios previos):
#   # Rule: ...
#   # Reason: ...
#   # Date: YYYY-MM-DD
#   # Responsible: ...
#   # Review-by: YYYY-MM-DD
# ──────────────────────────────────────────────────────────────────────────────
validate_block() {
  local file=$1
  local block_text=$2

  local rule=$(echo "$block_text" | grep -oE '^# Rule: .+' | head -1 | sed 's/^# Rule: //')
  local reason=$(echo "$block_text" | grep -oE '^# Reason: .+' | head -1 | sed 's/^# Reason: //')
  local date_field=$(echo "$block_text" | grep -oE '^# Date: [0-9]{4}-[0-9]{2}-[0-9]{2}' | head -1 | sed 's/^# Date: //')
  local responsible=$(echo "$block_text" | grep -oE '^# Responsible: .+' | head -1 | sed 's/^# Responsible: //')
  local review_by=$(echo "$block_text" | grep -oE '^# Review-by: [0-9]{4}-[0-9]{2}-[0-9]{2}' | head -1 | sed 's/^# Review-by: //')

  local missing=()
  [ -z "$rule" ] && missing+=("Rule")
  [ -z "$reason" ] && missing+=("Reason")
  [ -z "$date_field" ] && missing+=("Date")
  [ -z "$responsible" ] && missing+=("Responsible")
  [ -z "$review_by" ] && missing+=("Review-by")

  TOTAL=$((TOTAL + 1))

  if [ ${#missing[@]} -gt 0 ]; then
    INVALID=$((INVALID + 1))
    echo -e "${RED}❌ INVÁLIDA${NC} en $file"
    echo "   Campos faltantes: ${missing[*]}"
    echo "   Contenido del bloque:"
    echo "$block_text" | sed 's/^/      /'
    echo ""
    EXIT_CODE=1
    return
  fi

  # Calcular días hasta Review-by
  local days_remaining=$(python3 -c "
from datetime import date
review = date.fromisoformat('$review_by')
today = date.fromisoformat('$TODAY')
print((review - today).days)
" 2>/dev/null || echo "?")

  if [ "$days_remaining" = "?" ]; then
    echo -e "${YELLOW}⚠️  No se pudo parsear Review-by en $file:${NC} $review_by"
    return
  fi

  if [ "$days_remaining" -lt 0 ]; then
    EXPIRED=$((EXPIRED + 1))
    echo -e "${RED}❌ EXPIRADA${NC} hace $((-days_remaining)) días en $file"
    echo "   Rule: $rule"
    echo "   Reason: $reason"
    echo "   Date: $date_field · Responsible: $responsible · Review-by: $review_by"
    echo ""
    EXIT_CODE=1
  elif [ "$days_remaining" -le "$WARN_DAYS" ]; then
    EXPIRING=$((EXPIRING + 1))
    echo -e "${YELLOW}⚠️  EXPIRA EN $days_remaining DÍAS${NC} en $file"
    echo "   Rule: $rule"
    echo "   Responsible: $responsible · Review-by: $review_by"
    echo ""
    if [ "$STRICT" = "1" ]; then EXIT_CODE=1; fi
  else
    echo -e "${GREEN}✅ OK${NC} ($days_remaining días para Review-by) — $file"
    echo "   Rule: $rule · Responsible: $responsible"
    echo ""
  fi
}

# ──────────────────────────────────────────────────────────────────────────────
# Escanear archivos de supresión
# ──────────────────────────────────────────────────────────────────────────────
echo "📄 Escaneando archivos de supresión..."
echo ""

SUPPRESSION_FILES=(
  ".semgrepignore"
  ".trivyignore"
  ".gitleaks.toml"
  "checkov.yml"
  "terraform/checkov.yml"
)

for file in "${SUPPRESSION_FILES[@]}"; do
  if [ ! -f "$file" ]; then
    continue
  fi

  echo -e "${BLUE}── $file ──${NC}"

  # Extraer bloques: secuencia de líneas que empiezan con "# " seguidas por una línea no-comentario
  # Algoritmo simple: buscar líneas "# Rule:" y tomar las 5 líneas siguientes para validar
  while IFS= read -r line_num; do
    block=$(sed -n "${line_num},$((line_num + 4))p" "$file")
    validate_block "$file" "$block"
  done < <(grep -n '^# Rule:' "$file" | cut -d: -f1)
done

# ──────────────────────────────────────────────────────────────────────────────
# Resumen
# ──────────────────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Resumen de auditoría"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Total supresiones encontradas:  $TOTAL"
echo -e "  ${RED}Inválidas (formato):          $INVALID${NC}"
echo -e "  ${RED}Expiradas:                    $EXPIRED${NC}"
echo -e "  ${YELLOW}Próximas a expirar (<${WARN_DAYS}d):    $EXPIRING${NC}"
echo -e "  ${GREEN}Válidas y vigentes:           $((TOTAL - INVALID - EXPIRED - EXPIRING))${NC}"
echo ""

if [ "$EXIT_CODE" -ne 0 ]; then
  echo -e "${RED}❌ Auditoría FALLÓ — hay supresiones que requieren acción${NC}"
else
  echo -e "${GREEN}✅ Auditoría OK${NC}"
fi

exit $EXIT_CODE
