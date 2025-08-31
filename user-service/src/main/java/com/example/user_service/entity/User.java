package com.example.user_service.entity;



import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // local DB id (internal reference)

    @Column(nullable = false, unique = true)
    private String keycloakUserId; // maps to Keycloak UUID (sub claim in JWT)

//    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
//    private List<Order> orders;

    private List <Long> orderIds; // List of order IDs for simplicity

//    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
//    private Cart cart;

    private int cartId; // Cart ID for simplicity

//    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
//    private List<Product> products;

    private List <Long> productIds; // List of product IDs for simplicity
}
