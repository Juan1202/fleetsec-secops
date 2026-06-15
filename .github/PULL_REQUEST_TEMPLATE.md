<!--
PR Template — FleetSec SecOps
Cumple los gates inviolables del proyecto: Conventional Commits, AI disclosure, security checklist.
-->

## 📝 Resumen

<!-- 2-3 oraciones: qué cambia y por qué. Referencia al Jira issue (FSEC-XX). -->

Refs: FSEC-XX

---

## 🔗 Tipo de cambio

- [ ] `feat` — Nueva funcionalidad
- [ ] `fix` — Bug fix
- [ ] `fix(security)` — Remediación de vulnerabilidad VAPT (referenciar `V-XX`)
- [ ] `docs` — Solo documentación
- [ ] `chore` — Config, dependencias
- [ ] `ci` — Pipeline
- [ ] `refactor` — Sin cambio funcional
- [ ] `test` — Solo tests

---

## ✅ Checklist obligatorio

### Calidad de código
- [ ] Conventional Commits respetado en TODOS los commits del PR (commitlint debe pasar)
- [ ] Tests agregados o actualizados donde corresponda
- [ ] Documentación actualizada en el mismo PR (README/ADR/skill notes)
- [ ] No hay `// TODO` ni `console.log` ni `print()` debug en código de producción

### Seguridad
- [ ] Pipeline GitHub Actions verde sobre este PR (todos los stages)
- [ ] Si introduce algún hallazgo nuevo SAST/SCA/DAST/IaC, está documentado o suprimido con formato canónico (Rule/Reason/Date/Responsible/Review-by)
- [ ] No se introdujeron secretos (gitleaks debe pasar)
- [ ] Si tocó `terraform/`, se corrió `terraform validate` + `checkov` localmente
- [ ] Si remedia vulnerabilidad VAPT: incluye **test dual** (rechaza payload + valida flujo legítimo)
- [ ] Si toca paths en CODEOWNERS de seguridad, hay 2 reviewers aprobados

### IA disclosure (mandatorio)
- [ ] ¿Se usó IA generativa para este cambio? Si sí, agregué la entrada en [`docs/ai-report.md`](../docs/ai-report.md)
- [ ] Si la IA generó comandos, scripts de seguridad, o código sensible: revisé manualmente cada línea

### Datos personales (Ley 1581)
- [ ] Si el cambio toca datos de conductores/usuarios: pasa por checklist de PII
- [ ] Logs no contienen PII (cédula, email completo, teléfono) — revisado manualmente

---

## 🧪 Cómo testear

<!-- Pasos exactos para que el reviewer reproduzca el cambio. Incluye curl o screenshots si aplica. -->

```bash
# ejemplo
docker compose up --build
curl -X POST http://localhost:3000/api/items -d '...'
```

---

## 📸 Evidencia / capturas

<!-- Screenshots, output de comandos, antes/después si aplica. -->

---

## 🔥 Break-glass / supresiones (si aplica)

<!--
Si este PR SUPRIME o REDUCE algún gate de seguridad, completar:
- Regla suprimida:
- Motivo justificado:
- Fecha de revisión (≤ 90 días):
- Responsable:
- Aprobado por (segundo reviewer):

De lo contrario, dejar "N/A".
-->

N/A

---

## 📋 Referencias

- Jira: FSEC-XX
- Discusión previa: <link a Confluence/Slack si aplica>
- Docs externas: <RFC, CVE, vendor advisory>
