# Biashara AI Pro Desktop

This is a standalone desktop workstation for Biashara AI Pro. It is intentionally separate from the Android Gradle project so desktop work does not break the mobile app.

The Windows desktop product has two parts:

- `desktop-standalone`: Java local backend, offline data store, POS APIs, phone bridge, and bundled web UI resources.
- `desktop-shell-windows`: Windows app window using WebView2. It starts the Java backend hidden and embeds the localhost UI so users open a normal desktop app instead of a browser tab.

The v2 desktop entry point is:

```text
com.biasharaai.desktop.v2.BiasharaDesktopWebApp
```

The older Swing prototype remains in source as a reference. The Gradle launcher is still useful as a backend/browser fallback, but the installed Windows shortcut should point to `Biashara AI Pro Desktop.exe` from `desktop-shell-windows`.

## Build

From the repository root:

```powershell
.\gradlew.bat -p desktop-standalone installDist distZip
```

## Run

```powershell
desktop-standalone\build\install\biasharaai-desktop\bin\biasharaai-desktop.bat
```

For the standalone Windows shell:

```powershell
dotnet restore desktop-shell-windows\BiasharaDesktopShell.csproj
dotnet publish desktop-shell-windows\BiasharaDesktopShell.csproj -c Release -o desktop-shell-windows\publish
```

Install the publish output beside the Java backend distribution. The shell expects `lib\BiasharaAIDesktopStandalone.jar` under the same install root.

## Desktop Experience

The desktop app includes:

- Today dashboard with revenue, catalog, credit, stock alerts, mobile sync status, and image counts.
- Professional POS for products, services, mixed carts, USB barcode scanners, and phone scanner events.
- Laptop camera barcode scanning when the browser exposes the local BarcodeDetector API.
- Image-first product catalog management using real synced or uploaded product images.
- Phone Link screen for mobile scanner pairing and mobile catalog sync.
- Mobile stock intake sync with product images saved locally.
- WhatsApp catalog message preparation and product sharing.
- Settings for business profile, currency, tax, receipt footer, LM Studio, and WhatsApp placeholders.

## Barcode And Phone Scanning

Most USB barcode scanners work like a keyboard and press Enter after each scan.

- POS: open POS, focus the scanner field, then scan. Product barcodes add products to the cart. Pro service tokens such as `BSVC:<service-id>` add services.
- Phone Link: the desktop starts a localhost UI on ports `8765-8775` and a LAN phone bridge on ports `8865-8875`. The LAN bridge exposes only phone sync routes.

Current phone bridge endpoints:

```text
POST /api/phone/pair   {"token":"PAIRING_CODE","deviceName":"Phone name"}
POST /api/phone/scan   {"sessionKey":"SESSION","rawValue":"600100000001","deviceName":"Phone name"}
POST /api/phone/product-sync
{
  "sessionKey": "SESSION",
  "deviceName": "Phone name",
  "mobileProductId": "mobile-room-product-id",
  "name": "Hair oil 100ml",
  "sku": "HAIR-OIL-100",
  "barcode": "600100000001",
  "category": "Beauty",
  "stock": 18,
  "priceCents": 25000,
  "costCents": 16000,
  "imageFileName": "hair-oil.jpg",
  "imageBase64": "...",
  "whatsappRetailerId": "600100000001"
}
POST /api/phone/transaction-sync
{
  "sessionKey": "SESSION",
  "deviceName": "Phone name",
  "mobileTransactionId": "123",
  "receiptNumber": "RCP-123",
  "createdAtMillis": 1788256000000,
  "type": "INCOME",
  "description": "Mobile POS sale",
  "paymentMethod": "CASH",
  "subtotalCents": 25000,
  "productSubtotalCents": 25000,
  "serviceSubtotalCents": 0,
  "taxCents": 0,
  "totalCents": 25000,
  "paidCents": 25000,
  "balanceCents": 0,
  "lines": [
    {
      "kind": "PRODUCT",
      "mobileProductId": "42",
      "name": "Hair oil 100ml",
      "barcode": "600100000001",
      "category": "Beauty",
      "quantity": 1,
      "unitCents": 25000,
      "lineTotalCents": 25000
    }
  ]
}
POST /api/phone/reconcile
{
  "sessionKey": "SESSION",
  "deviceName": "Phone name",
  "includeImages": false
}
POST /api/phone/stock-intake
{
  "sessionKey": "SESSION",
  "deviceName": "Phone name",
  "productName": "Hair oil 100ml",
  "barcode": "600100000001",
  "category": "Beauty",
  "quantity": 12,
  "priceCents": 25000,
  "costCents": 16000,
  "imageFileName": "hair-oil.jpg",
  "imageBase64": "..."
}
```

The bridge uses a short-lived pairing code and a per-session key. The Android-side "Connect Desktop" scanner screen should be added later as an additive mobile feature using the existing mobile barcode scanner and router.

Product sync updates matching products by barcode, creates missing products, saves mobile images locally, and replaces the desktop stock count with the mobile stock count.

Transaction sync records recent mobile receipts on desktop using `MOB-<mobileTransactionId>` ids so retries do not create duplicates. Reconciliation returns desktop-origin products, current stock, and desktop POS transactions with committed sale lines so the phone can import desktop sales as local receipts.

Stock intake updates matching products by barcode, creates missing products, records a sync row, and stores incoming images under:

```text
%USERPROFILE%\.biasharaai-desktop-pro\incoming-images
```

The Phone Link screen also accepts a mobile catalog JSON import through the local UI. This is a desktop-side bridge for testing and for export-based workflows until the Android sender is added.

## WhatsApp Business

The WhatsApp screen prepares customer-ready product messages from stocked inventory and can open a WhatsApp share link. Settings include placeholders for WhatsApp Business phone-number ID and catalog ID.

The production Cloud API sender should be implemented as a separate integration service with secure credential storage, product catalog sync, message-template approval handling, and clear opt-in controls.

## Desktop AI

The desktop assistant can run in two modes:

- `RULES`: fast local business rules with no model server.
- `LM_STUDIO`: local AI through LM Studio's OpenAI-compatible server.

Default LM Studio endpoint:

```text
http://127.0.0.1:1234/v1
```

The desktop app tests `GET /v1/models` and sends assistant prompts to `POST /v1/chat/completions`. The Assistant screen uses a streaming desktop endpoint so LM Studio tokens appear progressively in the UI when the selected model streams content.

The request includes a compact local business context from desktop products, services, customers, transactions, stock alerts, and mobile sync counts. Product catalog images are not sent to the model; only image availability is summarized.

The Assistant screen can attach up to three user-selected images to a question. Those images are sent as `image_url` content parts to LM Studio and require a vision-capable local model. Voice input is represented in the UI as a disabled placeholder until a local speech-to-text model is connected.

For safety, the LM Studio URL must use `http` or `https` and point to `localhost` or a private LAN address. POS, inventory, image sync, and phone scanning do not depend on the AI model.

The legacy built-in model path setting is kept as a future second AI option.

## Local Data

The app stores local files under:

```text
%USERPROFILE%\.biasharaai-desktop-pro
```

Files:

- `products.tsv`
- `services.tsv`
- `transactions.tsv`
- `sale-lines.tsv`
- `customers.tsv`
- `phone-scans.tsv`
- `product-sync.tsv`
- `stock-sync.tsv`
- `settings.properties`
- `incoming-images\`
- `models\`
