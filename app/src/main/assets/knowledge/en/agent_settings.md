# Agent Settings
feature_id: agent_settings
language: en

Agent Settings let you control which AI agents are active, when they run, and how sensitive they are. Go to **Settings → Agent Settings** to access these options.

## Enabling and Disabling Agents

Each agent can be turned on or off independently:

- **Cash Flow Sentinel** — Monitors debts and overdue payments. Toggle on to receive alerts when customers are overdue.
- **Stock Guardian** — Alerts you when product stock falls below your set threshold. Toggle off if you do not track stock quantities.
- **Sales Analyst** — Analyses sales trends and compares periods. Requires at least 7 days of sales history.
- **Expense Watcher** — Flags expense spikes. Only useful if you record expenses regularly.

## Quiet Hours

Set a time window during which the agent will not generate or deliver new notifications. For example, set Quiet Hours to 10 PM – 7 AM to avoid disturbances at night.

1. Tap **Quiet Hours**.
2. Toggle **Enable Quiet Hours** on.
3. Set the Start and End time.
4. Tap **Save**.

Cards generated during quiet hours are held and delivered when quiet hours end.

## Alert Thresholds

Fine-tune how sensitive each agent is:

- **Low Stock Threshold**: Default is 5 units. Change to a number that makes sense for your business (e.g., 20 for fast-moving items).
- **Debt Overdue Days**: Number of days after the due date before the Cash Flow Sentinel flags a debt. Default is 3 days.
- **Sales Drop Alert**: Percentage drop in daily sales before the Sales Analyst raises a concern. Default is 30%.

## Notification Style

- **In-App Feed Only**: Cards appear only in the Agent Feed tab.
- **Push Notification**: Also sends a device notification. Tap it to jump directly to the card.
- **Silent**: Agents run but produce no alerts — useful for checking manually.

## Resetting to Defaults

Tap **More (⋮) → Reset Agent Settings** to restore all thresholds to their factory defaults.
