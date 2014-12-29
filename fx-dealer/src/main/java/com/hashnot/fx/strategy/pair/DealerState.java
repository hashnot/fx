package com.hashnot.fx.strategy.pair;

/**
 * @author Rafał Krupiński
 */
public enum DealerState {
    NotProfitable,
    Opening,
    Cancelling,

    // waiting for my order to be closed
    Waiting
}
