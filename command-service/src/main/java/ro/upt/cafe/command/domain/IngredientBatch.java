package ro.upt.cafe.command.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "ingredient_batches")
public class IngredientBatch {
    @Id
    private String batchId;
    private String cafeId;
    private String name;
    
    @Enumerated(EnumType.STRING)
    private IngredientCategory category;
    
    private double remainingQuantity;
    private LocalDate roastOrOpenDate;
    private LocalDate expiryDate;

    // Default constructor for JPA
    public IngredientBatch() {}

    public void deduct(double amount) {
        if (this.remainingQuantity < amount) {
            throw new IllegalStateException("Insufficient stock in batch " + batchId);
        }
        this.remainingQuantity -= amount;
    }

    // Getters and Setters
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getCafeId() { return cafeId; }
    public void setCafeId(String cafeId) { this.cafeId = cafeId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public IngredientCategory getCategory() { return category; }
    public void setCategory(IngredientCategory category) { this.category = category; }
    public double getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(double remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public LocalDate getRoastOrOpenDate() { return roastOrOpenDate; }
    public void setRoastOrOpenDate(LocalDate roastOrOpenDate) { this.roastOrOpenDate = roastOrOpenDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
}