# Política de privacidad de Offlock

**Última actualización: 11 de junio de 2026**

Offlock es un gestor de contraseñas de conocimiento cero desarrollado por Chris46036. Esta política describe cómo trata tus datos la aplicación Offlock para Android (`com.passvault.app`).

## Resumen

**Offlock no recopila, transmite, vende ni comparte ningún dato personal.** No hay cuentas de usuario, no hay servidores propios, no hay analíticas, no hay publicidad ni rastreadores de ningún tipo.

## Datos que almacena la aplicación

Todos los datos que guardas en Offlock (contraseñas, códigos 2FA, tarjetas, notas, identidades, passkeys y archivos adjuntos) se almacenan **exclusivamente en tu dispositivo**, dentro del almacenamiento privado de la aplicación, cifrados con AES-256-GCM mediante una clave derivada de tu contraseña maestra con Argon2id.

- Tu contraseña maestra **nunca se guarda** en ningún sitio.
- El desarrollador no tiene acceso técnico a tus datos ni forma de recuperarlos.
- Las copias de seguridad que exportes (archivos `.pvlt`) mantienen el mismo cifrado.
- Si activas el desbloqueo biométrico, la clave se protege con el Android Keystore de tu dispositivo; tu huella nunca es accesible para la aplicación.

## Conexiones de red

Offlock solo realiza una conexión de red, **opcional y iniciada manualmente por ti**: la comprobación de contraseñas filtradas con el servicio Have I Been Pwned (api.pwnedpasswords.com). Esta consulta usa k-anonimato: se envían únicamente los 5 primeros caracteres del hash SHA-1 de la contraseña — **la contraseña nunca se transmite**, ni completa ni identificable. No se envía ningún otro dato (ni usuarios, ni sitios, ni identificadores del dispositivo).

El resto de la aplicación funciona completamente sin conexión.

## Servicios del sistema (autofill y passkeys)

Cuando Offlock actúa como servicio de autorrellenado o proveedor de passkeys, el intercambio de credenciales ocurre localmente entre la aplicación y el sistema Android. Esos datos no salen del dispositivo.

## Permisos utilizados

- **Cámara**: solo para escanear códigos QR de verificación en dos pasos, a petición tuya. Las imágenes no se guardan ni se envían.
- **Internet**: solo para la comprobación opcional de filtraciones descrita arriba.
- **Biometría**: para el desbloqueo con huella, gestionado por el sistema.

## Datos de terceros

Offlock no integra SDKs de terceros de analítica, publicidad ni rastreo. El código es abierto y puede auditarse en https://github.com/Chris46036/PassVault

## Eliminación de datos

Todos los datos se eliminan al desinstalar la aplicación o al borrar sus datos desde los ajustes de Android. No existe ninguna copia en servidores porque nunca se envió nada.

## Cambios en esta política

Cualquier cambio se publicará en esta misma página, con la fecha de actualización al inicio.

## Contacto

Para preguntas sobre esta política, abre un issue en https://github.com/Chris46036/PassVault/issues

---

# Offlock Privacy Policy (English)

**Last updated: June 11, 2026**

Offlock is a zero-knowledge password manager developed by Chris46036. This policy describes how the Offlock Android app (`com.passvault.app`) handles your data.

## Summary

**Offlock does not collect, transmit, sell, or share any personal data.** There are no user accounts, no developer servers, no analytics, no ads, and no trackers of any kind.

## Data stored by the app

Everything you save in Offlock (passwords, 2FA codes, cards, notes, identities, passkeys and attachments) is stored **only on your device**, inside the app's private storage, encrypted with AES-256-GCM using a key derived from your master password with Argon2id.

- Your master password is **never stored** anywhere.
- The developer has no technical access to your data and no way to recover it.
- Exported backups (`.pvlt` files) keep the same encryption.
- If you enable biometric unlock, the key is protected by your device's Android Keystore; your fingerprint is never accessible to the app.

## Network connections

Offlock makes only one network connection, **optional and manually triggered by you**: checking for breached passwords against Have I Been Pwned (api.pwnedpasswords.com). This query uses k-anonymity: only the first 5 characters of the password's SHA-1 hash are sent — **the password itself is never transmitted**. No other data is sent (no usernames, sites, or device identifiers).

Everything else works fully offline.

## System services (autofill and passkeys)

When Offlock acts as the autofill service or passkey provider, credentials are exchanged locally between the app and the Android system. That data never leaves the device.

## Permissions used

- **Camera**: only to scan two-step verification QR codes, at your request. Images are not stored or sent.
- **Internet**: only for the optional breach check described above.
- **Biometrics**: for fingerprint unlock, managed by the system.

## Third-party data

Offlock includes no third-party analytics, advertising or tracking SDKs. The code is open source and auditable at https://github.com/Chris46036/PassVault

## Data deletion

All data is removed when you uninstall the app or clear its data from Android settings. There are no server copies because nothing was ever sent.

## Changes to this policy

Any changes will be published on this page, with the update date at the top.

## Contact

For questions about this policy, open an issue at https://github.com/Chris46036/PassVault/issues
