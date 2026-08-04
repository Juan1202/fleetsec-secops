# Playbook de Respuesta a Incidentes · IR-2026-001

> Ciclo **NIST SP 800-61 r2**. Comandos AWS CLI **ejecutables** (parámetros verificados contra
> aws-cli v2). Cada bloque de containment lleva **rollback**. Regla de oro:
> **preservar evidencia ANTES de destruir** (snapshot + memoria antes de detener/aislar).

## Variables del incidente

```bash
export AWS_PAGER=""
INCIDENT="IR-2026-001"
ACCOUNT_ID="123456789012"
REGION="us-east-1"
USER="svc-monitoring"
INSTANCE_ID="i-0abc1234def56789"
TOR_IP="185.220.101.22"
DATA_BUCKET="prod-drivers"
EVIDENCE_BUCKET="ir-evidence-locked"          # Object Lock COMPLIANCE, cuenta forense separada
SG_QUARANTINE="sg-0quarantine1234567"         # pre-creado en Preparation (deny all)
CONTAIN_TS="2026-07-28T04:30:00Z"             # corte para revocar sesiones STS
WINDOW_START="2026-07-28T00:00:00Z"
WINDOW_END="2026-07-28T05:00:00Z"
```

---

## Fase 1 · Preparation (lo que ya debe existir)

- **Contactos y roles:** IR lead, SecOps on-call, DPO (oficial de privacidad, para la SIC), legal, comms. War-room en canal dedicado con log de decisiones timestamped.
- **Infra de IR pre-provisionada** (la provee el módulo `security-baseline` del Sprint 3):
  - `SG_QUARANTINE` deny-all pre-creado.
  - Bucket `ir-evidence-locked` con **Object Lock COMPLIANCE** + SSE-KMS (cuenta forense separada).
  - CloudTrail multi-region + Object Lock en los logs; GuardDuty; VPC Flow Logs.
  - SCP que niega `cloudtrail:DeleteTrail`/`StopLogging` (**bloqueó** el intento del atacante).
- **Rol de IR** con permisos de forense (snapshot, SSM, s3 a evidence bucket).
- Runbooks (este documento) y plantillas de comunicación (CEO brief, SIC) listos.

---

## Fase 2 · Detection & Analysis

Confirmar el incidente y su alcance con queries reales.

```bash
# 2.1 · El AttachUserPolicy sospechoso en CloudTrail
aws cloudtrail lookup-events \
  --lookup-attributes AttributeKey=EventName,AttributeValue=AttachUserPolicy \
  --start-time "$WINDOW_START" --end-time "$WINDOW_END" \
  --region "$REGION"

# 2.2 · Toda la actividad del principal comprometido
aws cloudtrail lookup-events \
  --lookup-attributes AttributeKey=Username,AttributeValue="$USER" \
  --start-time "$WINDOW_START" --end-time "$WINDOW_END" \
  --region "$REGION" > "evidence-${USER}-events.json"

# 2.3 · Findings de GuardDuty de severidad alta (>=7)
DETECTOR_ID=$(aws guardduty list-detectors --query 'DetectorIds[0]' --output text --region "$REGION")
aws guardduty list-findings --detector-id "$DETECTOR_ID" \
  --finding-criteria '{"Criterion":{"severity":{"Gte":7}}}' --region "$REGION"

# 2.4 · Confirmar el acceso masivo al bucket de PII (data events)
aws cloudtrail lookup-events \
  --lookup-attributes AttributeKey=EventName,AttributeValue=GetObject \
  --start-time "$WINDOW_START" --end-time "$WINDOW_END" --region "$REGION" \
  | grep -c "$DATA_BUCKET"    # esperado: ~387
```

**Clasificación:** SEV-1 (exfiltración activa de PII + compromiso de credenciales privilegiadas).
Activar war-room y notificar al DPO (arranca el reloj de 15 días hábiles de la SIC).

---

## Fase 3 · Containment

> **ORDEN CRÍTICO (punto de decisión #1): evidencia ANTES de destruir.** La RAM se pierde al
> apagar; el snapshot y la captura de memoria van **antes** del stop/quarantine.

### 3.1 · Desactivar las access keys del usuario comprometido

```bash
aws iam list-access-keys --user-name "$USER"
# Desactivar (preserva para forense; preferible a delete al inicio)
aws iam update-access-key --user-name "$USER" --access-key-id AKIAEXAMPLE12345 --status Inactive
# Revocar acceso de consola
aws iam delete-login-profile --user-name "$USER"
```
**Rollback:** `aws iam update-access-key --user-name "$USER" --access-key-id AKIAEXAMPLE12345 --status Active` (y recrear login profile si fue un falso positivo). Las keys *eliminadas* no se recuperan — por eso se **desactivan**, no se borran, en el primer paso.

### 3.2 · Revocar las sesiones STS ya emitidas (tokens robados)

El método canónico AWS: negar todo lo emitido antes del corte con `aws:TokenIssueTime` — surte
efecto **inmediato** sin esperar la expiración del token.

```bash
cat > /tmp/revoke-sessions.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Deny",
    "Action": "*",
    "Resource": "*",
    "Condition": { "DateLessThan": { "aws:TokenIssueTime": "$CONTAIN_TS" } }
  }]
}
EOF

aws iam put-user-policy --user-name "$USER" \
  --policy-name RevokeSessionsBefore-${INCIDENT} \
  --policy-document file:///tmp/revoke-sessions.json
```
**Rollback:** `aws iam delete-user-policy --user-name "$USER" --policy-name RevokeSessionsBefore-${INCIDENT}`

### 3.3 · Quitar la policy maliciosa (AdministratorAccess)

```bash
aws iam detach-user-policy --user-name "$USER" \
  --policy-arn arn:aws:iam::aws:policy/AdministratorAccess
```
**Rollback:** `aws iam attach-user-policy --user-name "$USER" --policy-arn arn:aws:iam::aws:policy/AdministratorAccess` (solo si se determina que el attach era legítimo — improbable).

### 3.4 · Aislar la EC2 — SNAPSHOT + MEMORIA **ANTES** de tocar la red

```bash
# (a) Snapshot de TODOS los volúmenes EBS — evidencia de disco
for VOL in $(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" \
    --query 'Reservations[].Instances[].BlockDeviceMappings[].Ebs.VolumeId' --output text); do
  aws ec2 create-snapshot --volume-id "$VOL" \
    --description "Forensic ${INCIDENT} ${INSTANCE_ID} $(date -u +%Y%m%dT%H%M%SZ)" \
    --tag-specifications "ResourceType=snapshot,Tags=[{Key=ir-evidence,Value=true},{Key=incident,Value=${INCIDENT}}]"
done

# (b) Captura de MEMORIA vía SSM — ANTES del stop (la RAM se pierde al apagar)
aws ssm send-command --instance-ids "$INSTANCE_ID" \
  --document-name "AWS-RunShellScript" \
  --comment "IR ${INCIDENT} memory acquisition" \
  --parameters 'commands=[
    "set -e",
    "insmod /opt/lime/lime.ko \"path=/tmp/mem.lime format=lime\" || echo LIME_NOT_PRESENT",
    "aws s3 cp /tmp/mem.lime s3://ir-evidence-locked/'"${INCIDENT}"'/memory/'"${INSTANCE_ID}"'.lime"
  ]'

# (c) RECIÉN AHORA: mover a la SG de cuarentena (deny all)
aws ec2 modify-instance-attribute --instance-id "$INSTANCE_ID" --groups "$SG_QUARANTINE"

# (d) Opcional: detener (preserva disco, pierde RAM — ya capturada en (b))
# aws ec2 stop-instances --instance-ids "$INSTANCE_ID"
```
**Rollback:** restaurar las SG originales →
`aws ec2 modify-instance-attribute --instance-id "$INSTANCE_ID" --groups sg-app-original` (los IDs originales se registran en el war-room antes del cambio). Los snapshots quedan como evidencia inmutable; no se revierten.

### 3.5 · Bloquear la IP Tor en el WAF

`update-ip-set` **reemplaza** el conjunto completo → incluir las direcciones ya presentes + la nueva.

```bash
IPSET_ID=$(aws wafv2 list-ip-sets --scope REGIONAL --region "$REGION" \
  --query "IPSets[?Name=='blocklist-ir'].Id" --output text)
LOCK_TOKEN=$(aws wafv2 get-ip-set --scope REGIONAL --region "$REGION" \
  --name blocklist-ir --id "$IPSET_ID" --query 'LockToken' --output text)

aws wafv2 update-ip-set --scope REGIONAL --region "$REGION" \
  --name blocklist-ir --id "$IPSET_ID" \
  --addresses "${TOR_IP}/32" \
  --lock-token "$LOCK_TOKEN"
```
**Rollback:** volver a `update-ip-set` con la lista previa (sin `${TOR_IP}/32`) usando el nuevo `LockToken`.

### 3.6 · Preservar CloudTrail y logs a la bóveda de evidencia (Object Lock)

```bash
aws s3 cp \
  s3://prod-secaudit-logs-${ACCOUNT_ID}/AWSLogs/${ACCOUNT_ID}/CloudTrail/${REGION}/2026/07/28/ \
  s3://${EVIDENCE_BUCKET}/${INCIDENT}/cloudtrail/ --recursive

# Subset de VPC Flow Logs del egreso (correlación del 45.7 GB)
aws logs start-query \
  --log-group-name /aws/vpc/flowlogs \
  --start-time $(date -u -d "$WINDOW_START" +%s 2>/dev/null || echo 1785283200) \
  --end-time   $(date -u -d "$WINDOW_END"   +%s 2>/dev/null || echo 1785301200) \
  --query-string "fields @timestamp, srcAddr, dstAddr, bytes | filter dstAddr = '${TOR_IP}' | sort bytes desc"
```
> **No hay rollback** — la preservación de evidencia es aditiva e inmutable (Object Lock COMPLIANCE).

---

## Fase 4 · Eradication

- **Rotar TODAS las credenciales** potencialmente expuestas: keys de `svc-monitoring`, secretos en
  Secrets Manager referenciados por la EC2, y cualquier key derivada del rol de instancia.
- **Parchar la ruta de entrada — el SSRF V-03:** forzar **IMDSv2** en la EC2 y en el launch template
  (lo que el módulo del Sprint 3 ya materializa). Sin esto, el vector de robo de credenciales sigue
  abierto (ver [RCA](rca.md)).
  ```bash
  aws ec2 modify-instance-metadata-options --instance-id "$INSTANCE_ID" \
    --http-tokens required --http-endpoint enabled --http-put-response-hop-limit 1
  ```
- **Revisar persistencia:** buscar IAM users/roles/keys creados por el atacante, funciones Lambda,
  reglas de EventBridge, o access keys nuevas en la ventana del incidente.
  ```bash
  aws cloudtrail lookup-events \
    --lookup-attributes AttributeKey=EventName,AttributeValue=CreateUser \
    --start-time "$WINDOW_START" --end-time "$WINDOW_END" --region "$REGION"
  ```

## Fase 5 · Recovery

- Restaurar el servicio desde una imagen limpia (nueva EC2 desde AMI validada, con IMDSv2, tras
  destruir la instancia comprometida — ya con evidencia preservada).
- **Monitoreo elevado:** bajar el umbral de las alarmas del baseline, watch sobre `svc-monitoring`
  y `prod-drivers` por 30 días.
- **Validar erradicación:** confirmar que el atacante no tiene acceso residual (sin sesiones STS
  válidas, sin keys activas, sin persistencia).
- Cargar `${TOR_IP}` al threat intel set de GuardDuty (ver [iocs.md](iocs.md)).

## Fase 6 · Lessons Learned

- Post-mortem **blameless** → [RCA](rca.md) (5 Whys + Swiss Cheese, identifica V-03 como causa raíz).
- Plan de remediación P1/P2/P3 → [remediation-plan.md](remediation-plan.md).
- Reglas de detección para cazar el mismo TTP más rápido → [detections/](detections/) + [mitre-mapping.md](mitre-mapping.md).
- Conservar el registro del incidente **≥5 años** (Ley 1581 / SIC).

---

## Apéndice · Checklist de containment (orden estricto)

1. [ ] Access keys → `Inactive` (3.1)
2. [ ] Sesiones STS revocadas vía `aws:TokenIssueTime` (3.2)
3. [ ] `AdministratorAccess` detached (3.3)
4. [ ] **Snapshot EBS** (3.4a)
5. [ ] **Captura de memoria** vía SSM (3.4b) — *antes* de (3.4c/d)
6. [ ] EC2 a `SG_QUARANTINE` (3.4c)
7. [ ] IP Tor bloqueada en WAF (3.5)
8. [ ] CloudTrail/Flow Logs a `ir-evidence-locked` (3.6)
