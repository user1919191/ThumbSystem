package com.example.demo.manager.Cache;


import com.example.demo.manager.Cache.AddResult;
import com.example.demo.manager.Cache.Item;

import java.util.List;
import java.util.concurrent.BlockingQueue;

public interface TopK {
    AddResult add(String key, int increment);
    List<com.example.demo.manager.Cache.Item> list();
    BlockingQueue<Item> expelled();
    void fading();
    long total();
}