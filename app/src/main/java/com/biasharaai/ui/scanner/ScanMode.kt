package com.biasharaai.ui.scanner

/**
 * Defines the purpose of the barcode scan, determining result handling.
 *
 * Passed as a navigation argument (`scan_mode`) to [BarcodeScannerFragment].
 */
enum class ScanMode {
    /** Search existing product by barcode. Navigate to edit if found, offer to add if not. */
    SCAN_FOR_LOOKUP,

    /** Pre-fill the barcode field on a new product entry form. */
    SCAN_TO_ADD,

    /** Open POS / record sale with the scanned product. */
    SCAN_TO_RECORD_SALE,
}
