> **Language:** Spanish (Español) · [Українська](../HOWTO.md) · [English](../en/HOWTO.md)

# HOWTO — escenarios rápidos

Pasos cortos para el uso diario. Descripción completa de la UI: [USER_GUIDE.md](USER_GUIDE.md).

## Iniciar la GUI

```bash
cd java
./pingui-java.sh
```

Idioma: menú **Idioma** o `./pingui-java.sh -- --lang es`.

## Añadir un objetivo y monitorear

1. Escriba IPv4 / hostname en el campo bajo la lista.
2. **Añadir** (o Enter).
3. Active la casilla — comienza el trace / ping.
4. Seleccione la fila — el grafo de ruta aparece a la derecha.

## Vista Simple y Extendida

- **Simple** — ventana compacta (~580 px).
- **Extendida** — panel ancho, Expert ping, historial.

## Ping only

Active **Ping only** en el modo de sonda para medir solo RTT sin traceroute completo.

## Guardar

**Guardar** (Ctrl/Cmd+S) escribe la lista en el YAML del perfil.

## Ayuda

**F1** / menú de ayuda. Detalles: [USER_GUIDE.md](USER_GUIDE.md).
