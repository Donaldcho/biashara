# Business Ledger

## Overview

The Ledger is a chronological record of all money moving in and out of your business. Unlike the Sales screen (which shows only POS transactions), the Ledger captures everything: sales, expenses, supplier payments, cash deposits, and manual entries.

## Accessing the Ledger

Tap **Insights** in the bottom navigation, then select the **Ledger** tab. The screen shows entries in reverse-chronological order (newest first).

## Ledger Entry Types

| Type | Description |
|------|-------------|
| **Sale** | Revenue from a POS transaction |
| **Expense** | Money paid out (rent, utilities, supplies) |
| **Supplier payment** | Payment to a supplier for stock |
| **Cash deposit** | Cash added to the float |
| **Cash withdrawal** | Cash removed from the float |
| **Adjustment** | Manual correction to balance |

## Adding a Manual Entry

1. Tap the **+** button on the Ledger screen.
2. Choose the entry type (Expense, Deposit, etc.).
3. Enter the amount and a short description.
4. Tap **Save**.

Manual entries appear immediately in the ledger and are included in all calculations.

## Filtering the Ledger

Use the filter chip row at the top to narrow entries:
- **Today / This week / This month** — quick date presets
- **Custom** — pick any date range with the calendar picker
- **Type** — show only Sales, Expenses, etc.

## Running Balance

The running balance column shows the cumulative total after each entry. A negative running balance indicates your expenses have exceeded your income for the selected period.

## Understanding the Summary Bar

At the top of the Ledger screen, a summary bar shows:
- **In** — total money received (sales + deposits)
- **Out** — total money paid out (expenses + supplier payments + withdrawals)
- **Net** — In minus Out

## Ledger vs Cash Count

The Ledger shows recorded transactions. The **Cash Count** screen lets you physically count the cash in your till and reconcile it against the expected balance. Use Cash Count at end of day.

## AI-Powered Ledger Queries

You can ask the AI Chat assistant to analyse your ledger:
- "Show me all expenses from last week"
- "What's my net profit this month?"
- "Which day had the most expenses?"

The agent uses the `query_ledger` and `query_ledger_v2` skills to retrieve and summarise this data.

## Tips

- Record expenses as they happen — waiting until end of day leads to forgotten entries.
- Use the description field to categorise expenses (e.g. "Rent - March", "Electricity - April").
- The Trends tab aggregates ledger data into weekly charts so you can see patterns over time.
