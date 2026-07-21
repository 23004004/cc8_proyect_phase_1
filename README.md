# PROYECTO

### IA Utilizada: Codex - GPT-5.4-Mini

#### PROMP:

Necesito ayuda para un desarrollo. No quiero únicamente código, quiero arquitectura bien justificada, buenas prácticas y explicaciones técnicas.
## Contexto del proyecto

Estoy desarrollando un proyecto universitario llamado captura la bandera.

**Tecnologías obligatorias:**

- Java 21.0.7 2025-04-15 LTS
- IntelliJ IDEA
- Java Swing para la interfaz gráfica
- TCP Sockets
- Arquitectura Cliente-Servidor
- Comunicación mediante JSON
- UTF-8
- Proyecto java simple sin usar maven o Gradle.

El objetivo principal **NO** es la interfaz gráfica.

El objetivo principal es desarrollar un servidor robusto, escalable y compatible con implementaciones realizadas por otros compañeros en diferentes lenguajes de programación.

Debemos de tomar en cuenta que nosotros podemos ser el servidor y tambien el cliente. peor no los dos al mismo tiempo.

Todos los clientes deberán poder conectarse entre sí independientemente del lenguaje utilizado.

Por esa razón el protocolo de comunicación es el aspecto más importante.

También adjuntaré el documento oficial donde se especifica completamente el protocolo que TODOS los grupos deben respetar.

Ese documento tiene prioridad sobre cualquier decisión que propongas.

## Restricciones importantes

La interfaz gráfica será sencilla utilizando Swing.

No quiero utilizar motores gráficos.

La prioridad absoluta será:

1. Arquitectura
2. Comunicación mediante sockets
3. Sincronización del juego
4. Concurrencia
5. Compatibilidad con otros lenguajes
6. Rendimiento
7. Finalmente la interfaz.

El servidor deberá soportar aproximadamente 50 jugadores simultáneos.

Debe minimizar problemas de latencia.

Debe evitar condiciones de carrera.

Debe tener un diseño limpio.

Debe ser fácilmente mantenible.

Debe seguir principios SOLID cuando sea posible.

El servidor será la única autoridad del juego.

Los clientes nunca decidirán posiciones ni resultados.

Toda la lógica debe ejecutarse en el servidor.

La interfaz únicamente representará el estado recibido.

## Estructura inicial del proyecto

capture_the_flag/

src/

connect/
Client.java
Server.java

model/
Game.java

view/
PanelGame.java

Main.java


Actualmente estos archivos se encuentran prácticamente vacíos.
Quiero que me ayudes a construir el proyecto paso a paso.

## Forma de trabajar

No escribas todo el proyecto de una vez.

Trabajaremos de forma incremental.

Antes de escribir código:

- analiza el problema
- identifica responsabilidades
- propone arquitectura
- explica ventajas y desventajas
- después implementaremos solamente una pequeña parte.

Cuando propongas una solución:

- explica por qué
- qué problema resuelve
- cómo afectará al resto del proyecto

Siempre intenta que el código sea desacoplado.

Evita clases gigantes.

Evita duplicación.

Usa nombres claros.

Piensa como si este proyecto fuera software profesional.

Si detectas que mi estructura puede mejorar, proponla primero antes de escribir código.

Cada decisión deberá considerar que posteriormente otros clientes escritos en Python, C#, Go, Node.js o C++ deberán poder conectarse sin modificar el protocolo.

No asumas información que no aparezca en el documento oficial del protocolo.

Si alguna regla entra en conflicto con una decisión de diseño, deberá respetarse el protocolo oficial.

Nuestro primer objetivo será diseñar correctamente la arquitectura del proyecto antes de escribir cualquier línea de código.