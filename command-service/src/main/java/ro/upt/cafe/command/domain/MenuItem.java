package ro.upt.cafe.command.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "menu_items")
public class MenuItem {
    @Id
    private String itemId;
    private String name;
    private String beverageGroup;
    private BigDecimal basePriceRon;
    private int activeDiscountPct = 0;

    public MenuItem() {}

    // Getters and Setters
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBeverageGroup() { return beverageGroup; }
    public void setBeverageGroup(String beverageGroup) { this.beverageGroup = beverageGroup; }
    public BigDecimal getBasePriceRon() { return basePriceRon; }
    public void setBasePriceRon(BigDecimal basePriceRon) { this.basePriceRon = basePriceRon; }
    public int getActiveDiscountPct() { return activeDiscountPct; }
    public void setActiveDiscountPct(int activeDiscountPct) { this.activeDiscountPct = activeDiscountPct; }
}