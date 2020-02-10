package com.finplant.mt_remote_client;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.finplant.mt_remote_client.dto.common.Request;
import com.finplant.mt_remote_client.dto.common.Subscription;
import com.finplant.mt_remote_client.dto.jackson.BytesMapping;
import com.finplant.mt_remote_client.dto.jackson.LocalDateTimeMapping;
import com.finplant.mt_remote_client.dto.jackson.MonthMapping;
import com.finplant.mt_remote_client.dto.jackson.ZoneOffsetMapping;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

@Slf4j
public class RpcClient implements AutoCloseable {

    private final WsClient client;

    private final Map<Long, MonoSink<JsonNode>> requests = new ConcurrentHashMap<>();
    private final Map<String, FluxSink<JsonNode>> subscriptions = new ConcurrentHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong idCounter = new AtomicLong(0);
    private final Disposable.Composite disposables = Disposables.composite();

    public RpcClient(WsClient client) {
        this.client = client;

        val messagesDisposable = client.listen()
                                       .map(this::toJsonNode)
                                       .doOnError(this::logMessageError)
                                       .retry()
                                       .subscribe(this::onMessage, this::logMessageError);
        disposables.add(messagesDisposable);

        val disconnectDisposable = client.connectionStatus()
                                         .filter(connected -> !connected)
                                         .subscribe(connected -> cleanup());
        disposables.add(disconnectDisposable);

        initializeMapper();
    }

    @Override
    public void close() {
        disposables.dispose();
    }

    public Mono<Void> connect(URI url) {
        return client.connect(url);
    }

    public Flux<Boolean> connection() {
        return client.connection();
    }

    public Mono<Void> disconnect() {
        return client.disconnect();
    }

    public <I, T> Mono<T> call(String method, I payload, Class<T> resultClass) {
        return doCall(method, payload).map(response -> parse(response, resultClass));
    }

    public <I, T> Mono<T> call(String method, I payload, TypeReference<T> typeReference) {
        return doCall(method, payload).map(response -> parse(response, typeReference));
    }

    public <I, T> Mono<T> call(String method, TypeReference<T> typeReference) {
        return doCall(method, null).map(response -> parse(response, typeReference));
    }

    public <T> Mono<T> call(String method, Class<T> resultClass) {
        return doCall(method, null).map(response -> parse(response, resultClass));
    }

    public <T> Mono<Void> call(String method, T payload) {
        return doCall(method, payload).then();
    }

    public Mono<Void> call(String method) {
        return doCall(method, null).then();
    }

    public <T> Flux<T> subscribe(String subscription, Class<T> eventClass) {
        return doSubscription(subscription).map(json -> parse(json, eventClass));
    }

    private void cleanup() {

        log.debug("Cleanup");

        requests.values().forEach(MonoSink::success);
        requests.clear();
        subscriptions.values().forEach(FluxSink::complete);
        subscriptions.clear();
    }

    private <T> Mono<JsonNode> doCall(String method, T payload) {

        return Mono.defer(() -> {

            long id = idCounter.getAndIncrement();
            val json = makeJson(method, payload, id);

            val storeRequest = Mono.<JsonNode>create(sink -> {
                requests.put(id, sink);
                sink.onDispose(() -> requests.remove(id));
            });

            return storeRequest.delaySubscription(client.send(json));
        });
    }

    private Flux<JsonNode> doSubscription(String name) {

        return Flux.defer(() -> {

            val subscribeRequest = call("subscribe", new Subscription(name));
            val unSubscribeRequest = call("unsubscribe", new Subscription(name));

            val storeSubscription = Flux.<JsonNode>create(sink -> {
                subscriptions.put(name, sink);
                sink.onDispose(unSubscribeRequest::subscribe);
            });

            return storeSubscription.delaySubscription(subscribeRequest);
        });
    }

    @SneakyThrows
    private <T> T parse(JsonNode json, Class<T> resultClass) {
        return mapper.treeToValue(json, resultClass);
    }

    @SneakyThrows
    private <T> T parse(JsonNode json, TypeReference<T> typeReference) {
        val javaType = mapper.getTypeFactory().constructType(typeReference);
        return mapper.readValue(mapper.treeAsTokens(json), javaType);
    }

    @SneakyThrows
    private <T> String makeJson(String method, T payload, long id) {
        val request = new Request<>(method, id, payload);
        return mapper.writeValueAsString(request);
    }

    @SneakyThrows
    private void onMessage(JsonNode json) {

        if (handleEventIfExists(json)) {
            return;
        }

        handleResponseIfExists(json);
    }

    private boolean handleEventIfExists(JsonNode json) {
        val eventNode = json.get("event");
        if (eventNode == null) {
            return false;
        }

        val sink = subscriptions.get(eventNode.asText());
        if (sink == null) {
            throw new IllegalStateException(String.format("Got unsubscribed event: %s", json));
        }

        val data = json.get("data");
        sink.next(data);

        return true;
    }

    private void handleResponseIfExists(JsonNode json) {
        JsonNode idNode = json.get("id");
        if (idNode == null) {
            throw new IllegalArgumentException("Message has no mandatory field 'id'");
        }

        val id = idNode.asLong();
        val request = requests.get(id);
        if (request == null) {
            throw new IllegalArgumentException(String.format("Unexpected response: %s", json));
        }

        val status = json.get("status").asText();
        if (!status.equals("OK")) {

            val message = json.get("message");
            if (message == null) {
                request.error(new Errors.MtmError("Missing 'message' in error response"));
                return;
            }

            request.error(new Errors.MtmError(message.asText()));
            return;
        }

        val result = json.get("result");
        if (result == null) {
            request.success();
            return;
        }

        request.success(result);
    }

    @SneakyThrows
    private JsonNode toJsonNode(String message) {
        return mapper.readTree(message);
    }

    private void initializeMapper() {
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        mapper.enable(SerializationFeature.WRITE_ENUMS_USING_INDEX);

        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(Month.class, new MonthMapping.Serialize());
        simpleModule.addDeserializer(Month.class, new MonthMapping.Deserializer());
        simpleModule.addSerializer(LocalDateTime.class, new LocalDateTimeMapping.Serialize());
        simpleModule.addDeserializer(LocalDateTime.class, new LocalDateTimeMapping.Deserializer());
        simpleModule.addSerializer(ZoneOffset.class, new ZoneOffsetMapping.Serialize());
        simpleModule.addDeserializer(ZoneOffset.class, new ZoneOffsetMapping.Deserializer());
        simpleModule.addSerializer(byte[].class, new BytesMapping.Serialize());
        simpleModule.addDeserializer(byte[].class, new BytesMapping.Deserializer());
        mapper.registerModule(simpleModule);
    }

    private void logMessageError(Throwable throwable) {
        log.error("Error handling message", throwable);
    }
}
