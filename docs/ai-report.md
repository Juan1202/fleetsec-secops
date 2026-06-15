# AI Report · FleetSec SecOps

> **Documento mandatorio según brief de la prueba técnica.**
> Versión extendida; el README principal contiene la **versión compacta ≤ 150 palabras** que cumple el requisito de evaluación.
> Este documento se actualiza incrementalmente en cada PR significativo que use IA.

---

## 1. Tools y tareas

### 1.1 IA generativa principal

**Claude.ai** (proyecto principal: `juanandres-secops-skills` + sesiones interactivas)

Tareas delegadas:
- **Skills personalizados** (10 SKILL.md, 3 rules, 4 slash commands) usados como base de conocimiento autorizada para cada sub-tarea de seguridad
- **Drafting de reglas Sigma** (4 reglas con MITRE ATT&CK mapping)
- **Plantillas Terraform** para módulo `security-baseline` (estructura inicial, no values production)
- **Fichas VAPT en español** (estructura plantilla por finding, no contenido de evidencia específica del sistema)
- **Generación DOCX** vía Node.js `docx` package (re-uso de flujo de Traccar 6.7)
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
Durante el drafting de la regla Sigma para detectar bulk exfiltration vía `s3:GetObject` (Sprint 4, Story FSEC-4.3, regla R3), Claude propuso el siguiente snippet como si fuera **sintaxis core de Sigma**:

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

3. **En la regla Sigma "portable"** dejé únicamente el matching y agregué un campo `falsepositives:` que advierte:
   > "Requires backend-side aggregation; rule alone matches single events. Combine with correlation rule or query post-processing for the 'bulk' semantic."

4. **Lección capturada en `docs/ADRs/ADR-006-sigma-backend-choice.md`**: cuando una regla requiere correlación temporal, declarar el backend target up front en vez de pretender portabilidad.

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
  - Cada supresión en `.semgrepignore`, `.trivyignore`, `checkov.yml` requiere los 4 campos canónicos (Rule, Reason, Date, Responsible, Review-by)
  - CI step valida los campos obligatorios (FSEC-1.4)
  - Job semanal abre Issue si una supresión está a < 14 días de expirar
  - El humano que aprueba la supresión queda registrado (no es la IA)

### 3.5 Selección de stack para app vulnerable
- **Por qué**: la decisión técnica involucra trade-offs (tiempo vs aprendizaje vs cobertura) que dependen del background del autor y del juicio sobre qué demostrar al evaluador. ADR-001 documentará la decisión.

### 3.6 Aprobaciones de PR con cambios de seguridad
- **Por qué**: CODEOWNERS exige human review. La IA puede ayudar a redactar el comentario de review, pero el "Approve" lo da un humano que entiende el contexto del repo y el blast radius del cambio.

---

## 4. Métricas de uso de IA (running tally)

| Sprint | % AI-generated (lines) | % Human-modified post-AI | Validación |
|---|---|---|---|
| Sprint 0 | ~70% (scaffolding repetitivo) | ~30% (CODEOWNERS, gitleaks rules custom) | JSON schema + frontmatter regex |
| Sprint 1 | — | — | (a actualizar al cierre) |
| Sprint 2 | — | — | (a actualizar) |
| Sprint 3 | — | — | (a actualizar) |
| Sprint 4 | — | — | (a actualizar) |
| Sprint 5 | — | — | (a actualizar) |

---

## 5. Cambios incrementales

> Cada PR significativo que use IA debe agregar una entrada aquí.

### 2026-06-15 · Sprint 0 — Fundación
- Skills repo `juanandres-secops-skills`: 10 SKILL.md generados con Claude, todos revisados línea por línea
- Cronograma + plan de sprints: estructura propuesta por Claude, calibrada manualmente contra brief PDF
- Jira FSEC: 6 epics + 25 historias creadas vía API; descripciones generadas con Claude desde el cronograma
- Confluence cronograma: re-formato markdown → HTML storage por Claude (renderizado verificado)
- Scaffolding del repo (este commit): generado por Claude, revisado manualmente antes de primer push

---

## 6. Notas finales

- Este documento se considera **parte del entregable 05** (Documentación) y por tanto sujeto a revisión por el evaluador
- La versión ≤ 150 palabras en el README es la que cumple el requisito formal del brief; este documento extendido lo respalda
- **Claiming zero AI errors does not score** — este documento intencionalmente incluye una alucinación real con su corrección documentada
- Histórico de versiones via `git log docs/ai-report.md` (Conventional Commits — `docs(ai-report): ...`)

---

*Última actualización: 2026-06-15 · Autor: Juan Andrés Moya*
