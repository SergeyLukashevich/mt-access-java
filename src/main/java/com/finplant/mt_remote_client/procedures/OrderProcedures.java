package com.finplant.mt_remote_client.procedures;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.finplant.mt_remote_client.RpcClient;
import com.finplant.mt_remote_client.dto.mt4.Mt4TradeRecord;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderProcedures {

    private final RpcClient client;

    public OrderProcedures(@NonNull RpcClient client) {
        this.client = client;
    }

    public Mono<Integer> open(@NonNull OpenOrderParameters parameters) {
        return client.call("order.open", parameters, Map.class).map(response -> (Integer) response.get("order"));
    }

    public Mono<Void> modify(@NonNull ModifyOrderParameters parameters) {
        return client.call("order.modify", parameters);
    }

    public Mono<Void> close(int order, BigDecimal price, BigDecimal volume) {
        return client.call("order.close", Map.of("order", order, "price", price, "volume", volume));
    }

    public Mono<Void> closeBy(int order, int orderBy) {
        return client.call("order.close_by", Map.of("order", order, "order_by", orderBy));
    }

    public Mono<Void> closeAll(int login, String symbol) {
        return client.call("order.close_all", Map.of("login", login, "symbol", symbol));
    }

    public Mono<Void> cancel(int order, Mt4TradeRecord.Command command) {
        return client.call("order.cancel", Map.of("order", order, "command", command));
    }

    public Mono<Void> activate(int order, BigDecimal price) {
        return client.call("order.activate", Map.of("order", order, "price", price));
    }

    public Mono<Integer> balance(@NonNull BalanceOrderParameters parameters) {
        return client.call("order.balance", parameters, Map.class).map(response -> (Integer) response.get("order"));
    }

    public Mono<Mt4TradeRecord> get(int order) {
        return client.call("trade.get", Map.of("order", order), Mt4TradeRecord.class);
    }

    public Flux<Mt4TradeRecord> getAll() {
        return client.call("trades.get", new TypeReference<List<Mt4TradeRecord>>() {}).flatMapMany(Flux::fromIterable);
    }

    public Flux<Mt4TradeRecord> getByLogin(int login, String group) {
        return client.call("trades.get.by_login",
                           Map.of("login", login, "group", group),
                           new TypeReference<List<Mt4TradeRecord>>() {})
                     .flatMapMany(Flux::fromIterable);
    }

    public Flux<Mt4TradeRecord> getHistory(int login, LocalDateTime from, LocalDateTime to) {

        var params = new HashMap<>();
        params.put("login", (long) login);
        if (from != null) {
            params.put("from", from.toEpochSecond(ZoneOffset.UTC));
        }
        if (to != null) {
            params.put("to", to.toEpochSecond(ZoneOffset.UTC));
        }

        return client.call("trades.history", params, new TypeReference<List<Mt4TradeRecord>>() {})
                     .flatMapMany(Flux::fromIterable);
    }

    public Flux<Mt4TradeRecord> listen() {
        return client.subscribe("trade", Mt4TradeRecord.class);
    }

    @Data
    @Builder(toBuilder = true)
    public static class OpenOrderParameters {

        @JsonProperty("login")
        private final Integer login;
        @JsonProperty("symbol")
        private final String symbol;
        @JsonProperty("command")
        private final Mt4TradeRecord.Command command;
        @JsonProperty("volume")
        private final BigDecimal volume;
        @JsonProperty("price")
        private final BigDecimal price;

        @JsonProperty("sl")
        private final BigDecimal stopLoss;
        @JsonProperty("tp")
        private final BigDecimal takeProfit;
        @JsonProperty("expiration")
        private final LocalDateTime expiration;
        @JsonProperty("comment")
        private final String comment;
    }

    @Data
    @Builder(toBuilder = true)
    public static class ModifyOrderParameters {

        @JsonProperty("order")
        private final Integer order;

        @JsonProperty("price")
        private final BigDecimal price;
        @JsonProperty("sl")
        private final BigDecimal stopLoss;
        @JsonProperty("tp")
        private final BigDecimal takeProfit;
        @JsonProperty("expiration")
        private final LocalDateTime expiration;
        @JsonProperty("comment")
        private final String comment;
    }

    @Data
    @Builder(toBuilder = true)
    public static class BalanceOrderParameters {
        @JsonProperty("login")
        private final Integer login;
        @JsonProperty("command")
        private final Mt4TradeRecord.Command command;
        @JsonProperty("amount")
        private final BigDecimal amount;

        @JsonProperty("expiration")
        private final LocalDateTime expiration;
        @JsonProperty("comment")
        private final String comment;
    }
}
