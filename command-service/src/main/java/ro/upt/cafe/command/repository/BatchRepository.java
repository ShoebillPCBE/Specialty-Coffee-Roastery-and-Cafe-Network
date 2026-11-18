package ro.upt.cafe.command.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.upt.cafe.command.domain.IngredientBatch;

public interface BatchRepository extends JpaRepository<IngredientBatch, String> {
}