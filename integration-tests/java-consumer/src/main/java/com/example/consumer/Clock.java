package com.example.consumer;

import eu.nitonfx.processors.delegation.Delegator;

@Delegator
public interface Clock {

    long currentTimeMillis();

    String zoneId();
}
