-- Semilla SOLO para desarrollo local (perfil `dev`): no se carga en producción porque
-- application-prod.yml no incluye este directorio en spring.flyway.locations.
-- Es una migración repetible: se vuelve a ejecutar si el archivo cambia y es idempotente.
--
-- Usuario administrador de desarrollo (rol RRHH_ADMIN, entra con correo y contraseña):
--   correo:     admin@clickclak.local
--   contraseña: CcTMgAZsXid07
-- La contraseña es solo para esta máquina; en producción se crea el administrador real
-- por el endpoint de usuarios y se descarta este.
INSERT INTO usuario (nombres, apellidos, tipo_documento, numero_documento, correo, password_hash, rol_id)
SELECT 'Administrador', 'Desarrollo', 'DNI', '00000000', 'admin@clickclak.local',
       '$2a$10$/vW9gMnOVx6eHu..VCYp5OpX4y8HRPqvseyTqGKJJ.hgMhVxzHPqG',
       rol.id
FROM rol
WHERE rol.nombre = 'RRHH_ADMIN'
ON CONFLICT (correo) DO NOTHING;
