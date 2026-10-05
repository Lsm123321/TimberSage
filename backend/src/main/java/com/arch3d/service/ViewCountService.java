package com.arch3d.service;

public interface ViewCountService {
    boolean canCountView(String key);
    void recordView(String key);
    boolean hasViewed(String key);
}
