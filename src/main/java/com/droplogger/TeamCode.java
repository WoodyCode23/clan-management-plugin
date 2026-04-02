package com.droplogger;

public enum TeamCode
{
    NONE("", "Select your team"),
    WOO("WOO", "Warriors of Olm"),
    MB("MB", "Master Blasters"),
    TT("TT", "Torta Ticklers"),
    DN("DN", "Doobie Nation"),
    DRY("DRY", "Dry Streak"),
    MCD("MCD", "McDouble's Thrall Emporium"),
    BB("BB", "Boobie Brigade"),
    SRY("SRY", "Shrimpy's Rowdy Yappers");

    private final String code;
    private final String fullName;

    TeamCode(String code, String fullName)
    {
        this.code = code;
        this.fullName = fullName;
    }

    public String getCode() { return code; }
    public String getFullName() { return fullName; }

    public static TeamCode fromCode(String code)
    {
        if (code == null || code.isEmpty()) return NONE;
        for (TeamCode tc : values())
        {
            if (tc.code.equalsIgnoreCase(code)) return tc;
        }
        return NONE;
    }

    @Override
    public String toString()
    {
        if (this == NONE) return fullName;
        return code + " - " + fullName;
    }
}
