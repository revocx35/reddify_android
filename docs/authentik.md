# Reddify Android behind authentik

Server side: see the server repo's README "authentik". The app needs:

1. A **public** OAuth2/OpenID provider, client ID `reddify-android`, grant types *authorization code* + *refresh
   token* (set them explicitly; providers created through the API start with none and then fail with "The request
   is otherwise malformed"), redirect URI regex `^io\.github\.revocx35\.reddify(\.debug)?:/?/authentik$`, scopes
   openid, profile, email, offline_access, an RS256 signing key.
2. If the site uses forward auth: the proxy provider and this provider list each other under *Federated OIDC
   Providers*, and the proxy provider has *intercept header auth* on.
3. The server's `.env`: `OIDC_APP_ISSUER=https://<authentik>/application/o/<slug>/`, `OIDC_APP_CLIENT_ID=reddify-android`.

The app sends `Authorization: Bearer <authentik access token>`; the server verifies it (JWKS, issuer, audience)
and maps the `sub` to the Reddify user (same `sub` as the web login when both providers use hashed user ids).
Tokens are stored encrypted with an Android Keystore key; app backups are off.
