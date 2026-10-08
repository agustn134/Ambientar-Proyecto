package com.proyecto.servicios.service;

import com.proyecto.servicios.model.auth.*;
import com.proyecto.servicios.repositorys.usuario.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
public class LoginService {
    private static final int MAX_INTENTOS=3;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwords;
    private final JwtService tokens;
    private final String hashInexistente;

    public LoginService(UsuarioRepository usuarios, PasswordEncoder passwords, JwtService tokens) {
        this.usuarios=usuarios; this.passwords=passwords; this.tokens=tokens;
        this.hashInexistente=passwords.encode(UUID.randomUUID().toString());
    }

    /** Un rechazo se retorna, no se lanza aquí: la transacción debe confirmar el contador. */
    @Transactional("sfTransactionManager")
    public Optional<LoginResponse> autenticar(LoginRequest request) {
        var encontrado=usuarios.buscarParaLogin(request.correo());
        if (encontrado.isEmpty()) {
            passwords.matches(request.password(),hashInexistente);
            return Optional.empty();
        }
        var usuario=encontrado.get();
        if (!usuario.isActivo() || !"ACTIVO".equals(usuario.getCliente().getEstatus())) {
            passwords.matches(request.password(),hashInexistente);
            return Optional.empty();
        }
        if (!passwords.matches(request.password(),usuario.getPasswordHash())) {
            int intentos=Math.min(MAX_INTENTOS,usuario.getIntentosFallidos()+1);
            usuario.setIntentosFallidos(intentos);
            if (intentos == MAX_INTENTOS) {
                usuario.setActivo(false);
                usuario.setVersionToken(usuario.getVersionToken()+1);
            }
            usuarios.saveAndFlush(usuario);
            return Optional.empty();
        }
        usuario.setIntentosFallidos(0);
        usuarios.saveAndFlush(usuario);
        return Optional.of(tokens.emitir(usuario));
    }
}
