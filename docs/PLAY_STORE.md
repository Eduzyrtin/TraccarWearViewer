# Google Play: Wear OS Viewer for Traccar

Paquete `com.sanabria.traccarwearosviewer` · solo Wear OS · ficha en [`fastlane/metadata/android/`](../fastlane/metadata/android)
(formato estándar, ver §8 de `HERRAMIENTAS/GUIA_PROYECTOS.md`). Idiomas: los 9 de la app; `en-US` es el predeterminado.

- **Precio**: gratuita. También se descarga gratis desde GitHub (Releases).

## Qué subir y dónde (Play Console › Crecimiento › Presencia en la tienda › Ficha principal)

1. **Idiomas**: Gestionar idiomas → añade `es-ES`, `fr-FR`, `de-DE`, `it-IT`, `pt-PT`, `ca`, `eu-ES`, `gl-ES`. Pega en cada uno
   `title.txt`, `short_description.txt` y `full_description.txt` de su carpeta.
2. **Icono**: `en-US/images/icon.png` (vale para todos los idiomas).
3. **Gráfico de funciones** (obligatorio): `<idioma>/images/featureGraphic.png`, con el lema traducido.
4. **Capturas de Wear OS**: las de `en-US/images/wearScreenshots/` (6; 1:1, entre 384 y 3840 px). Valen para todos los idiomas.
5. **Novedades** de cada versión: `<idioma>/changelogs/<versionCode>.txt`.
6. Sin capturas de móvil ni tableta: la app no tiene versión de teléfono.

## Antes de publicar (lo que Play pedirá)

- **Política de privacidad (URL)**: obligatoria. El README ya describe la privacidad; publica ese texto en una URL.
- **Seguridad de los datos**: recoge **correo y credenciales** solo para obtener un token (la contraseña no se guarda). Habla
  solo con el servidor del usuario y con el proveedor de mapas elegido. Sin analíticas ni terceros.
- **Clasificación de contenido**, **público objetivo** (solo adultos), **anuncios** (ninguno) y factor de forma **Wear OS** al subir el AAB.
- **Marca**: la descripción dice que no está afiliada a Traccar. Mantén ese texto para evitar un rechazo por uso de marca.
- **Enlace al repositorio**: la descripción apunta a `github.com/Eduzyrtin/TraccarWearViewer`. Si el repo sigue privado, quita
  la sección «FREE AND OPEN SOURCE» (y su traducción) de los 9 idiomas, o haz el repo público antes de publicar.
