package com.subwayticket.extractor.http;

import com.subwayticket.model.result.Result;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

/**
 * Created by shengyun-zhou on 6/10/16.
 */
public class RESTfulAPIUtil {
    public static final String API_BASE_URL_V1 = "http://101.200.144.204:16080/subway-ticket-web/mobileapi/v1";

    public static WebTarget getWebTarget(String url){
        Client client = ClientBuilder.newClient();
        client.register(GsonProvider.class);
        return client.target(url);
    }

    public static Invocation.Builder getRequestBuilder(String url, String token){
        WebTarget target = getWebTarget(url);
        Invocation.Builder builder = target.request();
        if(token != null)
            builder.header("AuthToken", token);
        return builder;
    }

    public static Response get(String url, String token){
        return getRequestBuilder(url, token).buildGet().invoke();
    }

    public static Response delete(String url, String token){
        return getRequestBuilder(url, token).buildDelete().invoke();
    }

    public static Response post(String url, Object jsonEntity, String token){
        return getRequestBuilder(url, token).buildPost(Entity.json(jsonEntity)).invoke();
    }

    public static Response put(String url, Object jsonEntity, String token){
        if(jsonEntity == null)
            return getRequestBuilder(url, token).buildPut(Entity.text("")).invoke();
        return getRequestBuilder(url, token).buildPut(Entity.json(jsonEntity)).invoke();
    }

    public static Result parseResponse(Response response){
        return parseResponse(response, Result.class);
    }

    public static Result parseResponse(Response response, Class<? extends Result> entityClass){
        if(response.getStatus() == Response.Status.NO_CONTENT.getStatusCode())
            return null;
        return response.readEntity(entityClass);
    }
}
