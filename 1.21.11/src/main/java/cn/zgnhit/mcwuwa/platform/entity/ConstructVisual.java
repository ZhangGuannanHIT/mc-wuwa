package cn.zgnhit.mcwuwa.platform.entity;

/** Common read-only presentation contract; no client class is loaded on a server. */
public interface ConstructVisual {
    String modelId();
    String animation();
    float visualScale();
    int durationTicks();
    long startTick();
    default float animationProgress(double elapsed) {
        return animation().equals("idle") ? (float)(Math.max(0,elapsed)%80)/80
            : (float)Math.clamp(elapsed/Math.max(1,durationTicks()),0,1);
    }
}
