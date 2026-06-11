# PassVault 🔐

**Gestor de contraseñas para Android — 100% local, de código abierto y de conocimiento cero.**

Tus contraseñas se cifran en tu dispositivo y nunca salen de él. Sin cuentas, sin servidores, sin telemetría.

[![Licencia: MIT](https://img.shields.io/badge/Licencia-MIT-blue.svg)](LICENSE)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-Apóyame-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/chris46036)

## ✨ Funciones

- 🔒 **Cifrado fuerte** — AES-256-GCM con clave derivada por PBKDF2-HMAC-SHA256 (250.000 iteraciones). El archivo de la bóveda es indescifrable sin tu contraseña maestra.
- 👆 **Desbloqueo biométrico** — huella digital respaldada por el Android Keystore; la clave protegida nunca sale del hardware seguro.
- 🎲 **Generador de contraseñas** — de 8 a 64 caracteres o frases de paso memorables, con medidor de fortaleza en tiempo real.
- 🔢 **Códigos 2FA (TOTP)** — guarda tus secretos de verificación en dos pasos y genera los códigos dentro de la app, como un autenticador.
- ✍️ **Autorrellenado del sistema** — rellena usuario y contraseña en otras apps y navegadores, y ofrece guardar credenciales nuevas automáticamente.
- 🩺 **Auditoría de seguridad** — detecta contraseñas débiles, reutilizadas y antiguas, y comprueba filtraciones contra Have I Been Pwned usando k-anonimato (tu contraseña jamás se envía).
- ⏱️ **Auto-bloqueo** — la bóveda se cierra sola al salir de la app o tras el tiempo que configures.
- 📋 **Portapapeles seguro** — lo copiado se marca como sensible y se borra automáticamente a los segundos.
- 🚫 **Anti-capturas** — pantalla protegida contra capturas y vista previa en apps recientes.
- 💾 **Copias de seguridad cifradas** — exporta e importa tu bóveda como archivo `.pvlt` protegido.
- 🗂️ **Organización** — categorías, favoritos y búsqueda instantánea.

## 📱 Instalación

1. Descarga el APK desde [Releases](../../releases) (o compílalo tú mismo, abajo).
2. Ábrelo en tu teléfono y acepta la instalación de orígenes desconocidos si te lo pide.
3. Crea tu contraseña maestra. ⚠️ **No se puede recuperar si la olvidas** — ese es el precio del conocimiento cero.
4. En **Ajustes → Autorrellenar**, activa PassVault como servicio de autofill del sistema.

## 🛠️ Compilar desde el código

Requisitos: JDK 17 y el Android SDK (API 34).

```bash
git clone https://github.com/Chris46036/PassVault.git
cd PassVault
./gradlew assembleRelease
```

Para firmar el APK de release, crea un `keystore.properties` en la raíz:

```properties
storeFile=tu.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

## 🔐 Modelo de seguridad

- La bóveda vive en un único archivo cifrado dentro del almacenamiento privado de la app.
- La contraseña maestra **nunca se guarda**; solo existe la clave derivada en memoria mientras la bóveda está desbloqueada.
- AES-GCM autentica el contenido: cualquier manipulación del archivo se detecta al descifrar.
- La comprobación de filtraciones envía solo los 5 primeros caracteres del hash SHA-1 (k-anonimato).
- Sin permiso de red salvo para la comprobación opcional de filtraciones. Sin analíticas, sin rastreadores.

¿Encontraste una vulnerabilidad? Abre un issue o contacta de forma privada.

## ☕ Apoya el proyecto

Si PassVault te resulta útil, puedes [invitarme un café en Ko-fi](https://ko-fi.com/chris46036). ¡Gracias!

## 📄 Licencia

[MIT](LICENSE) — úsalo, modifícalo y compártelo libremente.
