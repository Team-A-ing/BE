package com.readb.domain.talk;

public class TalkSession {
    private SpeakerProfile leaderProfile;
    private SpeakerProfile memberProfile;
    private long leaderMs = 0;
    private long memberMs = 0;

    public void setLeaderProfile(SpeakerProfile p) { this.leaderProfile = p; }
    public void setMemberProfile(SpeakerProfile p) { this.memberProfile = p; }
    public SpeakerProfile getLeaderProfile() { return leaderProfile; }
    public SpeakerProfile getMemberProfile() { return memberProfile; }

    public void addLeaderMs(long ms) { this.leaderMs += ms; }
    public void addMemberMs(long ms) { this.memberMs += ms; }

    public long getLeaderMs() { return leaderMs; }
    public long getMemberMs() { return memberMs; }

    public boolean isCalibrated() {
        return leaderProfile != null && memberProfile != null;
    }

    public double leaderRatio() {
        long total = leaderMs + memberMs;
        if (total == 0) return 0.0;
        return Math.round(leaderMs * 1000.0 / total) / 10.0;
    }
}
