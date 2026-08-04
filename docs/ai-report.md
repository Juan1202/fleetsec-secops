# AI Report · FleetSec SecOps

> **Documento mandatorio (Entregable 05).** Arriba, el **reporte formal ≤150 palabras** (3 secciones,
> con una alucinación real detectada y corregida). Debajo, el **anexo extendido** que lo respalda.
> Se actualiza incrementalmente en cada PR que use IA.

---

## Reporte formal (≤150 palabras)

**Herramientas y tareas.** Claude Code (Anthropic) asistió el scaffolding, las fichas VAPT en
español, el módulo Terraform `security-baseline`, las reglas Sigma, el playbook de IR y esta
documentación. La sintaxis AWS CLI se verificó contra aws-cli v2; las reglas Sigma con `sigma check`;
el DOCX se generó con `python-docx`.

**Una alucinación detectada y corregida.** Al redactar la regla Sigma de exfiltración masiva (S3
GetObject), Claude propuso una agregación temporal (`count() by … within 5m`) como si fuera sintaxis
Sigma *core*. No lo es: la agregación es una extensión específica del backend. Se detectó contra la
especificación de SigmaHQ y con `sigma check`, y se corrigió usando una regla de correlación
`event_count` válida más el SPL real de Splunk.

**Bajo control humano.** Severidad CVSS, citas de la Ley 1581, supresiones de gates y merges de PR
quedan como decisión humana: la IA propone, el autor valida y commitea.

> _146 palabras · 3 secciones (verificable con `wc -w`)._

---

# Anexo — detalle extendido

## 1. Tools y tareas

### 1.1 IA generativa principal

**Claude.ai** (proyecto principal: `juanandres-secops-skills` + sesiones interactivas)

Tareas delegadas:
- **Skills personalizados** (10 SKILL.md, 3 rules, 4 slash commands) usados como base de conocimiento autorizada para cada sub-tarea de seguridad
- **Drafting de reglas Sigma** (4 reglas con MITRE ATT&CK mapping)
- **Plantillas Terraform** para módulo `security-baseline` (estructura inicial, no values production)
- **Fichas VAPT en español** (estructura plantilla por finding, no contenido de evidencia específica del sistema)
- **Generación DOCX** del reporte VAPT vía `python-docx` (script self-contained `vapt/reports/generate-report.py`)
- **Documentación**: README, ADRs, diagramas Mermaid, instrucciones de uso
- **Refactoring de markdown** entre formatos (Confluence storage HTML, ADF, Jira description)
- **Validación de JSON Schema** para `plugin.json` y outputs estructurados

### 1.2 IA generativa secundaria

**ChatGPT** (uso puntual, no sistemático)
- Sanity-check de comandos AWS CLI específicos (verificar sintaxis contra docs oficiales antes de incluir en `ir/playbook.md`)
- Cross-check ocasional de respuestas críticas de Claude cuando la confianza era baja

### 1.3 Herramientas asistidas por IA (no generativas)

- **GitHub Copilot** en VS Code para autocompletado durante desarrollo
- **Semgrep custom rules**: Claude propuso patrones, validación humana antes de incluir en pipeline

---

## 2. Una alucinación detectada y corregida

### Contexto
Durante el drafting de la regla Sigma para detectar bulk exfiltration vía `s3:GetObject` (Sprint 4, FSEC-25, regla `03-bulk-s3-getobject-exfil`), Claude propuso el siguiente snippet como si fuera **sintaxis core de Sigma**:

```yaml
detection:
  selection:
    eventSource: s3.amazonaws.com
    eventName: GetObject
  timeframe: 5m
  condition: selection | count() by userIdentity.arn, requestParameters.bucketName > 100
```

### El problema
La sintaxis con pipe (`|`) seguido de agregación (`count() by ... within Xm`) **no es parte del estándar Sigma core**. Es una **extensión backend-specific** soportada por:
- Splunk SPL conversions
- Microsoft Sentinel KQL conversions
- Algunas configuraciones de Sigmac/pySigma con backends específicos

### Cómo se detectó
Validación contra la **spec oficial Sigma v2.0** en `SigmaHQ/sigma-specification` ([repo oficial](https://github.com/SigmaHQ/sigma-specification)):
- El estándar Sigma describe `detection.selection.*` y `detection.condition` con operadores booleanos (`and`, `or`, `not`)
- **No define agregaciones temporales** ni operadores `count() by ... within`
- Las agregaciones existen en `pySigma` como pipeline transformations específicas por backend

### Cómo se corrigió
1. **Documenté el caveat** explícitamente en la skill `threat-detection-sigma`:
   > "Aggregation con `count()`/`avg()`/`sum()` por tiempo NO es estándar Sigma — requiere backend específico (Splunk, Sentinel, ELK + ESQL)."

2. **Elegí Splunk como target backend explícito** para esta regla específica y la convertí a SPL real:
   ```spl
   index=cloudtrail sourcetype=aws:cloudtrail eventSource="s3.amazonaws.com" eventName="GetObject"
   | stats count by userIdentity.arn, requestParameters.bucketName
   | where count > 100
   ```

3. **Regla Sigma válida con correlación:** en `ir/detections/03-bulk-s3-getobject-exfil.yml` dejé una
   regla base (matching de `GetObject` sobre el bucket) + una **regla de correlación `type: event_count`**
   (>100 en 5 min por principal/bucket) — la forma correcta que `sigma check` valida (0 errors). El SPL
   real vive en el `.spl` companion.

4. **Lección capturada** en la skill `threat-detection-sigma` y en la ficha/regla misma: cuando un
   detector requiere correlación temporal, usar una regla de correlación de Sigma (o declarar el
   backend target) en vez de pretender que la agregación es portable. *(No se creó un ADR dedicado;
   la lección vive junto al artefacto.)*

---

## 3. Tareas NO delegadas a IA sin supervisión

Las siguientes decisiones / acciones se mantienen explícitamente bajo control humano. La IA puede *proponer*, pero la decisión final y el commit van por humano:

### 3.1 Severidad y riesgo finales
- **Por qué**: las IAs tienden a inflar severidad (sesgo de cautela). En CVSSv3.1 cada métrica afecta el score; calibrar es trabajo de juicio que requiere contexto de negocio.
- **Práctica**: el vector CVSS propuesto por IA se contrasta con casos reales del mismo CWE y se ajusta según attack complexity, scope, y privilegios reales del sistema FleetSec.

### 3.2 Ejecución de AWS CLI sobre cuentas reales
- **Por qué**: una mala sintaxis o una flag wrong (`--region us-west-2` en vez de `us-east-1`) puede afectar producción. La IA ha generado comandos plausibles que en realidad no existen (ej. `aws cloudtrail enable-log-validation` — el comando correcto es `aws cloudtrail update-trail --enable-log-file-validation`).
- **Práctica**:
  1. `--dry-run` obligatorio donde el comando lo soporta
  2. Revisión manual línea por línea contra docs oficiales AWS
  3. Pre-run en cuenta sandbox antes de cualquier ejecución sobre prod
  4. Para IR: comandos van en playbook (`ir/playbook.md`) con **rollback** documentado debajo de cada bloque

### 3.3 Clasificación de PII bajo Ley 1581
- **Por qué**: la clasificación de un dato como "sensible" según Ley 1581 (Art. 5) tiene implicaciones legales directas. Confundir un dato "personal" con "sensible" o viceversa cambia:
  - Obligaciones de tratamiento (Art. 6)
  - Necesidad de autorización expresa
  - Si aplica el régimen de transferencia internacional (Art. 26)
- **Práctica**: en cada finding VAPT que involucre PII (V08 obligatorio, V01/V09 condicional), la clasificación se hace manualmente con cita explícita al artículo aplicable.

### 3.4 Supresiones de gates de seguridad
- **Por qué**: una supresión silenciosa es deuda invisible. El gate fue diseñado para fallar — saltarlo requiere justificación auditable.
- **Práctica**:
  - Cada supresión en `.semgrepignore`, `.trivyignore`, `checkov.yml`, `.gitleaks.toml` y `terraform/checkov.yml` requiere los **5 campos canónicos** (Rule, Reason, Date, Responsible, Review-by)
  - CI gate `Suppressions Audit` valida los campos obligatorios y falla el build si faltan (FSEC-15)
  - Job semanal abre Issue si una supresión está a < 14 días de expirar
  - El humano que aprueba la supresión queda registrado (no es la IA)

### 3.5 Selección de stack para app vulnerable
- **Por qué**: la decisión técnica involucra trade-offs (tiempo vs aprendizaje vs cobertura) que dependen del background del autor y del juicio sobre qué demostrar al evaluador. **ADR-003** documenta la decisión.

### 3.6 Aprobaciones de PR con cambios de seguridad
- **Por qué**: CODEOWNERS exige human review. La IA puede ayudar a redactar el comentario de review, pero el "Approve" lo da un humano que entiende el contexto del repo y el blast radius del cambio.

---

## 4. Métricas de uso de IA (running tally)

| Sprint | % AI-generated (lines) | % Human-modified post-AI | Validación |
|---|---|---|---|
> Tally cualitativo (no se instrumentó conteo exacto de líneas). El patrón es constante: la IA
> genera el andamiaje/borrador, el humano calibra lo que tiene consecuencias.

| Sprint | Rol de la IA | Bajo control humano (calibración) |
|---|---|---|
| Sprint 0 | Scaffolding, skills, cronograma | CODEOWNERS, reglas gitleaks custom |
| Sprint 1 | Boilerplate del pipeline, app vulnerable | Política del gate (ADR-004), reglas Semgrep |
| Sprint 2 | Estructura de fichas VAPT, DOCX | Scoring CVSS, evidencia de PoC, análisis de interacción |
| Sprint 3 | Módulo Terraform, tabla de cumplimiento | Supresiones canónicas, citas Ley 1581 por artículo |
| Sprint 4 | Draft del playbook y reglas Sigma | Verificación AWS CLI (aws-cli v2), orden de containment |
| Sprint 5 | README, diagramas, este reporte | La narrativa de sistema y la corrección de inexactitudes |

---

## 5. Cambios incrementales

> Cada PR significativo que use IA debe agregar una entrada aquí.

### 2026-06-15 · Sprint 0 — Fundación
- Skills repo, cronograma, Jira FSEC (6 epics + 25 historias), scaffolding — generados con Claude, revisados línea por línea.

### 2026-08 · Sprints 1–5
- **S1 (pipeline/app):** 9 stages + app vulnerable; el gate y las reglas Semgrep se calibraron a mano (ADR-004).
- **S2 (VAPT):** 11 fichas + 2 bonus + DOCX; CVSS y PoC verificados por el autor. **Aquí se detectó y corrigió la alucinación de agregación Sigma** (§2).
- **S3 (Terraform):** módulo `security-baseline` + COMPLIANCE; supresiones canónicas y citas Ley 1581 revisadas.
- **S4 (IR):** playbook + Sigma + RCA; AWS CLI verificado contra aws-cli v2, orden de containment (evidencia antes de destruir) definido por el autor.
- **S5 (docs):** README, diagramas Mermaid, ADRs, este reporte; la narrativa de sistema y la limpieza de inexactitudes (este mismo PR) son trabajo humano.

---

## 6. Notas finales

- Este documento es **parte del entregable 05** (Documentación), sujeto a revisión por el evaluador.
- El **reporte formal ≤150 palabras** vive arriba en este documento y se refleja en el README (§AI Report).
- **Claiming zero AI errors does not score** — se incluye intencionalmente una alucinación real con su corrección documentada y verificable (`sigma check`).
- Histórico via `git log docs/ai-report.md` (Conventional Commits — `docs(ai-report): ...`).

---

*Última actualización: 2026-08-04 · Autor: Juan Andrés Moya*
