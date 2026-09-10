import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { PaginaAgenda } from "./pages/PaginaAgenda";
import { PaginaInicioSesion } from "./pages/PaginaInicioSesion";
import { PaginaRecuperarClave } from "./pages/PaginaRecuperarClave";
import { PaginaRegistrarDispositivo } from "./pages/PaginaRegistrarDispositivo";
import { RutaProtegida } from "./routes/RutaProtegida";
import { ProveedorSesion } from "./store/ContextoSesion";

export default function App() {
  return (
    <ProveedorSesion>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<PaginaInicioSesion />} />
          <Route path="/recuperar-clave" element={<PaginaRecuperarClave />} />
          <Route element={<RutaProtegida />}>
            <Route path="/agenda" element={<PaginaAgenda />} />
          </Route>
          <Route element={<RutaProtegida rolesPermitidos={["SUPERVISOR", "RRHH_ADMIN"]} />}>
            <Route path="/registrar-dispositivo" element={<PaginaRegistrarDispositivo />} />
          </Route>
          <Route path="*" element={<Navigate to="/agenda" replace />} />
        </Routes>
      </BrowserRouter>
    </ProveedorSesion>
  );
}
