/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.signer.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.function.Consumer;

import static org.niis.xroad.signer.grpc.ServerCredentialsConfigurer.createServerCredentials;

/**
 * Server that manages startup/shutdown of RPC server.
 */
@Slf4j
public class RpcServer {
    private final Server server;

    public RpcServer(final String host, final int port, final ServerCredentials creds, final Consumer<ServerBuilder<?>> configFunc) {
        ServerBuilder<?> builder = NettyServerBuilder.forAddress(new InetSocketAddress(host, port), creds);
        configFunc.accept(builder);

        server = builder.build();
    }

    public void start() throws IOException {
        server.start();

        log.info("RPC server has started, listening on {}", server.getListenSockets());
    }

    public void shutdown() {
        if (server != null) {
            log.info("Shutting down RPC server..");
            server.shutdown();
            log.info("Shutting down RPC server.. Success!");
        }
    }

    public static RpcServer newServer(String host, int port, Consumer<ServerBuilder<?>> configFunc)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        var serverCredentials = createServerCredentials();
        log.info("Initializing RPC server with {} credentials..", serverCredentials.getClass().getSimpleName());

        return new RpcServer(host, port, serverCredentials, configFunc);
    }


}
