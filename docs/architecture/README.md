# Arquitectura · Diagramas (FSEC-29)

Diagramas Mermaid (se renderizan en GitHub). Cuentan la **narrativa de sistema**: cómo el
mismo hilo —el SSRF **V-03**— atraviesa los cuatro entregables técnicos.

---

## 1. As-is · entorno vulnerable (baseline, pre-remediación)

La app se sirve **sin autenticación**, con **IMDSv1** habilitado y sin baseline de infraestructura.
El SSRF (V-03) alcanza el IMDS y roba las credenciales del rol → la puerta del breach.

```mermaid
graph LR
  Attacker([Atacante]) -->|sin credenciales| App
  subgraph VPC_flat["Cuenta AWS · sin baseline"]
    App["App Spring Boot<br/>V01-V11 sin remediar<br/>(sin auth, sin headers)"]
    App -->|"V-03 SSRF · GET"| IMDS["IMDS v1<br/>169.254.169.254"]
    IMDS -->|"credenciales del rol"| App
    App --> DB[("H2 · PII en claro<br/>~60k conductores")]
    App -->|"PII sin redactar"| Logs["logs/ (texto plano)"]
    IAM["IAM sin permission boundary<br/>AttachUserPolicy permitido"]
  end
  App -.->|"no hay"| NoDetect["sin CloudTrail alarmas<br/>sin GuardDuty · sin WAF"]
  classDef bad fill:#f8d7da,stroke:#c0392b,color:#000;
  class App,IMDS,DB,Logs,IAM,NoDetect bad;
```

## 2. To-be · entorno endurecido (post-entregables)

App remediada (Sprint 2) **sobre** el baseline Terraform (Sprint 3), con detección y respuesta
de IR (Sprint 4). Cada capa corta un eslabón del breach.

```mermaid
graph TB
  User([Cliente]) --> WAF
  subgraph Edge["Borde"]
    WAF["WAF v2<br/>SQLi/BadInputs BLOCK · rate-limit · geo CO/PE/US"]
  end
  WAF --> ALB[ALB]
  subgraph VPC["VPC 3-tier · Flow Logs"]
    ALB --> App2["App remediada<br/>Spring Security (JWT + headers)<br/>V01-V11 Fixed"]
    App2 -->|"IMDSv2 required"| IMDS2["IMDS v2<br/>(token obligatorio: SSRF cortado)"]
    App2 --> RDS[("RDS Multi-AZ<br/>CMK · TLS · sin público")]
  end
  subgraph Detect["Deteccion + respuesta"]
    CT["CloudTrail multi-region<br/>+ 4 metric filters/alarmas"]
    GD["GuardDuty<br/>+ threat intel set"]
    IR["IR playbook<br/>NIST 800-61"]
  end
  App2 --> CT --> IR
  App2 --> GD --> IR
  IAM2["IAM + permission boundary<br/>(AttachUserPolicy neutralizado)"] --> App2
  classDef good fill:#d4edda,stroke:#1e7e34,color:#000;
  class WAF,App2,IMDS2,RDS,CT,GD,IR,IAM2 good;
```

## 3. Pipeline DevSecOps (9 stages → Security Gate)

Detector honesto: sobre la app vulnerable sale rojo; post-remediación, verde. El `Security Gate`
es el único *required check* y agrega los 9 stages.

```mermaid
flowchart LR
  PR([PR / push a main]) --> P
  subgraph P["DevSecOps Security Pipeline"]
    direction TB
    C[Commitlint]
    T[Tests JUnit]
    SAST[SAST · Semgrep]
    SCA[SCA · Trivy rootfs]
    CON[Container · Trivy image]
    IAC[IaC · Checkov]
    DAST[DAST · OWASP ZAP]
    SB[SBOM · Syft/CycloneDX]
    SEC[Secrets · gitleaks]
    SUP[Suppressions Audit]
  end
  C --> GATE
  T --> GATE
  SAST --> GATE
  SCA --> GATE
  CON --> GATE
  IAC --> GATE
  DAST --> GATE
  SB --> GATE
  SEC --> GATE
  SUP --> GATE
  GATE{{"Security Gate<br/>(required check)"}}
  GATE -->|todos verdes| MERGE([Mergeable])
  GATE -->|algun rojo| BG["Break-glass<br/>auto-Issue"]
  classDef gate fill:#1f3a5f,stroke:#1f3a5f,color:#fff;
  class GATE gate;
```

## 4. Breach timeline con overlay MITRE ATT&CK (IR-2026-001)

El hilo completo: el SSRF (V-03 / **T1552.005**) es el vector de entrada; cada paso mapea a una
técnica y a la capa del baseline (Sprint 3) que lo habría cortado.

```mermaid
sequenceDiagram
    autonumber
    actor A as Atacante (Tor 185.220.101.22)
    participant APP as App (SSRF V-03)
    participant IMDS as IMDS v1
    participant AWS as AWS Control Plane
    participant S3 as S3 prod-drivers
    Note over A,IMDS: T+00:00 - entrada
    A->>APP: explota SSRF (V-03)
    APP->>IMDS: GET credenciales del rol
    Note right of IMDS: T1552.005 - IMDS<br/>corte: IMDSv2 (Sprint 3)
    A->>AWS: ConsoleLogin (creds svc-monitoring)
    Note right of AWS: T1078.004 - Valid Accounts<br/>corte: MFA
    Note over A,AWS: T+00:15 - escalada
    A->>AWS: AttachUserPolicy AdministratorAccess
    Note right of AWS: T1098.001<br/>corte: permission boundary
    Note over A,S3: T+00:30-01:00 - exfil
    A->>S3: 387 GetObject (PII)
    Note right of S3: T1530<br/>corte: CloudTrail data events + alarma
    A->>A: egress 45.7 GB a Tor
    Note right of A: T1567.002<br/>corte: Flow Logs + egress FW
    Note over A,AWS: T+01:30 - anti-forense
    A->>AWS: DeleteTrail (BLOQUEADO por SCP)
    Note right of AWS: T1562.008<br/>corte: SCP + Object Lock (OK)
    Note over A,AWS: T+02:00 - C2
    A->>AWS: DNS exfil (GuardDuty finding)
    Note right of AWS: T1071.004<br/>corte: GuardDuty + DNS Firewall
```

---

> **Fuentes:** MITRE ATT&CK v14 · NIST SP 800-61 r2. El detalle de cada técnica está en
> [`ir/mitre-mapping.md`](../../ir/mitre-mapping.md); la cadena causal en [`ir/rca.md`](../../ir/rca.md).
