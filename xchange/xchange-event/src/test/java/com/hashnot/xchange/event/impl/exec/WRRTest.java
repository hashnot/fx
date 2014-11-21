package com.hashnot.xchange.event.impl.exec;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WRRTest {

    private static List<String> wrr(Map<String, Integer> weights) {
        int min = Integer.MAX_VALUE;
        int count = 0;
        for (int weight : weights.values()) {
            min = Math.min(min, weight);
            count += weight;
        }
        Map<String, Integer> weightsRt = new HashMap<>(weights);
        List<String> result = new ArrayList<>(count);


        return result;
    }

    @Test
    public void testWeightedRoundRobin() {
        Map<String, Integer> weights = new HashMap<>();
        weights.put("label1", 4);
        weights.put("label2", 4);
        weights.put("label3", 1);
        System.out.println(wrr(weights));
    }

}