#!/usr/bin/env python3
"""Generate Biashara AI end-user manual PDF with step-by-step workflows."""

from pathlib import Path

from fpdf import FPDF

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "USER_MANUAL.pdf"


class ManualPDF(FPDF):
    def header(self):
        if self.page_no() == 1:
            return
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(100, 100, 100)
        self.cell(0, 8, "Biashara AI - User Manual", align="L")
        self.ln(4)

    def footer(self):
        self.set_y(-15)
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(120, 120, 120)
        self.cell(0, 10, f"Page {self.page_no()}", align="C")

    def chapter_title(self, num: str, title: str):
        self.ln(4)
        self.set_x(self.l_margin)
        self.set_font("Helvetica", "B", 16)
        self.set_text_color(30, 64, 175)
        self.multi_cell(self.epw, 9, f"{num}  {title}")
        self.ln(2)
        self.set_draw_color(30, 64, 175)
        y = self.get_y()
        self.line(self.l_margin, y, self.w - self.r_margin, y)
        self.ln(4)
        self.set_text_color(0, 0, 0)

    def section_title(self, title: str):
        self.ln(2)
        self.set_x(self.l_margin)
        self.set_font("Helvetica", "B", 12)
        self.multi_cell(self.epw, 7, title)
        self.ln(1)

    def body(self, text: str):
        self.set_x(self.l_margin)
        self.set_font("Helvetica", "", 10)
        self.multi_cell(self.epw, 5.5, text)
        self.ln(2)

    def steps(self, items: list[str]):
        self.set_font("Helvetica", "", 10)
        for i, item in enumerate(items, 1):
            self.set_x(self.l_margin)
            self.multi_cell(self.epw, 5.5, f"{i}. {item}")
            self.ln(0.5)
        self.ln(2)

    def bullet_list(self, items: list[str]):
        self.set_font("Helvetica", "", 10)
        for item in items:
            self.set_x(self.l_margin)
            self.multi_cell(self.epw, 5.5, f"- {item}")
        self.ln(2)


def build() -> None:
    pdf = ManualPDF()
    pdf.set_margins(15, 15, 15)
    pdf.set_auto_page_break(auto=True, margin=18)
    pdf.add_page()

    # Cover
    pdf.ln(35)
    pdf.set_font("Helvetica", "B", 28)
    pdf.set_text_color(30, 64, 175)
    pdf.cell(0, 14, "Biashara AI", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.set_font("Helvetica", "", 16)
    pdf.set_text_color(60, 60, 60)
    pdf.cell(0, 10, "Step-by-Step User Manual", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.ln(8)
    pdf.set_font("Helvetica", "", 11)
    pdf.multi_cell(
        0,
        6,
        "For shop and service business owners in Africa.\n"
        "Learn how to set up the app, record sales, manage stock,\n"
        "track credit, and use your AI business assistant.",
        align="C",
    )
    pdf.ln(20)
    pdf.set_font("Helvetica", "I", 10)
    pdf.cell(0, 6, "Deviceterra  |  Version for app database v32", align="C")
    pdf.add_page()

    # TOC
    pdf.chapter_title("", "Table of contents")
    toc = [
        "1. First-time setup (step by step)",
        "2. Daily routine - what to do each day",
        "3. Today screen - AI alerts",
        "4. Record a product sale (POS)",
        "5. Record a credit sale",
        "6. Add products to inventory",
        "7. Scan a supplier receipt",
        "8. Insights - cash flow and ledger",
        "9. Collect money customers owe you",
        "10. Chat - ask your assistant",
        "11. Business profile - teach the AI",
        "12. Settings you should know",
        "13. Pro features - services and vouchers",
        "14. Troubleshooting",
    ]
    pdf.bullet_list(toc)
    pdf.add_page()

    # 1 Setup
    pdf.chapter_title("1", "First-time setup (step by step)")
    pdf.section_title("Step A - Install and open the app")
    pdf.steps(
        [
            "Install Biashara AI from your app store or APK provided by your supplier.",
            "Open the app. You will see a short splash screen, then language selection.",
        ]
    )
    pdf.section_title("Step B - Choose your language")
    pdf.steps(
        [
            "On 'Choose your language', tap your language: English, Kiswahili, Hausa, Yoruba, or Amharic.",
            "The entire app will use this language. You can change it later in phone system settings if needed.",
            "After selection, you arrive on the Today tab (home screen).",
        ]
    )
    pdf.section_title("Step C - Set your currency")
    pdf.steps(
        [
            "Tap Settings (gear icon) on the bottom bar.",
            "Scroll to Regional.",
            "Tap Currency and choose your country money (e.g. Kenyan Shilling KES, Tanzanian TZS).",
            "All prices and receipts will use this currency.",
        ]
    )
    pdf.section_title("Step D - Tell the app about your business (important)")
    pdf.steps(
        [
            "In Settings, tap Business profile.",
            "Tap Redo conversation setup OR fill the form fields directly.",
            "Conversation setup: answer 8 questions one at a time (business name, what you sell, customers, hours, location, your name, suppliers, goals). Tap Send after each answer. You can Skip a question.",
            "OR edit fields on the form and tap Save profile.",
            "Example: 'Amina Hair Studio - braiding and locs - young women in Westlands - open Tue-Sun - supplier Mama Grace'.",
            "Agents and Chat will use this to speak to you by name and give relevant advice.",
        ]
    )
    pdf.section_title("Step E - Download the AI brain (recommended)")
    pdf.steps(
        [
            "Stay in Settings. Find the AI Model section at the top.",
            "Read your device tier: Full AI (best), Partial AI, or Rules only.",
            "Connect to Wi-Fi. Plug in your phone to charge.",
            "Tap Download model. Wait until status shows ready (about 2.5 GB).",
            "Without the model you still get sales and alerts, but Chat and weekly reviews are limited.",
        ]
    )
    pdf.section_title("Step F - Pro licence (only if you bought Pro)")
    pdf.steps(
        [
            "In Settings, scroll to Licence.",
            "Paste the licence key your supplier gave you.",
            "Tap Apply. You should see Pro and your edition name.",
            "A Services tab will appear in Inventory. Service mode appears on Sales.",
        ]
    )
    pdf.add_page()

    # 2 Daily
    pdf.chapter_title("2", "Daily routine - what to do each day")
    pdf.section_title("Every morning (2 minutes)")
    pdf.steps(
        [
            "Open the app. Go to Today tab.",
            "Read the attention chip - how many agent cards need review.",
            "Tap each card: Review, Approve, Dismiss, or Snooze as you wish.",
            "Optional: Today - Business ledger - Cash flow tab for a quick money picture.",
        ]
    )
    pdf.section_title("During the day")
    pdf.steps(
        [
            "Record EVERY sale in Sales tab as it happens (cash, mobile money, or credit).",
            "When stock arrives from supplier: Inventory - add products or scan supplier receipt.",
            "Use Chat anytime: 'What are today's sales?' or 'What is low on stock?'",
        ]
    )
    pdf.section_title("Every evening (5 minutes)")
    pdf.steps(
        [
            "Sales tab - menu (three dots) - Close day. Read totals and share if needed.",
            "Insights - Credit tab - send reminders to customers who owe you.",
            "Optional: Ledger tab - Cash count if you count physical cash in the drawer.",
        ]
    )
    pdf.add_page()

    # 3 Today
    pdf.chapter_title("3", "Today screen - AI alerts")
    pdf.body(
        "Today is your home screen. Background agents watch your shop and post suggestion cards."
    )
    pdf.section_title("How to work with agent cards")
    pdf.steps(
        [
            "Open Today tab (first icon on bottom bar - home).",
            "Each card is one suggestion: low stock, pricing idea, cash summary, customer SMS, weekly review, etc.",
            "Tap Review or View to see full details.",
            "Tap Approve if you agree (e.g. to use a drafted SMS).",
            "Tap Reject if you do not want this suggestion.",
            "Tap Snooze to be reminded later.",
            "Tap Dismiss to remove the card.",
            "Pull down on the list to refresh and run agents again.",
        ]
    )
    pdf.section_title("Shortcuts on Today")
    pdf.steps(
        [
            "Prepare supplier visit - pick products and get a negotiation script (needs Full AI).",
            "Business ledger - opens Insights with cash flow, credit, and ledger tabs.",
        ]
    )
    pdf.add_page()

    # 4 POS sale
    pdf.chapter_title("4", "Record a product sale (step by step)")
    pdf.steps(
        [
            "Tap Sales on the bottom bar (cart icon).",
            "Find the product: scroll, search by name, or tap the scan icon to scan barcode.",
            "Tap a product to add it to the cart. Tap again to add more quantity.",
            "Check the cart total at the bottom (subtotal, tax if enabled, grand total).",
            "Optional: tap Customer - choose Walk-in, pick existing customer, or Add new (name + phone).",
            "Tap Pay (or Checkout).",
            "Choose payment: Cash, Mobile money, or Split (part cash + part mobile).",
            "For Cash: enter amount customer gave; app shows change due.",
            "For Mobile money: confirm amount and complete.",
            "Tap Confirm / Complete sale.",
            "Receipt screen appears. Share via WhatsApp or print if you have a Bluetooth printer.",
            "Tap Done or Back to record the next sale.",
        ]
    )
    pdf.add_page()

    # 5 Credit
    pdf.chapter_title("5", "Record a credit sale (customer pays later)")
    pdf.steps(
        [
            "Go to Sales. Add products to cart as normal.",
            "Tap Customer - you MUST select or create a customer (credit needs a name).",
            "Tap Pay.",
            "Select On credit (or Credit).",
            "Enter due date if asked. Add a note if needed (e.g. 'will pay Friday').",
            "Confirm the sale. Stock is reduced; money is recorded as owed, not received yet.",
            "Later: Insights - Credit tab - find the customer - Remind (SMS) or Paid when they settle.",
        ]
    )
    pdf.add_page()

    # 6 Inventory
    pdf.chapter_title("6", "Add products to inventory (step by step)")
    pdf.section_title("Method 1 - Add manually")
    pdf.steps(
        [
            "Tap Inventory on bottom bar.",
            "Tap the + button.",
            "Choose Add manually.",
            "Enter product name, sell price, cost price (optional), stock quantity.",
            "Add photo: tap camera or gallery icon.",
            "Add barcode: tap scan icon on the form.",
            "Tap Save.",
        ]
    )
    pdf.section_title("Method 2 - Scan barcode")
    pdf.steps(
        [
            "Inventory - tap + - Scan barcode.",
            "Point camera at product barcode until it beeps or shows the code.",
            "If product is new, you go to Add product with barcode filled in.",
            "Fill name and price, then Save.",
        ]
    )
    pdf.add_page()

    # 7 Receipt scan
    pdf.chapter_title("7", "Scan a supplier receipt (bulk add stock)")
    pdf.steps(
        [
            "Go to Inventory.",
            "Tap + - Scan receipt.",
            "Photograph the supplier invoice or delivery note (good light, flat surface).",
            "Wait while the app reads line items.",
            "Review each line: name, quantity, cost. Edit wrong lines.",
            "Remove lines you do not want. Confirm.",
            "Products are added or stock is increased in your catalogue.",
        ]
    )
    pdf.add_page()

    # 8 Insights
    pdf.chapter_title("8", "Insights - cash flow and ledger")
    pdf.section_title("How to open Insights")
    pdf.steps(
        [
            "From Today: tap Business ledger, OR",
            "From Settings: tap Open ledger.",
        ]
    )
    pdf.section_title("Cash flow tab")
    pdf.steps(
        [
            "See income vs expenses chart.",
            "Read net position and AI summary (if model downloaded).",
            "Pull down to refresh.",
        ]
    )
    pdf.section_title("Ledger tab - record money in or out")
    pdf.steps(
        [
            "Open Ledger tab.",
            "Tap the + floating button.",
            "Choose Manual entry for hand-typed records.",
            "Choose Scan proof to photograph M-Pesa slip or bank receipt.",
            "Choose Cash count to reconcile cash in your drawer vs expected balance.",
            "Choose Export CSV to save spreadsheet to your phone or share.",
        ]
    )
    pdf.add_page()

    # 9 Credit collect
    pdf.chapter_title("9", "Collect money customers owe you")
    pdf.steps(
        [
            "Open Insights (Today - Business ledger OR Settings - Open ledger).",
            "Tap Credit tab.",
            "See total outstanding at top and list of each debt.",
            "Tap a customer row.",
            "Tap Remind - read SMS draft - tap Send to open your phone SMS app.",
            "When customer pays: tap Paid on that debt to mark settled.",
            "OR record a new cash sale in Sales and link to customer if paying off account.",
        ]
    )
    pdf.add_page()

    # 10 Chat
    pdf.chapter_title("10", "Chat - ask your assistant (step by step)")
    pdf.steps(
        [
            "Tap Chat on bottom bar.",
            "Type a question in plain language, e.g. 'What were today's sales?'",
            "OR tap a suggested prompt chip at the top.",
            "OR tap microphone and speak (enable voice in Settings first).",
            "Wait for answer. Tap speaker icon to hear it read aloud.",
            "Tap + for New chat to start fresh topic.",
            "Tap history icon to open past conversations.",
            "Attach image: tap clip/camera icon for receipt or stock photo questions.",
        ]
    )
    pdf.add_page()

    # 11 Profile
    pdf.chapter_title("11", "Business profile - teach the AI")
    pdf.steps(
        [
            "Settings - Business profile.",
            "To update one field: scroll, edit, tap Save profile.",
            "To redo all questions: tap Redo conversation setup.",
            "Answer each question; tap Send. Agent confirms 'Got it: ...' and asks next.",
            "After 8 questions, profile is saved. Agents use your business name and goals.",
        ]
    )
    pdf.add_page()

    # 12 Settings
    pdf.chapter_title("12", "Settings you should know")
    pdf.bullet_list(
        [
            "AI Model - download/delete Gemma; see device tier.",
            "Voice and speech - turn mic on; open Voice settings for language and TTS.",
            "Business profile - your story for agents.",
            "Agent run history - when agents ran; toggle agents on/off.",
            "Staff members (Pro) - names for service sales.",
            "Regional - currency.",
            "Licence - paste Shop or Pro key.",
        ]
    )
    pdf.add_page()

    # 13 Pro
    pdf.chapter_title("13", "Pro features - services and vouchers")
    pdf.section_title("Add a service (Pro)")
    pdf.steps(
        [
            "Apply Pro licence in Settings first.",
            "Inventory - switch to Services tab.",
            "Tap + to add service: name, price, duration minutes, warranty days.",
            "Save. Service appears on Sales when mode is Services or Both.",
        ]
    )
    pdf.section_title("Sell a service at POS (Pro)")
    pdf.steps(
        [
            "Sales - set catalog to Services or Both.",
            "Tap the service. Pick staff member or skip.",
            "Tap Pay and complete payment as normal.",
        ]
    )
    pdf.section_title("Sell a prepaid voucher (Pro)")
    pdf.steps(
        [
            "Sales - Services mode.",
            "Long-press the service (hold finger on it).",
            "Set number of sessions, price, validity days.",
            "Customer pays. Show voucher QR/card - customer scans each visit.",
            "Redeem: Sales - Pay - Voucher tab - scan QR or enter voucher ID.",
        ]
    )
    pdf.section_title("Mixed cart - products + services (Pro)")
    pdf.steps(
        [
            "Sales - set catalog to Both. Add products and services to same cart.",
            "Tap Pay. Choose payment plan if offered: pay all, credit products only, credit services only, or deposit + balance.",
            "If balance remains: long-press customer chip on Sales to Collect balance when they return.",
        ]
    )
    pdf.add_page()

    # 14 Troubleshooting
    pdf.chapter_title("14", "Troubleshooting")
    pdf.bullet_list(
        [
            "Agents say 'your business' not my name: complete Settings - Business profile.",
            "Model won't download: use Wi-Fi, free 3+ GB storage, keep phone charging.",
            "Barcode won't scan: better light; for vouchers use printed QR from voucher card.",
            "Credit greyed out: select a customer before Pay.",
            "No Services tab: apply Pro licence in Settings.",
            "Data is on your phone - export Ledger CSV for backup.",
        ]
    )
    pdf.ln(4)
    pdf.section_title("Quick navigation reference")
    pdf.bullet_list(
        [
            "Today = home, agent cards",
            "Inventory = products and services",
            "Chat = ask questions",
            "Sales = POS, every sale",
            "Settings = setup, profile, licence",
            "Insights = Today - Business ledger OR Settings - Open ledger",
        ]
    )

    pdf.output(str(OUTPUT))
    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    build()
