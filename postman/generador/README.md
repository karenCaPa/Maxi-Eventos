# Generador de la colección de Postman

`generar-coleccion.js` escribe los dos archivos de `postman/`:

- `maxi-eventos.postman_collection.json` (15 carpetas, 232 peticiones, 631 pruebas desde la Fase 10B3c)
- `maxi-eventos-local.postman_environment.json` (URLs, `gatewayBaseUrl`, `correo` y `password` **vacía**)

Regla de nombres (Fase 10): todo lo que la colección **crea** (clientes, lugares, proveedores y eventos) se llama
"Prueba Postman …", para que la interfaz web lo oculte por defecto junto con lo de los scripts de verificación.

Necesita solo Node.js (probado con la 22); usa los módulos `fs` y `path` de Node, **sin paquetes de npm**. No contiene
secretos: la contraseña se lee en Postman de la variable `password` del environment, que el script deja vacía.

## Regenerar

Desde la raíz del proyecto:

```powershell
node postman\generador\generar-coleccion.js
```

Sobrescribe los dos JSON de `postman/`. Para revisar el resultado antes de reemplazarlos, pasar otra carpeta de destino
(debe existir) y compararla:

```powershell
node postman\generador\generar-coleccion.js $env:TEMP\postman-nuevo
fc.exe /b $env:TEMP\postman-nuevo\maxi-eventos.postman_collection.json postman\maxi-eventos.postman_collection.json
```

Después, ejecutar la colección completa (`scripts\verificacion\ejecutar-postman.ps1`) para confirmar 0 fallos.

## Dos formas de mantener la colección

1. **Editar este script y regenerar** (recomendado para cambios grandes): las peticiones se arman con funciones pequeñas
   (`req`, `e400`, `e404`, `e409`, `pagina`, `reintentar`…), así que agregar un caso es una línea.
2. **Editar en Postman y exportar de nuevo** (Collection v2.1) sobre `postman/maxi-eventos.postman_collection.json`.
   Ojo: a partir de ese momento el script ya no refleja la colección; si se vuelve a ejecutar, **borra** los cambios hechos
   en Postman. Elegir una de las dos formas y no mezclarlas (o pasar los cambios al script).

Al exportar el environment desde Postman, comprobar que `password` siga vacía en "Initial value".
