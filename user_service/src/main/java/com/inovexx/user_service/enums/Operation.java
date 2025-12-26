package com.inovexx.user_service.enums;

import jakarta.annotation.Nullable;

public enum Operation {

    /**
     *  operationType: DEPOSIT or WITHDRAW
     */
    DEPOSIT,
    WITHDRAW;


    @Nullable
    public static Operation parse (String operation) {
        for (Operation o : values()) {
            if (o.name().equals(operation)) {
                return o;
            }
        }
        return null;
    }
}