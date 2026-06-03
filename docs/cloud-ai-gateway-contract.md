# Optional Cloud AI Gateway Contract

BiasharaAI does not call Claude directly from Android. The app calls an owner-controlled gateway so provider keys, rate limits, audit logging, and retrieval policy stay outside the APK.

## Endpoint

Configure the gateway URL in Settings > Optional cloud AI.

Allowed URLs:

- `https://...` for hosted gateways
- `http://localhost`, `http://127.0.0.1`, `http://10.x.x.x`, `http://172.16.x.x` to `172.31.x.x`, `http://192.168.x.x`, or `.local` for private LAN gateways

The optional gateway access token is sent as:

```http
Authorization: Bearer <token>
```

## Request

```json
{
  "mode": "research_augment",
  "provider": "Claude",
  "userQuestion": "What are current mobile money fees in Cameroon?",
  "language": "English",
  "allowInternetResearch": true,
  "app": "BiasharaAI",
  "visualSummary": "optional on-device image summary",
  "businessContext": "optional local business snapshot, only when the user enabled it",
  "sovereigntyPolicy": {
    "localFirst": true,
    "optionalCloud": true,
    "businessDataIncluded": false,
    "targetUsers": "SMEs in Africa",
    "instruction": "Answer for an African SME operator..."
  }
}
```

## Response

```json
{
  "answer": "Short practical answer for the shop owner.",
  "provider": "Claude",
  "model": "claude-sonnet",
  "usedInternet": true,
  "sources": [
    {
      "title": "Source title",
      "url": "https://example.com/source",
      "snippet": "Short relevant excerpt or summary."
    }
  ]
}
```

The Android app falls back to local AI or local rules if the gateway times out, fails, or returns no answer.
