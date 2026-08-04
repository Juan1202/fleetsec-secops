# Brief Ejecutivo — Incidente de Seguridad IR-2026-001

**Para:** CEO y comité ejecutivo · **De:** Líder de Seguridad
**Estado:** Contenido · **Fecha:** 2026-07-28

---

## Qué pasó

En la madrugada del 28 de julio, una persona no autorizada entró a nuestra plataforma en la nube
usando la contraseña de una cuenta técnica interna, se dio a sí misma permisos de administrador y
copió información de nuestros conductores hacia afuera. El acceso vino desde una red anónima (Tor),
lo que indica intención deliberada de ocultarse. El ataque duró unas **2 horas** hasta que lo
contuvimos.

## Qué se afectó

- **Datos:** información personal de conductores — nombre, cédula, correo, teléfono y datos de
  recorridos (geolocalización).
- **Escala:** aproximadamente **60.000 conductores**.
- **Volumen copiado:** ~45,7 GB.
- **Lo que NO pasó:** el atacante intentó borrar nuestros registros de auditoría para no dejar
  rastro, pero **una barrera de seguridad lo bloqueó** — conservamos la evidencia completa.

## Implicación regulatoria

- **Ley 1581 (protección de datos):** debemos notificar a la **SIC** (Superintendencia de Industria
  y Comercio) dentro de **15 días hábiles**. Fecha objetivo de envío: **2026-08-18**.
- **Sanción máxima posible:** hasta **2.000 SMMLV** (~**2.600 millones de pesos** a valores 2026),
  además de posible suspensión de actividades. *(Cifra a confirmar con legal — ver nota.)*
- **Notificación a titulares:** recomendada dada la sensibilidad de los datos.

## Tres acciones inmediatas (≤72 horas)

1. **Cerrar la puerta de entrada** — forzar autenticación reforzada en los servidores (IMDSv2), que
   elimina la falla técnica que originó el robo. *Dueño: SecOps · Plazo: +3 días.*
2. **Notificar a la SIC y a los titulares** — preparar y enviar la notificación formal. *Dueño:
   Oficial de Privacidad + Legal · Plazo: dentro del término legal.*
3. **Poner topes de permisos** — impedir que una sola cuenta pueda volverse administrador.
   *Dueño: SecOps · Plazo: +7 días.*

## Impacto financiero estimado (rango)

- **Costos directos** (forense, legal, notificación): decenas de millones de COP.
- **Exposición regulatoria:** hasta ~2.600 millones COP (sanción máxima; probable menor con
  mitigación demostrada y notificación en término).
- **Reputacional:** difícil de cuantificar; la notificación proactiva y las medidas ya tomadas
  reducen el daño.

> **La buena noticia:** ya sabíamos de esta falla (la detectamos en nuestra propia auditoría de
> seguridad) y la corrección ya estaba diseñada. Este incidente confirma la urgencia de
> desplegarla. La evidencia se preservó intacta y la respuesta fue ordenada.

---
*Nota de precisión: las cifras de sanción (2.000 SMMLV) y el plazo (15 días hábiles) se citan del
régimen vigente de Ley 1581 / Decreto 1377 y deben ser confirmadas por el área legal antes de
cualquier comunicación externa.*
