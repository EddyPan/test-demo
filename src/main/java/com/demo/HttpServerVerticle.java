package com.demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServerVerticle extends AbstractVerticle {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    private HttpServer hs;
    private WebClient webClient;
    private HttpClient httpClient;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        super.start(startFuture);


        hs = vertx.createHttpServer();
        webClient = WebClient.create(vertx);
        httpClient = vertx.createHttpClient();

        Router router = Router.router(vertx);
        router.route().path("/user/admin").method(HttpMethod.POST)
                .handler(rct -> {

                    // 用户请求的request
                    HttpServerRequest request = rct.request().setExpectMultipart(true);

                    // 用户请求的Header
                    MultiMap headers = request.headers();

                    JsonObject param = new JsonObject().put("requestUrl", "http://localhost:18080/authorize")
                            .put("httpMethod", "POST");

                    webClient.postAbs("http://localhost:18080/authorize")
                            .timeout(6000)
                            .putHeader("Content-Type", "application/json")
                            .putHeader("Authorization", headers.get("Authorization"))
                            .as(BodyCodec.jsonObject())
                            .sendJsonObject(param, ar -> authHandler(rct, ar));


                });

        router.route().path("/user/admin").method(HttpMethod.POST)
                .handler(rct -> {

                    // 用户请求的request
                    HttpServerRequest request = rct.request().setExpectMultipart(true);

                    HttpServerResponse response = rct.response()
                            .putHeader("Content-Type", "application/json");

                    // 用户请求的Header
                    MultiMap rctHeaders = request.headers();

                    HttpClientRequest clientRequest = httpClient.requestAbs(HttpMethod.POST, "http://localhost:18080/user/admin").setTimeout(6000);


                    clientRequest.exceptionHandler(e -> {
                        logger.error("Connect to backend timeout.", e);
                        response.setStatusCode(408).end("Connect to backend error.");
                    });

                    clientRequest.handler(resp -> {
                        response.setChunked(true);
                        Pump respPump = Pump.pump(resp, response);
                        respPump.start();
                        resp.endHandler(end -> {
                            response.end();
                        });
                        resp.exceptionHandler(e -> {
                            logger.error("resp pump error", e);
                            respPump.stop();
                            clientRequest.end();
                        });
                    });


                    clientRequest.putHeader("Content-Type", "application/json");

                    clientRequest.setChunked(true);
                    Pump reqPump = Pump.pump(request, clientRequest);
                    reqPump.start();

                    request.exceptionHandler(e -> {
                        logger.error("request pump error", e);
                        reqPump.stop();
                        clientRequest.end();
                    });
                    request.endHandler(end -> {
                        clientRequest.end();
                    });

                });


        router.route().path("/user/admin").method(HttpMethod.POST).failureHandler(rct -> {
//            rct.request().setExpectMultipart(true);
            Throwable failure = rct.failure();
            logger.error("Handling failure:", failure);

            rct.response().putHeader("Content-Type", "application/json")
                    .setStatusCode(500).end("Internal error");
        });

        hs.requestHandler(router).listen(5555);
        logger.info("Start http server on port: 5555");
    }


    private void authHandler(RoutingContext rct, AsyncResult<HttpResponse<JsonObject>> ar) {
        if (ar.succeeded()) {
            HttpResponse<JsonObject> response = ar.result();
            JsonObject result = response.body();
            Integer status = result.getInteger("status");
            if (status != null && status == 200) {
                logger.info("Auth passed");
                rct.next();

            } else {

                rct.response().putHeader("Content-Type", "application/json").end(result.toString());
            }
        } else {
            JsonObject err = new JsonObject().put("code", 500).put("msg", "Auth error message：" + ar.cause().getMessage());
            rct.response().putHeader("Content-Type", "application/json").end(err.toString());
        }
    }
}
