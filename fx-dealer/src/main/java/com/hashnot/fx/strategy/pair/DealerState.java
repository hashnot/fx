package com.hashnot.fx.strategy.pair;

/**
 * @author Rafał Krupiński
 */
public enum DealerState {
    NotProfitable,
    Opening,
    Cancelling,
    Updating,
    Closing,

    // waiting for my order to be closed
    Waiting
}
