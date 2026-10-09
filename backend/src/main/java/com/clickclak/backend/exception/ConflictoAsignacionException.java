package com.clickclak.backend.exception;

/** El colaborador ya tiene esa misma sede asignada en fechas que se cruzan; sedes distintas sí pueden coexistir. */
public class ConflictoAsignacionException extends RuntimeException {
    public ConflictoAsignacionException(Long usuarioId, String nombreSede) {
        super("El usuario " + usuarioId + " ya tiene asignada la sede \"" + nombreSede
                + "\" en ese rango de fechas; edite esa asignación en lugar de crear otra");
    }
}
