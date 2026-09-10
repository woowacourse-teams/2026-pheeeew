package com.pheeeew.device.infra.jwt;

public final class AccessTokenContract {

    public static final String ISSUER = "https://pheeeew.com";
    public static final String TOKEN_TYPE = "JWT";
    public static final String TOKEN_USE_CLAIM = "use";
    public static final String ACCESS_TOKEN_USE = "ACCESS";

    private AccessTokenContract() {
    }
}
