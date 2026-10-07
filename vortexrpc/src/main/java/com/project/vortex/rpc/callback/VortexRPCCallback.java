package com.project.vortex.rpc.callback;

import com.google.gson.JsonObject;

public interface VortexRPCCallback {

    void onReady(JsonObject user);

    void onDisconnected();

    void onError(Exception error);
}