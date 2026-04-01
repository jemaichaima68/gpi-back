package com.gpi.gpitracker.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UserCreateRequest {
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String password;

    // Champ téléphone
    private String phone;

    // Autres champs optionnels
    private LocalDate birthDate;
    private String address;
    private String city;
    private String postalCode;
    private String country;
}