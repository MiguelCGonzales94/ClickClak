package com.clickclak.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clickclak.backend.model.Dispositivo;
import com.clickclak.backend.repository.DispositivoRepository;

@Service
public class DispositivoService {

    private final DispositivoRepository dispositivoRepository;

    public DispositivoService(DispositivoRepository dispositivoRepository) {
        this.dispositivoRepository = dispositivoRepository;
    }

    @Transactional(readOnly = true)
    public List<Dispositivo> listarActivosPorUsuario(Long usuarioId) {
        return dispositivoRepository.findByUsuarioIdAndActivoTrue(usuarioId);
    }
}
