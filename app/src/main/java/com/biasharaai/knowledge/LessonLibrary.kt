package com.biasharaai.knowledge

import com.biasharaai.data.local.db.FeatureMastery
import com.biasharaai.data.local.db.MasteryLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catalogue of all micro-lessons shipped with the app.
 * Each feature has 1–4 lessons of increasing depth.
 */
@Singleton
class LessonLibrary @Inject constructor() {

    private val catalogue: Map<String, List<MicroLesson>> by lazy { buildCatalogue() }

    fun allFeatureIds(): List<String> = catalogue.keys.sorted()

    fun allLessonsForFeature(featureId: String, languageCode: String = "en"): List<MicroLesson> =
        catalogue[featureId]?.filter { it.languageCode == languageCode }
            ?: catalogue[featureId]?.filter { it.languageCode == "en" }
            ?: emptyList()

    fun firstLessonForFeature(featureId: String, languageCode: String = "en"): MicroLesson? =
        allLessonsForFeature(featureId, languageCode).firstOrNull()

    fun lessonById(lessonId: String): MicroLesson? =
        catalogue.values.flatten().firstOrNull { it.lessonId == lessonId }

    fun nextLessonForMastery(mastery: FeatureMastery, languageCode: String = "en"): MicroLesson? {
        val lessons = allLessonsForFeature(mastery.featureId, languageCode)
        val level = runCatching { MasteryLevel.valueOf(mastery.masteryLevel) }
            .getOrDefault(MasteryLevel.UNDISCOVERED)
        val lessonIndex = when (level) {
            MasteryLevel.UNDISCOVERED, MasteryLevel.DISCOVERED -> 0
            MasteryLevel.LEARNING -> 1.coerceAtMost(lessons.lastIndex)
            MasteryLevel.PROFICIENT -> 2.coerceAtMost(lessons.lastIndex)
            MasteryLevel.MASTERED -> return null
        }
        return lessons.getOrNull(lessonIndex)
    }

    private fun buildCatalogue(): Map<String, List<MicroLesson>> = mapOf(
        "add_product" to listOf(
            MicroLesson(
                lessonId = "add_product_basics",
                featureId = "add_product",
                languageCode = "en",
                title = "Adding your first product",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "Open the Inventory screen from the bottom navigation bar.", "Tap Inventory (box icon)", StepActionType.TAP),
                    LessonStep(2, "Tap the '+' button in the top-right corner to open the Add Product form.", "Tap the '+' button", StepActionType.TAP),
                    LessonStep(3, "Enter the product name (e.g. 'Maize Flour 2kg'), selling price, and cost price.", navigationHint = null, StepActionType.TYPE),
                    LessonStep(4, "Set the stock quantity — this is how many units you have right now.", actionType = StepActionType.TYPE),
                    LessonStep(5, "Tap Save. The product now appears in your inventory list.", "Tap Save", StepActionType.CONFIRM),
                ),
            ),
            MicroLesson(
                lessonId = "add_product_barcode",
                featureId = "add_product",
                languageCode = "en",
                title = "Adding a barcode to your product",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "Open Add Product or edit an existing product.", actionType = StepActionType.TAP),
                    LessonStep(2, "Tap the barcode icon next to the Barcode field.", "Tap the barcode scanner icon", StepActionType.TAP),
                    LessonStep(3, "Point your camera at the product barcode and wait for the beep.", actionType = StepActionType.CONFIRM),
                    LessonStep(4, "The barcode is now saved. At POS, scanning this barcode will auto-add the item to the cart.", actionType = StepActionType.READ),
                ),
            ),
        ),
        "pos_sale" to listOf(
            MicroLesson(
                lessonId = "pos_sale_basics",
                featureId = "pos_sale",
                languageCode = "en",
                title = "Making your first sale",
                estimatedMinutes = 3,
                steps = listOf(
                    LessonStep(1, "Open the POS screen from the bottom navigation.", "Tap POS (cart icon)", StepActionType.TAP),
                    LessonStep(2, "Tap a product to add it to the cart. Tap multiple times to increase quantity, or swipe to adjust.", actionType = StepActionType.TAP),
                    LessonStep(3, "Review the total at the bottom. Tap 'Charge' when ready.", "Tap Charge", StepActionType.TAP),
                    LessonStep(4, "Choose the payment method: Cash, MTN MoMo, Orange Money, or another mobile money option.", actionType = StepActionType.TAP),
                    LessonStep(5, "Enter the amount received. The app calculates change automatically.", actionType = StepActionType.TYPE),
                    LessonStep(6, "Tap 'Complete Sale'. The sale is recorded and you can print a receipt.", "Tap Complete Sale", StepActionType.CONFIRM),
                ),
            ),
            MicroLesson(
                lessonId = "pos_sale_customer",
                featureId = "pos_sale",
                languageCode = "en",
                title = "Attaching a customer to a sale",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "In the POS cart screen, tap the 'Customer' field at the top.", actionType = StepActionType.TAP),
                    LessonStep(2, "Search for the customer by name or phone, or tap + to add a new one.", actionType = StepActionType.TYPE),
                    LessonStep(3, "Complete the sale as normal. The sale is now linked to this customer.", actionType = StepActionType.CONFIRM),
                    LessonStep(4, "If the customer pays on credit, select 'Credit' as payment method. The amount is added to their debt.", actionType = StepActionType.READ),
                ),
            ),
        ),
        "customers" to listOf(
            MicroLesson(
                lessonId = "customers_add",
                featureId = "customers",
                languageCode = "en",
                title = "Adding a customer",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "Open Customers from the main menu.", actionType = StepActionType.TAP),
                    LessonStep(2, "Tap the '+' button. Enter the customer's name and phone number.", actionType = StepActionType.TYPE),
                    LessonStep(3, "Tap Save. The customer is now in your database and can be attached to sales.", actionType = StepActionType.CONFIRM),
                ),
            ),
        ),
        "debts" to listOf(
            MicroLesson(
                lessonId = "debts_record",
                featureId = "debts",
                languageCode = "en",
                title = "Recording a customer debt",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "Debts are automatically recorded when you complete a credit sale in POS.", actionType = StepActionType.READ),
                    LessonStep(2, "To view debts, open the Customers screen and tap a customer name.", actionType = StepActionType.TAP),
                    LessonStep(3, "The customer's outstanding debts are listed. Tap a debt to mark it as paid.", actionType = StepActionType.TAP),
                    LessonStep(4, "You can also add a manual debt from the customer detail screen using the + button.", actionType = StepActionType.READ),
                ),
            ),
        ),
        "transactions" to listOf(
            MicroLesson(
                lessonId = "transactions_view",
                featureId = "transactions",
                languageCode = "en",
                title = "Viewing your transaction history",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "Open the Transactions screen from the bottom navigation.", actionType = StepActionType.TAP),
                    LessonStep(2, "All sales, expenses, and credits are listed here, newest first.", actionType = StepActionType.READ),
                    LessonStep(3, "Use the date filter at the top to view a specific period (today, this week, this month, or custom).", actionType = StepActionType.TAP),
                    LessonStep(4, "Tap a transaction to see its details, including line items and payment method.", actionType = StepActionType.TAP),
                ),
            ),
        ),
        "agent_feed" to listOf(
            MicroLesson(
                lessonId = "agent_feed_intro",
                featureId = "agent_feed",
                languageCode = "en",
                title = "Understanding the AI Agent Feed",
                estimatedMinutes = 3,
                steps = listOf(
                    LessonStep(1, "The Agent Feed is your business assistant. It shows recommendations and alerts automatically.", actionType = StepActionType.READ),
                    LessonStep(2, "Each card has a headline and detail. Cards with an action button require your approval.", actionType = StepActionType.READ),
                    LessonStep(3, "Tap 'Approve' on a suggested price change to apply it, or 'Dismiss' to ignore.", actionType = StepActionType.TAP),
                    LessonStep(4, "The AI runs in the background (hourly or daily) and adds cards when it finds insights.", actionType = StepActionType.READ),
                    LessonStep(5, "Configure which agents run in Settings → Agent Settings.", navigationHint = "Open Settings → Agent Settings", StepActionType.READ),
                ),
            ),
        ),
        "chat" to listOf(
            MicroLesson(
                lessonId = "chat_basics",
                featureId = "chat",
                languageCode = "en",
                title = "Asking the AI questions",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "Open the Chat screen. Type a question in plain language — no special commands needed.", actionType = StepActionType.TYPE),
                    LessonStep(2, "Try: 'What were my best-selling products this week?' or 'Which customers owe me money?'", actionType = StepActionType.READ),
                    LessonStep(3, "The AI reads your actual business data to answer. Results appear within seconds.", actionType = StepActionType.READ),
                    LessonStep(4, "Tap the microphone icon to ask by voice instead of typing.", "Tap the mic icon", StepActionType.TAP),
                ),
            ),
        ),
        "voice_commands" to listOf(
            MicroLesson(
                lessonId = "voice_basics",
                featureId = "voice_commands",
                languageCode = "en",
                title = "Using voice commands",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "Tap the microphone icon on any screen that supports voice input.", actionType = StepActionType.TAP),
                    LessonStep(2, "Speak clearly. The app understands English and Swahili.", actionType = StepActionType.READ),
                    LessonStep(3, "Navigation commands: 'Go home', 'Open POS', 'Open chat', 'Open inventory'.", actionType = StepActionType.READ),
                    LessonStep(4, "Query commands: 'Mauzo ya leo' (today's sales), 'What is my stock level?'", actionType = StepActionType.READ),
                    LessonStep(5, "On data-entry screens (Add Product), speaking fills in the form field automatically.", actionType = StepActionType.READ),
                ),
            ),
        ),
        "receipt_scan" to listOf(
            MicroLesson(
                lessonId = "receipt_scan_basics",
                featureId = "receipt_scan",
                languageCode = "en",
                title = "Scanning an expense receipt",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "To record an expense from a supplier receipt, open the camera/scan option in Transactions.", actionType = StepActionType.TAP),
                    LessonStep(2, "Point your camera at the receipt and capture the photo.", actionType = StepActionType.TAP),
                    LessonStep(3, "The AI reads the receipt text and fills in the amount and description automatically.", actionType = StepActionType.READ),
                    LessonStep(4, "Review the extracted data, correct if needed, then Save to record the expense.", actionType = StepActionType.CONFIRM),
                ),
            ),
        ),
        "settings" to listOf(
            MicroLesson(
                lessonId = "settings_basics",
                featureId = "settings",
                languageCode = "en",
                title = "Setting up your business",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "Open Settings from the bottom navigation bar.", actionType = StepActionType.TAP),
                    LessonStep(2, "Set your Business Name — this appears on receipts.", actionType = StepActionType.TYPE),
                    LessonStep(3, "Set your Currency (FCFA/XAF is the default for Cameroon).", actionType = StepActionType.TYPE),
                    LessonStep(4, "Set your Tax Rate if you charge VAT or local tax.", actionType = StepActionType.TYPE),
                    LessonStep(5, "Tap Save. These settings apply across the app immediately.", actionType = StepActionType.CONFIRM),
                ),
            ),
        ),
        "ledger" to listOf(
            MicroLesson(
                lessonId = "ledger_basics",
                featureId = "ledger",
                languageCode = "en",
                title = "Understanding your business ledger",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "The Ledger is a complete record of all money in and out of your business.", actionType = StepActionType.READ),
                    LessonStep(2, "Every sale, expense, and cash movement is recorded as a ledger entry automatically.", actionType = StepActionType.READ),
                    LessonStep(3, "The running balance shows your cash position at any point in time.", actionType = StepActionType.READ),
                    LessonStep(4, "Use the Cash Count feature daily to verify your physical cash matches the ledger.", actionType = StepActionType.READ),
                ),
            ),
        ),
        "cash_count" to listOf(
            MicroLesson(
                lessonId = "cash_count_basics",
                featureId = "cash_count",
                languageCode = "en",
                title = "Performing a daily cash count",
                estimatedMinutes = 3,
                steps = listOf(
                    LessonStep(1, "At the end of each day, count the physical cash in your till/box.", actionType = StepActionType.READ),
                    LessonStep(2, "Open the Cash Count screen from the Ledger section.", actionType = StepActionType.TAP),
                    LessonStep(3, "Enter the actual cash amount you counted.", actionType = StepActionType.TYPE),
                    LessonStep(4, "The app shows the difference between expected and actual. A difference may indicate an error or theft.", actionType = StepActionType.READ),
                    LessonStep(5, "Tap Save to record the count. Discrepancies are flagged to the AI agent.", actionType = StepActionType.CONFIRM),
                ),
            ),
        ),
        "model_settings" to listOf(
            MicroLesson(
                lessonId = "model_basics",
                featureId = "model_settings",
                languageCode = "en",
                title = "Downloading an AI model",
                estimatedMinutes = 3,
                steps = listOf(
                    LessonStep(1, "The AI chat and agent features require an on-device model. Open Settings → AI Model.", actionType = StepActionType.TAP),
                    LessonStep(2, "You will see available models. 'Gemma 4 E2B' is the primary chat model (~2.5GB).", actionType = StepActionType.READ),
                    LessonStep(3, "Tap Download next to the model. Downloading requires WiFi and ~2.5GB storage.", actionType = StepActionType.TAP),
                    LessonStep(4, "Once downloaded, the model is available offline. Chat and agent features are fully unlocked.", actionType = StepActionType.READ),
                ),
            ),
        ),
        "reports" to listOf(
            MicroLesson(
                lessonId = "reports_basics",
                featureId = "reports",
                languageCode = "en",
                title = "Viewing your business reports",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "Ask the AI in Chat: 'Show me this week's sales report' or 'What is my profit today?'", actionType = StepActionType.TYPE),
                    LessonStep(2, "The AI pulls live data from your transactions to generate the report.", actionType = StepActionType.READ),
                    LessonStep(3, "For inventory reports, ask: 'What products are low on stock?' or 'Show me slow-moving items'.", actionType = StepActionType.READ),
                    LessonStep(4, "The AI Agent will also proactively send you weekly review reports in the Agent Feed.", actionType = StepActionType.READ),
                ),
            ),
        ),
        "edit_product" to listOf(
            MicroLesson(
                lessonId = "edit_product_basics",
                featureId = "edit_product",
                languageCode = "en",
                title = "Editing a product",
                estimatedMinutes = 1,
                steps = listOf(
                    LessonStep(1, "Open Inventory, then tap the product you want to edit.", actionType = StepActionType.TAP),
                    LessonStep(2, "Tap the Edit (pencil) icon. Change name, price, cost, or stock as needed.", actionType = StepActionType.TAP),
                    LessonStep(3, "Tap Save. The changes take effect immediately at POS.", actionType = StepActionType.CONFIRM),
                ),
            ),
        ),
        "printer_setup" to listOf(
            MicroLesson(
                lessonId = "printer_basics",
                featureId = "printer_setup",
                languageCode = "en",
                title = "Setting up a receipt printer",
                estimatedMinutes = 3,
                steps = listOf(
                    LessonStep(1, "Turn on your Bluetooth thermal printer and make sure it is paired with your phone in Android Settings → Bluetooth.", actionType = StepActionType.READ),
                    LessonStep(2, "In BiasharaAI, go to Settings → Printer.", actionType = StepActionType.TAP),
                    LessonStep(3, "Tap 'Select Printer' and choose your printer from the list of paired devices.", actionType = StepActionType.TAP),
                    LessonStep(4, "Set the paper width (58mm or 80mm) to match your printer roll.", actionType = StepActionType.TYPE),
                    LessonStep(5, "Tap Test Print to verify the connection.", actionType = StepActionType.CONFIRM),
                ),
            ),
        ),
        "agent_settings" to listOf(
            MicroLesson(
                lessonId = "agent_settings_basics",
                featureId = "agent_settings",
                languageCode = "en",
                title = "Configuring the AI agent",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "Go to Settings → Agent Settings.", actionType = StepActionType.TAP),
                    LessonStep(2, "The Master Switch turns all agents on or off. Keep it on for proactive insights.", actionType = StepActionType.READ),
                    LessonStep(3, "Individual toggles let you disable specific agents (e.g. turn off Pricing Agent if prices are fixed).", actionType = StepActionType.TAP),
                    LessonStep(4, "Quiet Hours: set a start and end time when the agent will not send notifications (e.g. 22:00–07:00).", actionType = StepActionType.TYPE),
                    LessonStep(5, "Daily Summary Hour: the time each day when the agent runs its comprehensive review.", actionType = StepActionType.TYPE),
                ),
            ),
        ),
        "voice_settings" to listOf(
            MicroLesson(
                lessonId = "voice_settings_basics",
                featureId = "voice_settings",
                languageCode = "en",
                title = "Configuring voice and speech",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "Go to Settings → Voice & Speech.", actionType = StepActionType.TAP),
                    LessonStep(2, "Voice Input toggle: enable to allow microphone-based voice commands throughout the app.", actionType = StepActionType.TAP),
                    LessonStep(3, "Language Mode: 'Auto' detects Swahili or English automatically. Or force a specific language.", actionType = StepActionType.TAP),
                    LessonStep(4, "TTS (Text-to-Speech): when enabled, the AI reads answers aloud. Adjust speed and pitch.", actionType = StepActionType.TAP),
                    LessonStep(5, "Silence Timeout: how long to wait for you to finish speaking (default 2.5 seconds).", actionType = StepActionType.READ),
                ),
            ),
        ),
        "delete_product" to listOf(
            MicroLesson(
                lessonId = "delete_product_basics",
                featureId = "delete_product",
                languageCode = "en",
                title = "Deleting a product",
                estimatedMinutes = 1,
                steps = listOf(
                    LessonStep(1, "Open Inventory and tap the product you want to delete.", actionType = StepActionType.TAP),
                    LessonStep(2, "Tap the Delete (trash) icon. Confirm the deletion.", actionType = StepActionType.TAP),
                    LessonStep(3, "Historical sales records that included this product are preserved. Only the product entry is removed.", actionType = StepActionType.READ),
                ),
            ),
        ),
        "pos_barcode" to listOf(
            MicroLesson(
                lessonId = "pos_barcode_basics",
                featureId = "pos_barcode",
                languageCode = "en",
                title = "Scanning barcodes at POS",
                estimatedMinutes = 2,
                steps = listOf(
                    LessonStep(1, "In the POS screen, tap the barcode icon at the top.", actionType = StepActionType.TAP),
                    LessonStep(2, "Point the camera at the product's barcode.", actionType = StepActionType.TAP),
                    LessonStep(3, "The product is automatically added to the cart. Scan again to increase the quantity.", actionType = StepActionType.READ),
                    LessonStep(4, "If no product matches the barcode, you are prompted to create a new product with that barcode.", actionType = StepActionType.READ),
                ),
            ),
        ),
    )
}
