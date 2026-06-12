# Offlock 🔐

**Gestor de contraseñas para Android — 100% local, de código abierto y de conocimiento cero.**

Tus contraseñas se cifran en tu dispositivo y nunca salen de él. Sin cuentas, sin servidores, sin telemetría.

[![Build](https://github.com/Chris46036/PassVault/actions/workflows/build.yml/badge.svg)](https://github.com/Chris46036/PassVault/actions/workflows/build.yml)
[![Licencia: MIT](https://img.shields.io/badge/Licencia-MIT-blue.svg)](LICENSE)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-Apóyame-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/chris46036)

## ✨ Funciones

- 🗝️ **Proveedor de passkeys** — crea e inicia sesión con passkeys (WebAuthn) guardadas en tu bóveda, integrado con el Credential Manager del sistema (Android 14+).
- 🔒 **Cifrado fuerte** — AES-256-GCM con clave derivada por **Argon2id** (64 MiB, resistente a ataques con GPU), el mismo KDF que usan los gestores líderes. Las bóvedas antiguas con PBKDF2 se migran automáticamente.
- 👆 **Desbloqueo biométrico** — huella digital respaldada por el Android Keystore; la clave protegida nunca sale del hardware seguro.
- ✍️ **Autorrellenado completo** — rellena credenciales en apps y navegadores (con sugerencias dentro del teclado en Android 11+), **detecta credenciales nuevas y pregunta si guardarlas**, incluso con la bóveda bloqueada. Comparación **estricta de dominios** (anti-phishing): `facebook.malicioso.com` jamás recibirá tu contraseña de `facebook.com`.
- 🔢 **Códigos 2FA (TOTP)** — escanea el **código QR** con la cámara o pega el secreto; al autorrellenar una cuenta con 2FA, **el código se copia solo** al portapapeles.
- 🗂️ **Tipos de elemento** — cuentas, **tarjetas de crédito, notas seguras, identidades y archivos adjuntos cifrados**, con categorías, **etiquetas**, favoritos y búsqueda.
- 🏦 **Varias bóvedas** — Personal, Trabajo… cada una independiente y con su propia contraseña maestra.
- 🎲 **Generador** — contraseñas de 8 a 64 caracteres o frases de paso memorables, con medidor de fortaleza.
- 🩺 **Auditoría de seguridad** — contraseñas débiles, reutilizadas, antiguas y filtradas (Have I Been Pwned con k-anonimato; tu contraseña jamás se envía).
- 🕓 **Historial de contraseñas** y 🗑️ **papelera** con recuperación durante 30 días.
- 📥 **Importación** — KeePass (`.kdbx`), Chrome, Bitwarden, LastPass y similares (CSV).
- 💾 **Copias de seguridad** — exporta/importa la bóveda cifrada (`.pvlt`), **auto-respaldo a la carpeta que elijas** (SD, carpeta de Drive…) y 2 respaldos locales rotativos contra corrupción.
- 💳 **Autofill de tarjetas** — rellena número, vencimiento, CVV y titular en formularios de pago.
- 🛡️ **Anti fuerza bruta** — retrasos crecientes tras intentos fallidos y, opcionalmente, **borrado de la bóveda tras N intentos**. Confirmación de identidad (huella/PIN) para revelar datos de tarjeta o exportar.
- ⏱️ **Auto-bloqueo** (también por inactividad en pantalla), 📋 limpieza automática del portapapeles y 🚫 bloqueo de capturas de pantalla.
- ⚖️ **Argon2 autocalibrado** — el coste de derivación se ajusta a tu dispositivo (~1 s), más duro en gama alta.
- 📤 **Datos abiertos** — exporta a CSV sin cifrar cuando quieras migrar: tus datos son tuyos.
- 🎨 **Material You** (colores dinámicos en Android 12+), widget, mosaico de ajustes rápidos, atajos de aplicación y diseño a dos paneles en tablets.
- 🌍 **En español e inglés**, según el idioma del sistema. Compatible con **Android 7.0+** (~98% de los dispositivos).

## 📱 Instalación

1. Descarga el APK desde [Releases](../../releases) (o compílalo tú mismo, abajo).
2. Ábrelo en tu teléfono y acepta la instalación de orígenes desconocidos si te lo pide.
3. Crea tu contraseña maestra. ⚠️ **No se puede recuperar si la olvidas** — ese es el precio del conocimiento cero.
4. En **Ajustes → Autorrellenar**, activa Offlock como servicio de autofill del sistema.

## 🛠️ Compilar desde el código

Requisitos: JDK 17 y el Android SDK (API 34).

```bash
git clone https://github.com/Chris46036/PassVault.git
cd Offlock
./gradlew assembleRelease
```

Los tests unitarios (cifrado, TOTP contra los vectores del RFC 6238, generador, CSV y dominios) corren con `./gradlew test`, y la integración continua compila y prueba cada commit.

Para firmar el APK de release, crea un `keystore.properties` en la raíz:

```properties
storeFile=tu.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

## 🔐 Modelo de seguridad

- La bóveda vive en un único archivo cifrado dentro del almacenamiento privado de la app, con dos respaldos locales rotativos.
- La contraseña maestra **nunca se guarda**; solo existe la clave derivada en memoria mientras la bóveda está desbloqueada.
- Derivación de clave con **Argon2id** (64 MiB de memoria, 3 iteraciones): los ataques de fuerza bruta con GPU resultan prohibitivos.
- AES-GCM autentica el contenido: cualquier manipulación del archivo se detecta al descifrar.
- El autofill compara **dominios registrables exactos** para no entregar credenciales a sitios parecidos (phishing).
- La comprobación de filtraciones envía solo los 5 primeros caracteres del hash SHA-1 (k-anonimato).
- Sin permiso de red salvo para la comprobación opcional de filtraciones. Sin analíticas, sin rastreadores.

¿Encontraste una vulnerabilidad? Abre un issue o contacta de forma privada.

## ☕ Apoya el proyecto

Si Offlock te resulta útil, puedes [invitarme un café en Ko-fi](https://ko-fi.com/chris46036). ¡Gracias!

## 📄 Licencia

[MIT](LICENSE) — úsalo, modifícalo y compártelo libremente.
