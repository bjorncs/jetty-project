package org.eclipse.jetty.embedded;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author bjorncs
 */
public class Bug {

    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);

        // Declare server handler collection
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        server.setHandler(contexts);

        // Configure context "/" (root) for servlets
        ServletContextHandler root = new ServletContextHandler(contexts, "/",
                ServletContextHandler.SESSIONS);
        // Add servlets to root context
        root.addServlet(new ServletHolder(new AsyncServlet()), "/async-servlet");

        server.start();
        runClients();
        server.stop();
    }

    private static void runClients() throws InterruptedException {
        ExecutorService executor = Executors.newCachedThreadPool();
        for (int i = 0; i < 8; i++) {
            executor.submit(() -> {
                HttpClientBuilder builder = HttpClientBuilder.create();
                builder.setConnectionManager(new PoolingHttpClientConnectionManager());
                try (CloseableHttpClient client = builder.build()) {
                    for (int j = 0; j < 50; j++) {
                        HttpPost httpPost = new HttpPost("http://localhost:8080/async-servlet");
                        InputStreamEntity reqEntity = new InputStreamEntity(new BlockingInputStream(16 * 1024), -1, ContentType.APPLICATION_OCTET_STREAM);
                        reqEntity.setChunked(true);
                        httpPost.setEntity(reqEntity);
                        client.execute(httpPost).close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.MINUTES);
    }


    @WebServlet(asyncSupported = true)
    private static class AsyncServlet extends HttpServlet {
        private final Executor executor = Executors.newFixedThreadPool(32);

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            AsyncContext asyncContext = req.startAsync(req, resp);
            asyncContext.setTimeout(0);
            Listener listener = new Listener(asyncContext, executor);
            req.getInputStream().setReadListener(listener);
        }


        private class Listener implements ReadListener, WriteListener {
            private static final int BUFFER_SIZE = 16 * 1024;
            private final byte[] buffer = new byte[BUFFER_SIZE];
            private final CompletableFuture<Void> inputComplete = new CompletableFuture<>();
            private final CompletableFuture<Void> outputComplete = new CompletableFuture<>();
            private final HttpServletResponse response;
            private final AtomicBoolean responseWritten = new AtomicBoolean(false);
            private final AtomicBoolean responseCommitted = new AtomicBoolean(false);
            private final AsyncContext asyncContext;
            private final Executor executor;

            private ServletOutputStream output;
            private ServletInputStream input;

            public Listener(AsyncContext asyncContext, Executor executor) throws IOException {
                this.asyncContext = asyncContext;
                this.executor = executor;
                this.input = asyncContext.getRequest().getInputStream();
                this.response = (HttpServletResponse) asyncContext.getResponse();
                this.output = response.getOutputStream();
                CompletableFuture.allOf(inputComplete, outputComplete)
                        .whenComplete((ignoredResult, ignoredThrowable) -> asyncContext.complete());

                // Assign write listener in current thread and ReadPendingException is no longer thrown
                executor.execute(() -> output.setWriteListener(this));
//                output.setWriteListener(this);
            }

            @Override
            public void onDataAvailable() throws IOException {
                while (input.isReady()) {
                    final int numBytesRead = input.read(buffer);
                    if (!responseCommitted.compareAndSet(false, true)) {
                        response.setContentType("text/plain;charset=utf-8");
                        response.setStatus(HttpServletResponse.SC_OK);
                    }
                    if (numBytesRead < 0) {
                        // End of stream; there should be no more data available, ever.
                        return;
                    }
                }
            }

            @Override
            public void onAllDataRead() throws IOException {
                inputComplete.complete(null);
            }

            @Override
            public void onWritePossible() throws IOException {
                executor.execute(() -> {
                    while (output.isReady()) {
                        if (responseWritten.compareAndSet(false, true)) {
                            try {
                                output.write("Hello world".getBytes());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            outputComplete.complete(null);
                            return;
                        }
                    }
                });
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                asyncContext.complete();
            }
        }
    }

    private static class BlockingInputStream extends ByteArrayInputStream {
        private static final int CHUNK_SIZE = 4242;
        public BlockingInputStream(int size) {
            super(createByteBuffer(size));
        }

        private static byte[] createByteBuffer(int size) {
            byte[] buffer = new byte[size];
            for (int i = 0; i < size; i++) {
                buffer[i] = (byte) i;
            }
            return buffer;
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            sleep();
            return super.read(b, off, chunk(len));
        }

        @Override
        public synchronized int available() {
            return chunk(super.available());
        }

        private static int chunk(int size) {
            return Math.min(size, CHUNK_SIZE);
        }

        private static void sleep() {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
