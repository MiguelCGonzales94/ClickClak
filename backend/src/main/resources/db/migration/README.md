# Migraciones Flyway

Nombrar `V<version>__<descripcion>.sql`, ej: `V1__crea_tabla_usuarios.sql`. Flyway las aplica en orden ascendente y una sola vez — nunca editar una migración ya aplicada, crear una nueva.
