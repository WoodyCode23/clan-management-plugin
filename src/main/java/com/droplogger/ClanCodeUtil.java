package com.droplogger;

import java.util.Base64;

/**
 * Encodes and decodes clan codes.
 * Format: base64(apiUrl|slug|apiKey)
 */
public class ClanCodeUtil
{
    private ClanCodeUtil() {}

    public static String[] decode(String clanCode)
    {
        if (clanCode == null || clanCode.trim().isEmpty())
        {
            return null;
        }

        try
        {
            String decoded = new String(Base64.getDecoder().decode(clanCode.trim()));
            String[] parts = decoded.split("\\|", 3);
            if (parts.length != 3)
            {
                return null;
            }

            String apiUrl = parts[0].trim();
            String slug = parts[1].trim();
            String apiKey = parts[2].trim();

            if (apiUrl.isEmpty() || slug.isEmpty() || apiKey.isEmpty())
            {
                return null;
            }

            return new String[]{apiUrl, slug, apiKey};
        }
        catch (IllegalArgumentException e)
        {
            // Invalid base64
            return null;
        }
    }

    public static String encode(String apiUrl, String slug, String apiKey)
    {
        String raw = apiUrl + "|" + slug + "|" + apiKey;
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }
}
