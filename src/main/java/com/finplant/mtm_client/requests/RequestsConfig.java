package com.finplant.mtm_client.requests;

import java.util.Map;

import com.finplant.mtm_client.RpcClient;
import com.finplant.mtm_client.dto.ConCommon;
import com.finplant.mtm_client.dto.ConGroup;

import com.finplant.mtm_client.dto.ConManager;
import reactor.core.publisher.Mono;

public class RequestsConfig {
    private final RpcClient client;

    public RequestsConfig(RpcClient client) {
        this.client = client;
    }

    public Mono<ConCommon> getCommon() {
        return client.request("config.common.get", ConCommon.class);
    }

    public Mono<Void> setCommon(ConCommon common) {
        return client.request("config.common.set", common);
    }

    public Mono<ConGroup> getGroup(String group) {
        return client.request("config.group.get", Map.of("group", group), ConGroup.class);
    }

    public Mono<Void> addGroup(ConGroup group) {
        return client.request("config.group.add", group);
    }

    public Mono<Void> setGroup(ConGroup group) {
        return client.request("config.group.set", group);
    }

    public Mono<Void> deleteGroup(String group) {
        return client.request("config.group.del", Map.of("group", group));
    }

    public Mono<Void> addManager(ConManager manager) {
        return client.request("config.manager.set", manager);
    }

    public Mono<ConManager> getManager(Integer login) {
        return client.request("config.manager.get", Map.of("login", login), ConManager.class);
    }

    public Mono<Void> deleteManager(Integer login) {
        return client.request("config.manager.del", Map.of("login", login));
    }
}
