# Viewer for Traccar (Wear OS)

Visor sencillo de un servidor [Traccar](https://www.traccar.org) para Wear OS: dónde están tus
dispositivos, un tile con los que elijas y avisos cuando no llevas el móvil encima. No es un gestor:
para administrar se sigue usando la web o la Traccar Manager oficial del teléfono.

Funciona con cualquier servidor Traccar 6 sin tocar su configuración. Usa la API REST con un token.

## Funciones

- **Acceso:** con correo y contraseña la app pide un token de larga duración y **no guarda la
  contraseña**. También se puede pegar un token creado en la web (*Ajustes › Preferencias*), que es
  lo necesario con SSO u OpenID.
- **Lista y detalle:** estado, última actualización, velocidad y dirección, y *Abrir mapa* en el
  reloj o, si no hay app de mapas, en el teléfono.
- **Tile:** hasta 3 dispositivos fijados (o los últimos activos), coloreados por estado.
- **Avisos lejos del móvil:** con el teléfono conectado por Bluetooth avisa la Traccar Manager
  oficial. Cuando no está cerca, el reloj consulta cada 15 minutos los eventos del servidor de los
  tipos que el usuario tiene configurados en *Notificaciones* y los muestra.

## Limitaciones conocidas

- Los avisos propios llegan con hasta unos 15 minutos de retraso, el mínimo de WorkManager. Para
  tiempo real haría falta una conexión WebSocket persistente (`/api/socket`), con más consumo de
  batería.
- Si el móvil está lejos pero con internet, algunos relojes reciben también su aviso a través de la
  nube y puede verse duplicado.
- El token se guarda en el almacenamiento privado de la app, que no entra en las copias de seguridad.

## Compilar

Android Studio con AGP 9.4, JDK 17 o superior y el SDK de Android 37.

```sh
./gradlew assembleDebug
```

## Pendiente

- Probarlo con un servidor real y un reloj real.
- Mapa estático en el detalle.
- Elegir licencia.
