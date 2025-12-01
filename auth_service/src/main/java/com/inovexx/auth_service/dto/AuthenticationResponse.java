package com.inovexx.auth_service.dto;


import lombok.Getter;

import java.io.Serializable;

@Getter
public class AuthenticationResponse implements Serializable {

    private final String jwt;

    public AuthenticationResponse(String jwt) {
        this.jwt = jwt;
    }
}
