package opencep.plugin.twitter;

/** Credentials are supplied at runtime, never embedded in source. */
public record TwitterCredentials(String bearerToken) {
  public TwitterCredentials {
    if (bearerToken == null || bearerToken.isBlank())
      throw new IllegalArgumentException("Missing bearer token");
  }

  public static TwitterCredentials fromEnvironment() {
    return new TwitterCredentials(System.getenv("TWITTER_BEARER_TOKEN"));
  }
}
