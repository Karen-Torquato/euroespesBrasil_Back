package org.example.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    /** Armazenada sempre como hash BCrypt — nunca em texto puro */
    @Column(nullable = false)
    private String senha;

    /** Ex.: ROLE_ADMIN, ROLE_USER */
    @Column(nullable = false)
    private String role;
}

