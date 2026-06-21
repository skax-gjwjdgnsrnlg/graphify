package com.graphify.toss.dto;

public record TossAccountDto(
        String accountNumber,
        String accountName,
        double balance,
        double availableBalance
) {}
