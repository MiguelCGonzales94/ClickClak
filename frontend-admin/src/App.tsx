import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { EstructuraPanel } from "./components/EstructuraPanel";
import { PaginaAsignaciones } from "./pages/PaginaAsignaciones";
import { PaginaDashboard } from "./pages/PaginaDashboard";
import { PaginaInicioSesion } from "./pages/PaginaInicioSesion";
import { PaginaSedes } from "./pages/PaginaSedes";
import { PaginaTurnos } from "./pages/PaginaTurnos";
import { PaginaUsuarios } from "./pages/PaginaUsuarios";
import { RutaProtegida } from "./routes/RutaProtegida";
import { ProveedorSesion } from "./store/ContextoSesion";

const ROLES_ADMIN = ["SUPERVISOR", "RRHH_ADMIN"];

export default function App() {
  return (
    <ProveedorSesion>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<PaginaInicioSesion />} />
          <Route element={<RutaProtegida rolesPermitidos={ROLES_ADMIN} />}>
            <Route element={<EstructuraPanel />}>
              <Route path="/" element={<PaginaDashboard />} />
              <Route path="/usuarios" element={<PaginaUsuarios />} />
              <Route path="/sedes" element={<PaginaSedes />} />
              <Route path="/turnos" element={<PaginaTurnos />} />
              <Route path="/asignaciones" element={<PaginaAsignaciones />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ProveedorSesion>
  );
}
