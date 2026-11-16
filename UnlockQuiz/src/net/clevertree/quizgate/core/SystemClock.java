package net.clevertree.quizgate.core;

public final class SystemClock implements Clock {
    @Override public long nowMillis() {
        return System.currentTimeMillis();
    }
}
