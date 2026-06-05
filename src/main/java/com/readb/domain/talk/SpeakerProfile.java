package com.readb.domain.talk;

public record SpeakerProfile(double meanRms, double stdRms) {
    public double distance(double rms) {
        if (stdRms == 0) return Math.abs(rms - meanRms);
        return Math.abs(rms - meanRms) / stdRms;
    }
}
