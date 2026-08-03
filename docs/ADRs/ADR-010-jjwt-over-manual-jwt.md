# ADR-010 · Migración de JWT manual a librería jjwt

**Status:** Accepted
**Date:** 2026-08-03
**Sprint:** 2 (remediación VAPT)
**Authors:** Juan Andrés Moya

---

## Context

La remediación de [V-02](../../vapt/findings/V-02.md) (JWT `alg:none` aceptado, CWE-347) requiere corregir la verificación de tokens. El `JwtService` original estaba **implementado a mano** (Base64 + HMAC manual) y confiaba en el campo `alg` del header del cliente — la raíz del vector.

### Restricciones
- El fix debe **rechazar `alg:none`**, firmas inválidas y algoritmos inesperados.
- No debe romper el login legítimo (test dual).
- La firma pública de `JwtService` (`generateToken`/`validateToken`/`TokenClaims`) debe conservarse para no tocar `AuthController`.

### Alternativas consideradas

| Opción | Pro | Contra |
|---|---|---|
| A) Parchear el impl manual (quitar la rama `alg:none`, exigir HMAC) | Cambio mínimo, sin dependencia nueva | Se sigue manteniendo criptografía casera — superficie de error propia (padding, comparación de tiempo constante, parsing) |
| B) **Migrar a jjwt** (`io.jsonwebtoken`) | **Rechaza `alg:none` por diseño; algoritmo fijado por la clave; parsing y verificación probados por la comunidad** | Agrega 3 dependencias (api/impl/jackson) |
| C) Nimbus JOSE+JWT | Muy completa (JWE, JWK) | Más pesada de lo necesario para HS256 simple |

---

## Decision

**Opción B: migrar a jjwt (0.12.6).** *Don't roll your own crypto.*

`validateToken` usa `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`, que **rechaza tokens sin firma (`alg:none`), con firma inválida, con algoritmo distinto al de la clave, o expirados** — sin lógica criptográfica propia. `generateToken` firma con `Jwts.builder()...signWith(key)`. El `JwtService` manual (incluida la rama `alg:none`, el HMAC a mano y `constantTimeEquals`) se **elimina por completo** — no coexiste código muerto.

---

## Consequences

### Positivas
- ✅ V-02 remediado en la raíz: el algoritmo lo fija el servidor (la clave), no el cliente.
- ✅ Menos superficie de error: la verificación de firma/expiración es de una librería madura.
- ✅ `Keys.hmacShaKeyFor` valida que la clave sea ≥ 32 bytes (HS256) al arrancar.

### Negativas
- ⚠️ +3 dependencias (jjwt-api/impl/jackson) → entran al SBOM y al SCA. Mitigación: jjwt es liviana y activamente mantenida.

### Neutras
- 📋 La firma pública de `JwtService` se conserva → `AuthController` no cambia. El test dual valida rechazo (`alg:none` de la PoC) y flujo legítimo (HS256 válido).

---

## References

- [V-02 · JWT alg:none](../../vapt/findings/V-02.md)
- OWASP JWT Cheat Sheet · "Do not trust the `alg` header"
- jjwt · https://github.com/jwtk/jjwt
