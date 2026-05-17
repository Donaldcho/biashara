# Customer Debts
feature_id: debts
language: en

The Debts feature lets you give goods on credit and track what each customer owes you. It prevents losses from forgotten credit and helps you collect payments on time.

## Recording a Debt (Credit Sale)

A debt is created automatically when you complete a POS sale and choose **Credit** as the payment method. Make sure a customer is selected before charging.

You can also add a debt manually:
1. Go to **Debts** from the main menu.
2. Tap **+ New Debt**.
3. Select the customer, enter the amount, add a description (e.g., "Flour 10kg"), and set a due date.
4. Tap **Save**.

## Viewing All Debts

The **Debts** screen shows:
- **Total Owed**: Sum of all outstanding balances.
- Customer list sorted by amount owed (largest first by default).
- Color coding: Green = within due date, Orange = due soon, Red = overdue.

## Recording a Debt Payment

1. Open the Debts screen and tap the customer who is paying.
2. Tap **Record Payment**.
3. Enter the amount paid and the payment method (cash, M-Pesa, etc.).
4. Tap **Save**. The balance updates immediately.

## Sending a Payment Reminder

1. Open a customer's debt detail.
2. Tap **Remind** to send an SMS or WhatsApp message with their current balance.
3. The reminder message uses your business name and shows the exact amount owed.

## Debt Reports

Go to **Reports → Debt Report** to see:
- Total credit extended this month
- Total collected
- Oldest unpaid debts
- Customers who have never paid

## Tips

- Set a **Credit Limit** on each customer to avoid over-lending.
- The AI agent will automatically alert you when a debt becomes overdue if the Cash Flow Sentinel is enabled.
