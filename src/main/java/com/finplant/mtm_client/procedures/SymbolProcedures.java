package com.finplant.mtm_client.procedures;

import java.math.BigDecimal;
import java.util.Map;

import com.finplant.mtm_client.RpcClient;
import com.finplant.mtm_client.dto.Tick;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SymbolProcedures {

    public static final int CALL_TIMEOUT_S = 10;

    private final RpcClient client;

    public SymbolProcedures(RpcClient client) {
        this.client = client;
    }

    public Mono<Void> addTick(String symbol, BigDecimal bid, BigDecimal ask) {
        return client.call("symbol.tick.add", Map.of("symbol", symbol, "bid", bid, "ask", ask));
    }

    public Flux<Tick> subscribe() {
        return client.subscribe("tick", Tick.class);
    }

    public Mono<Tick> showSymbol(String symbol) {
        return client.call("symbol.show", Map.of("symbol", symbol), Tick.class);
    }

    public Mono<Tick> hideSymbol(String symbol) {
        return client.call("symbol.hide", Map.of("symbol", symbol), Tick.class);
    }
}
