# Delete a Product
feature_id: delete_product
language: en

Deleting a product removes it from your active product list and the POS till. Historical sales records are preserved.

## Steps to Delete a Product

1. Open the **Products** screen.
2. Find the product you want to remove.
3. Long-press the product row and choose **Delete** from the context menu, or open the product and tap the **trash** icon in the top-right corner.
4. A confirmation dialog will appear. Read it carefully:
   - If the product has **no past sales**, it is permanently deleted.
   - If the product has **past sales**, you will be asked to either **Archive** or **Delete**.
5. Confirm your choice.

## Archive vs Delete

| Action | What happens |
|--------|-------------|
| **Archive** | Product is hidden from POS and stock views but all sales history is intact. You can restore it later from **Products → Archived**. |
| **Delete** | Product record is removed. Past sales entries still show the product name and price as text, but no product link remains. |

**Recommendation**: Use **Archive** when you may stock the product again in future. Use **Delete** only for products added by mistake.

## What Happens to Historical Sales?

- All past sales amounts, quantities, and dates are preserved.
- The product name on old receipts remains visible.
- Reports (revenue, profit) still include past sales of the deleted product.
- The deleted product no longer appears in low-stock alerts or reorder reminders.

## Restoring an Archived Product

1. Go to **Products → More (⋮) → Archived Products**.
2. Tap the product to open it.
3. Tap **Restore** to make it active again.
