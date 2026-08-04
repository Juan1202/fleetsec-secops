# Notificación de Incidente de Seguridad a la SIC

**Superintendencia de Industria y Comercio — Delegatura para la Protección de Datos Personales**

> Reporte presentado conforme a la **Ley 1581 de 2012** y el **Decreto 1377 de 2013**, dentro del
> término de **15 días hábiles** desde la detección del incidente.
>
> | Campo | Valor |
> |---|---|
> | Responsable del Tratamiento | FleetSec S.A.S. (NIT [pendiente]) |
> | Incidente interno | IR-2026-001 |
> | Fecha de detección | 2026-07-28 |
> | Fecha objetivo de envío | 2026-08-18 (dentro de los 15 días hábiles) |

---

## 1. Naturaleza del incidente

El 28 de julio de 2026, entre las 02:00 y las 04:00 (UTC), un tercero no autorizado accedió a la
infraestructura en la nube (AWS) de FleetSec utilizando credenciales válidas de una cuenta técnica
interna (`svc-monitoring`), presuntamente obtenidas mediante la explotación de una vulnerabilidad de
la aplicación (Server-Side Request Forgery que permitió el acceso al servicio de metadatos de la
instancia y el robo de credenciales). El actor escaló privilegios a nivel administrador y realizó
la lectura y exfiltración masiva de información almacenada en un repositorio de datos personales de
conductores. El acceso se originó desde un nodo de la red de anonimización Tor
(IP `185.220.101.22`), lo que evidencia intención deliberada de ocultamiento.

## 2. Categorías de datos personales afectados

- **Datos de identificación:** nombre completo, número de cédula.
- **Datos de contacto:** correo electrónico, número de teléfono.
- **Datos de licencia de conducción.**
- **Datos de geolocalización/movimiento:** orígenes, destinos y horarios de viajes.

> No se procesan datos sensibles (Art. 5) en el repositorio afectado. Los datos de geolocalización
> se consideran de especial cuidado por permitir inferir patrones de desplazamiento de los titulares.

## 3. Volumen y número aproximado de titulares

- **Titulares afectados:** aproximadamente **60.000 conductores**.
- **Volumen de información exfiltrada:** aproximadamente **45,7 GB**.
- **Ventana de exposición:** ~2 horas (desde el acceso inicial hasta la contención).

## 4. Consecuencias posibles del incidente

- Uso indebido de los datos de contacto para fraude, phishing o suplantación.
- Riesgo derivado de la exposición de patrones de geolocalización (seguridad física de los
  titulares).
- Riesgo reputacional y de confianza para los titulares y la organización.
- No se ha detectado, a la fecha, publicación o comercialización de los datos.

## 5. Medidas adoptadas (contención y erradicación)

- Desactivación inmediata de las credenciales comprometidas y **revocación de las sesiones activas**
  (invalidación de tokens temporales).
- Remoción de los privilegios administrativos indebidamente asignados.
- **Preservación de la evidencia forense** (snapshots de disco y captura de memoria) antes de
  cualquier acción destructiva, y exportación de registros de auditoría a un repositorio
  **inmutable** (Object Lock).
- Bloqueo de la dirección IP de origen en el firewall de aplicaciones (WAF).
- El intento del atacante de **borrar los registros de auditoría fue bloqueado** por un control
  preventivo (SCP), por lo que se conserva la trazabilidad completa del incidente.
- Notificación interna al Oficial de Protección de Datos y activación del plan de respuesta.

## 6. Medidas de prevención futuras

- Forzar autenticación reforzada al servicio de metadatos (**IMDSv2**), que elimina el vector de
  robo de credenciales que originó el incidente.
- Implementar **límites de permisos** (permission boundaries) que impidan la auto-escalada a
  administrador.
- Exigir **MFA** para todas las cuentas con acceso a consola.
- Desplegar **alertas de acceso masivo** a los repositorios de datos personales y **respuesta
  automatizada** a los hallazgos de detección.
- Restricción de egreso de red y monitoreo continuo (plan de remediación P1/P2/P3 documentado).

## 7. Datos de contacto del Responsable

- **Responsable del Tratamiento:** FleetSec S.A.S.
- **Oficial de Protección de Datos (DPO):** [Nombre] — privacidad@fleetsec.co — [teléfono]
- **Domicilio:** Bogotá D.C., Colombia
- **Para seguimiento de este reporte:** referencia IR-2026-001.

---

### Notas de cumplimiento

- La organización conservará el registro del incidente por un término **mínimo de 5 años**.
- Se evalúa la **notificación directa a los titulares** dada la naturaleza de los datos
  (recomendada); canal: correo electrónico + aviso público si aplica notificación masiva.
- **Régimen sancionatorio:** las sanciones aplicables (hasta **2.000 SMMLV**) están previstas en el
  **Art. 23 de la Ley 1581 de 2012**; el **Decreto 1377 de 2013** es reglamentario (procedimiento de
  notificación y tratamiento), no la fuente de la sanción.
- **Verificación legal pendiente (punto de decisión #2):** el plazo de 15 días hábiles y el
  equivalente en COP de la sanción (aproximado, según el SMMLV vigente) deben ser validados por el
  área legal contra la normativa y la Circular Externa Única de la SIC vigentes antes del envío.
