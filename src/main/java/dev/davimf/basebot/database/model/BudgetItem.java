package dev.davimf.basebot.database.model;

/** A line item in a {@link Budget}: a product snapshot (name + unit price) and quantity. */
public record BudgetItem(
        String id,
        String budgetId,
        String productName,
        long unitPriceCents,
        int quantity
) {
    /** Line total in cents. */
    public long subtotalCents() {
        return unitPriceCents * quantity;
    }
}
