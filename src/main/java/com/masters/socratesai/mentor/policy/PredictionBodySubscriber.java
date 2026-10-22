package com.masters.socratesai.mentor.policy;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Fixed memory ceiling before parsing even a chunked error response. */
final class PredictionBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private Flow.Subscription subscription;
    public CompletionStage<byte[]> getBody() { return body; }
    public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
    public void onNext(List<ByteBuffer> buffers) {
        if (buffers.stream().mapToLong(ByteBuffer::remaining).sum() > 65536 - bytes.size()) {
            subscription.cancel(); body.completeExceptionally(new IllegalStateException("Invalid inference response")); return;
        }
        buffers.forEach(buffer -> { var chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk); }); subscription.request(1);
    }
    public void onError(Throwable ignored) { body.completeExceptionally(new IllegalStateException("Inference transport unavailable")); }
    public void onComplete() { body.complete(bytes.toByteArray()); }
}
