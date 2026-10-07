package org.example.services;

import org.example.exceptions.BadRequestException;
import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.models.AuditoriaEvento;
import org.example.models.Permissao;
import org.example.models.Usuario;
import org.example.models.UsuarioPermissao;
import org.example.models.UsuarioPermissaoId;
import org.example.repositories.AuditoriaEventoRepository;
import org.example.repositories.PermissaoRepository;
import org.example.repositories.UsuarioPermissaoRepository;
import org.example.repositories.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class AdminManagementService {

    private final UsuarioRepository usuarioRepository;
    private final PermissaoRepository permissaoRepository;
    private final UsuarioPermissaoRepository usuarioPermissaoRepository;
    private final AuditoriaEventoRepository auditoriaEventoRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminManagementService(UsuarioRepository usuarioRepository,
                                  PermissaoRepository permissaoRepository,
                                  UsuarioPermissaoRepository usuarioPermissaoRepository,
                                  AuditoriaEventoRepository auditoriaEventoRepository,
                                  PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.permissaoRepository = permissaoRepository;
        this.usuarioPermissaoRepository = usuarioPermissaoRepository;
        this.auditoriaEventoRepository = auditoriaEventoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResumo> listarUsuarios() {
        return usuarioRepository.findAll().stream()
                .sorted(Comparator.comparing(Usuario::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResumo)
                .toList();
    }

    @Transactional
    public UsuarioResumo criarUsuario(CriarUsuarioRequest request) {
        validarUsuario(request);
        usuarioRepository.findByUsername(request.username().trim()).ifPresent(existing -> {
            throw new ConflictException("Já existe um usuário com este username");
        });

        Usuario usuario = new Usuario();
        usuario.setUsername(request.username().trim());
        usuario.setSenha(passwordEncoder.encode(request.senha()));
        usuario.setRole(request.role().trim());
        usuario.setAtivo(request.ativo() == null || request.ativo());
        usuario = usuarioRepository.save(usuario);

        atribuirPermissoes(usuario, request.permissoes());
        return toResumo(usuario);
    }

    @Transactional
    public UsuarioResumo atualizarUsuario(Long id, AtualizarUsuarioRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        if (request.username() != null && !request.username().isBlank()) {
            String novoUsername = request.username().trim();
            usuarioRepository.findByUsername(novoUsername)
                    .filter(outro -> !outro.getId().equals(id))
                    .ifPresent(outro -> {
                        throw new ConflictException("Já existe um usuário com este username");
                    });
            usuario.setUsername(novoUsername);
        }
        if (request.senha() != null && !request.senha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(request.senha()));
        }
        if (request.role() != null && !request.role().isBlank()) {
            usuario.setRole(request.role().trim());
        }
        if (request.ativo() != null) {
            usuario.setAtivo(request.ativo());
        }

        usuarioRepository.save(usuario);
        if (request.permissoes() != null) {
            atribuirPermissoes(usuario, request.permissoes());
        }
        return toResumo(usuario);
    }

    @Transactional
    public void removerUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        usuarioPermissaoRepository.deleteAll(
                usuarioPermissaoRepository.findAllByUsuarioId(usuario.getId())
        );
        usuarioRepository.delete(usuario);
    }

    public List<Permissao> listarPermissoes() {
        return permissaoRepository.findAll().stream()
                .sorted(Comparator.comparing(Permissao::getModulo).thenComparing(Permissao::getAcao))
                .toList();
    }

    public List<AuditoriaEvento> listarAuditoria() {
        return auditoriaEventoRepository.findAll().stream()
                .sorted(Comparator.comparing(AuditoriaEvento::getOcorridoEm).reversed())
                .limit(200)
                .toList();
    }

    @Transactional
    public void atribuirPermissoesUsuario(Long usuarioId, List<String> chaves) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        atribuirPermissoes(usuario, chaves);
    }

    private void atribuirPermissoes(Usuario usuario, List<String> chaves) {
        usuarioPermissaoRepository.deleteAll(
                usuarioPermissaoRepository.findAllByUsuarioId(usuario.getId())
        );

        if (chaves == null || chaves.isEmpty()) {
            return;
        }

        for (String chave : chaves) {
            if (chave == null || chave.isBlank()) {
                continue;
            }
            Permissao permissao = permissaoRepository.findByChave(chave.trim())
                    .orElseThrow(() -> new NotFoundException("Permissão não encontrada: " + chave));
            UsuarioPermissao usuarioPermissao = new UsuarioPermissao(
                    new UsuarioPermissaoId(usuario.getId(), permissao.getId()),
                    usuario,
                    permissao,
                    null,
                    null
            );
            usuarioPermissaoRepository.save(usuarioPermissao);
        }
    }

    private UsuarioResumo toResumo(Usuario usuario) {
        List<String> permissoes = usuarioPermissaoRepository.findAllByUsuarioId(usuario.getId()).stream()
                .map(item -> item.getPermissao().getChave())
                .sorted()
                .toList();
        return new UsuarioResumo(usuario.getId(), usuario.getUsername(), usuario.getRole(), usuario.isAtivo(), permissoes);
    }

    private void validarUsuario(CriarUsuarioRequest request) {
        if (request == null) {
            throw new BadRequestException("Usuário é obrigatório");
        }
        if (request.username() == null || request.username().isBlank()) {
            throw new BadRequestException("Username é obrigatório");
        }
        if (request.senha() == null || request.senha().isBlank()) {
            throw new BadRequestException("Senha é obrigatória");
        }
        if (request.role() == null || request.role().isBlank()) {
            throw new BadRequestException("Perfil é obrigatório");
        }
    }

    public record CriarUsuarioRequest(String username, String senha, String role, Boolean ativo, List<String> permissoes) {}
    public record AtualizarUsuarioRequest(String username, String senha, String role, Boolean ativo, List<String> permissoes) {}
    public record UsuarioResumo(Long id, String username, String role, boolean ativo, List<String> permissoes) {}
}
