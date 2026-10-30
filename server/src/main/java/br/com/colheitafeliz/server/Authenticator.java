package br.com.colheitafeliz.server;

import java.util.UUID;

interface Authenticator extends AutoCloseable {
    record Identity(UUID id, String name) {}
    Identity authenticate(String authorization);
    @Override default void close() {}
}
