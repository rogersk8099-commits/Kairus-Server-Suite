package gg.neonnexus.smpplatform.phase3.guild;

/** Default Neon Nexus guild ranks, ordered from highest to lowest authority. */
public enum GuildRank {
    LEADER(4), OFFICER(3), MEMBER(2), RECRUIT(1);

    private final int authority;
    GuildRank(int authority) { this.authority = authority; }
    public int authority() { return authority; }
    public boolean outranks(GuildRank other) { return authority > other.authority; }
}
