# AI Model Settings
feature_id: model_settings
language: en

BiasharaAI uses on-device AI models for chat, voice transcription, and agent analysis. All processing happens on your phone — your business data is never sent to a cloud server.

## Viewing Your Current Model

Go to **Settings → AI Model**. You will see:
- **Active Chat Model**: The model currently used for Chat and Agent Feed.
- **Active STT Model**: The model used for voice transcription.
- **Storage used**: How much phone storage the downloaded models occupy.

## Available Models

### Chat and Agent Models

| Model | Size | Best for |
|-------|------|---------|
| Gemma 2B | ~1.5 GB | Phones with 4 GB RAM or less — fast responses, basic queries |
| Gemma 7B | ~4 GB | Phones with 6 GB+ RAM — better reasoning, more detailed answers |
| FunctionGemma | ~2 GB | Optimised for BiasharaAI tool calls and structured business queries |

**Recommendation**: Use **FunctionGemma** for the best experience with sales queries and agent actions.

### STT (Speech-to-Text) Models

| Model | Size | Best for |
|-------|------|---------|
| Whisper Tiny | ~75 MB | Fast, low-accuracy — works on any phone |
| Whisper Base (Swahili-tuned) | ~150 MB | Good accuracy for Swahili and English |
| Whisper Small | ~250 MB | High accuracy, recommended if storage allows |

## Downloading a Model

1. Connect to Wi-Fi (models are large — do not download on mobile data unless necessary).
2. Go to **Settings → AI Model → Download Models**.
3. Tap **Download** next to the model you want.
4. Progress is shown — download continues in the background even if you navigate away.
5. Once downloaded, tap **Set as Active** to start using it.

## Switching Between Models

1. Go to **Settings → AI Model**.
2. Tap the model you want to activate.
3. Tap **Use This Model**.
4. The app restarts the AI engine — takes about 10 seconds.

## Deleting a Model

Tap a model → **Delete** to free up storage. You can re-download at any time.
