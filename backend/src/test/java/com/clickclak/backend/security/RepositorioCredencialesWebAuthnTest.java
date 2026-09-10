package com.clickclak.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.clickclak.backend.model.Dispositivo;
import com.clickclak.backend.model.Rol;
import com.clickclak.backend.model.Usuario;
import com.clickclak.backend.repository.DispositivoRepository;
import com.clickclak.backend.repository.UsuarioRepository;
import com.yubico.webauthn.data.ByteArray;

@ExtendWith(MockitoExtension.class)
class RepositorioCredencialesWebAuthnTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private DispositivoRepository dispositivoRepository;

    private RepositorioCredencialesWebAuthn repositorio;

    private Usuario usuario;
    private Dispositivo dispositivo;
    private ByteArray idCredencial;

    @BeforeEach
    void configurar() {
        repositorio = new RepositorioCredencialesWebAuthn(usuarioRepository, dispositivoRepository);

        usuario = Usuario.builder().id(7L).correo("tecnico@example.com").activo(true)
                .rol(Rol.builder().id(1L).nombre(Rol.COLABORADOR).build())
                .build();

        idCredencial = new ByteArray(new byte[] {1, 2, 3, 4});
        dispositivo = Dispositivo.builder().id(1L).usuario(usuario).activo(true)
                .credentialId(idCredencial.getBase64Url())
                .clavePublica(new ByteArray(new byte[] {5, 6, 7}).getBase64())
                .contadorFirma(3L)
                .build();
    }

    @Test
    void getCredentialIdsForUsername_devuelveLosDispositivosActivosDelUsuario() {
        when(usuarioRepository.findByCorreo("tecnico@example.com")).thenReturn(Optional.of(usuario));
        when(dispositivoRepository.findByUsuarioIdAndActivoTrue(7L)).thenReturn(List.of(dispositivo));

        var descriptores = repositorio.getCredentialIdsForUsername("tecnico@example.com");

        assertThat(descriptores).hasSize(1);
        assertThat(descriptores.iterator().next().getId()).isEqualTo(idCredencial);
    }

    @Test
    void getCredentialIdsForUsername_conCorreoDesconocido_devuelveConjuntoVacio() {
        when(usuarioRepository.findByCorreo("nadie@example.com")).thenReturn(Optional.empty());

        assertThat(repositorio.getCredentialIdsForUsername("nadie@example.com")).isEmpty();
    }

    @Test
    void getUserHandleForUsername_devuelveElIdDelUsuarioComoBytes() {
        when(usuarioRepository.findByCorreo("tecnico@example.com")).thenReturn(Optional.of(usuario));

        var manejador = repositorio.getUserHandleForUsername("tecnico@example.com");

        assertThat(manejador).contains(RepositorioCredencialesWebAuthn.idUsuarioComoManejador(7L));
    }

    @Test
    void getUsernameForUserHandle_esElInversoDeIdUsuarioComoManejador() {
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));

        var correo = repositorio.getUsernameForUserHandle(RepositorioCredencialesWebAuthn.idUsuarioComoManejador(7L));

        assertThat(correo).contains("tecnico@example.com");
    }

    @Test
    void getUsernameForUserHandle_conManejadorNoNumerico_devuelveVacio() {
        var manejadorInvalido = new ByteArray("no-es-un-numero".getBytes());

        assertThat(repositorio.getUsernameForUserHandle(manejadorInvalido)).isEmpty();
    }

    @Test
    void lookup_conCredencialYUsuarioCorrectos_devuelveLaCredencialRegistrada() {
        when(dispositivoRepository.findByCredentialId(idCredencial.getBase64Url())).thenReturn(Optional.of(dispositivo));

        var credencial = repositorio.lookup(idCredencial, RepositorioCredencialesWebAuthn.idUsuarioComoManejador(7L));

        assertThat(credencial).isPresent();
        assertThat(credencial.get().getSignatureCount()).isEqualTo(3L);
    }

    @Test
    void lookup_conUsuarioQueNoCoincide_devuelveVacio() {
        when(dispositivoRepository.findByCredentialId(idCredencial.getBase64Url())).thenReturn(Optional.of(dispositivo));

        var credencial = repositorio.lookup(idCredencial, RepositorioCredencialesWebAuthn.idUsuarioComoManejador(999L));

        assertThat(credencial).isEmpty();
    }

    @Test
    void lookup_conDispositivoInactivo_devuelveVacio() {
        dispositivo.setActivo(false);
        when(dispositivoRepository.findByCredentialId(idCredencial.getBase64Url())).thenReturn(Optional.of(dispositivo));

        assertThat(repositorio.lookup(idCredencial, RepositorioCredencialesWebAuthn.idUsuarioComoManejador(7L))).isEmpty();
    }

    @Test
    void lookupAll_devuelveUnConjuntoConLaCredencial() {
        when(dispositivoRepository.findByCredentialId(idCredencial.getBase64Url())).thenReturn(Optional.of(dispositivo));

        assertThat(repositorio.lookupAll(idCredencial)).hasSize(1);
    }
}
