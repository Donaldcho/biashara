# Bluetooth Printer Setup
feature_id: printer_setup
language: en

BiasharaAI supports Bluetooth thermal printers to print customer receipts instantly. Common compatible printers include Xprinter, Rongta, HOIN, and most 58mm or 80mm ESC/POS thermal printers.

## Step 1 — Pair the Printer with Your Phone

1. Turn on the thermal printer and make sure it is in pairing mode (usually indicated by a flashing blue light).
2. On your Android phone, open **Settings → Bluetooth**.
3. Tap **Scan** or **Add Device**.
4. Select your printer from the list (usually named something like "Printer_XXXX" or "RPP02").
5. Enter the PIN if prompted — default is usually **0000** or **1234**.
6. The printer should now show as **Paired** in your Bluetooth device list.

## Step 2 — Connect the Printer in BiasharaAI

1. Open BiasharaAI and go to **Settings → Printer Setup**.
2. Tap **Scan for Printers**. The app lists all paired Bluetooth devices.
3. Select your printer from the list.
4. Choose the **paper width**: 58mm or 80mm (check your printer's paper roll size).
5. Tap **Test Print**. A test receipt should print — verify text is aligned and readable.
6. Tap **Save** to make this the default printer.

## Printing a Receipt After a Sale

After completing a POS sale, choose **Print Receipt** in the completion dialog. The receipt is sent to the saved printer automatically.

## Troubleshooting

- **Printer not found**: Make sure Bluetooth is on and the printer is paired (Step 1).
- **Garbled text**: The paper width is wrong — try switching between 58mm and 80mm.
- **Paper jam**: Open the paper compartment, remove the jammed roll, reload it, and close firmly.
- **Faded print**: The thermal paper is inserted backwards (thermal side faces the print head) — flip the roll.
- **Connection drops**: Stand within 5–10 metres of the printer without walls in between.
