// [OUTLINE START]
// Package: dev.davimf.basebot.database.model
// 
// Record: BudgetItem
// 
// Record Components:
//   - Record Component : public final String id
//   - Record Component : public final String budgetId
//   - Record Component : public final String productName
//   - Record Component : public final long unitPriceCents
//   - Record Component : public final int quantity
// 
// Methods:
//   - `Method` : `public long subtotalCents()`
// [OUTLINE END]



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
